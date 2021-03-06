package eu.hgross.blaubot.messaging;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IActionListener;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.util.Log;

/**
 * A message receiver handles incoming data streams from an IBlaubotConnection.
 * It converts the byte stream into BlaubotMessage instances and notifies it's
 * listeners when a message was completely read.
 * 
 * Message listeners can be activated and deactivated.
 */
public class BlaubotMessageReceiver {
    /* 
     * TODO the current deactivate/activate implementation will stack new blocking threads on each
     * deactivate/activate cycle if no message is received in between.
     * Shouldn't be a problem in almost every use case but is still not pretty.
     */ 
    
    private static final String LOG_TAG = "BlaubotMessageReceiver";
    /**
     * Locks the access to the chunked message mappings
     */
    private final Object chunkLock = new Object();
    private final Map<Short, Boolean> receivedLastChunkMapping;
    private final Map<Short, List<BlaubotMessage>> receivedChunks;

    private final IBlaubotConnection blaubotConnection;
    private final CopyOnWriteArrayList<IBlaubotMessageListener> messageListeners;
    private volatile MessageReceivingThread messageReceivingThread;
    private boolean forwardChunks = false;
    /**
     * Monitor to avoid execution of two MessageReceivingThreads at the same time on this instance.
     * (could happen on fast activate/deactivate calls)
     */
    private final Object receiverMonitor = new Object();
    private long receivedMessages = 0;
    private long receivedPayloadBytes = 0;
    private long receivedChunkMessages = 0;

    public BlaubotMessageReceiver(IBlaubotConnection blaubotConnection) {
        this.blaubotConnection = blaubotConnection;
        this.messageListeners = new CopyOnWriteArrayList<>();
        this.receivedLastChunkMapping = new HashMap<>();
        this.receivedChunks = new HashMap<>();
    }

    public void addMessageListener(IBlaubotMessageListener messageListener) {
        this.messageListeners.add(messageListener);
    }

    public void removeMessageListener(IBlaubotMessageListener messageListener) {
        this.messageListeners.remove(messageListener);
    }

    /**
     * The blaubot connection used to receive messages.
     *
     * @return the connection object used to receive messages
     */
    public IBlaubotConnection getBlaubotConnection() {
        return blaubotConnection;
    }

    /**
     * Activates the message receiver (reading from the connection).
     * If the receiver was already started, it will start a new consumer-thread that will sequentially
     * take over the work from the previous thread.
     */
    public void activate() {
        MessageReceivingThread mrt = new MessageReceivingThread();
        mrt.setName("msg-receiver-" + blaubotConnection.getRemoteDevice().getUniqueDeviceID() + ", " + mrt.getId());
        synchronized (activationLock) {
            // we don't interrupt a possibly already running receive thread here, see comment in BlaubotMessageManager 
            // deactivate() method.
            messageReceivingThread = mrt;
        }
        mrt.start();
    }

    /**
     * Deactivates the message receiver (completes current message readings, if any and then shuts down)
     *
     * @param actionListener callback to be informed when the receiver was closed (thread finished), can be null
     */
    public void deactivate(final IActionListener actionListener) {
        final MessageReceivingThread mrt;
        synchronized (activationLock) {
            mrt = messageReceivingThread;
            messageReceivingThread = null;

            // replacing the consumer thread is sufficient, we call the listener
            if (actionListener != null) actionListener.onFinished();
        }
        if (mrt != null) {
            //mrt.interrupt(); // we don't interrupt the thread, because we could end up in a out of sync bytestream this way
        }
        // clear chunk mappings
        synchronized (chunkLock) {
            receivedLastChunkMapping.clear();
        }
    }

    /**
     * Monitor for activation/deactivation calls.
     */
    private Object activationLock = new Object();


    /**
     * @return number of received messages so far (including chunks)
     */
    public long getReceivedMessages() {
        return receivedMessages;
    }

    /**
     * @return number of received payload bytes so far (including chunks)
     */
    public long getReceivedPayloadBytes() {
        return receivedPayloadBytes;
    }

    /**
     * If set to true, the receiver will not collect chunks and dispatch the whole (pieced together)
     * message to the listeners, but simply forward the chunks to the listeners.
     * Default: false
     *
     * @param forwardChunks iff true, chunk forwarding is active
     */
    public void setForwardChunks(boolean forwardChunks) {
        this.forwardChunks = forwardChunks;
    }

    /**
     * @return number of received chunk messages (chunks themselves)
     */
    public long getReceivedChunkMessages() {
        return receivedChunkMessages;
    }

    /**
     * Called by the receiving thread if a chunk message was received.
     *
     * @param chunkMessage the message
     */
    private void onChunkMessageReceived(BlaubotMessage chunkMessage) {
        /*
         * Chunked messages are processed as follows:
         *  - we store a mapping chunkId -> List of received messages regarding this chunk id
         *  - additionally we store a mapping chunkId -> Boolean which indicates, that we received the last chunk, which is determined by receiving a chunked message with the chunkId, that has less than BlaubotConstants.MAX_PAYLOAD_SIZE bytes as payload
         *  - if we receive a chunked message, we check if the last message was received.
         *      - if no: do nothing
         *      - if yes: check if we have all the chunks (all numbers from 0 to chunkNo of the last message)
         *          - if no: do nothin
         *          - if yes:
         *              - built the resulting message from the chunked messages and notify our listeners
         *              - clear the mapping
         */
        final short chunkId = chunkMessage.getChunkId();
        List<BlaubotMessage> completeListOfChunks = null;
        synchronized (chunkLock) {
            final boolean contained = receivedChunks.containsKey(chunkId);
            if (!contained) {
                receivedChunks.put(chunkId, new ArrayList<BlaubotMessage>());
            }
            final List<BlaubotMessage> messageList = receivedChunks.get(chunkId);
            messageList.add(chunkMessage);

            boolean isLastChunkMessage = chunkMessage.getPayload().length < BlaubotConstants.MAX_PAYLOAD_SIZE;
            if (isLastChunkMessage) {
                receivedLastChunkMapping.put(chunkId, true);
            }

            if (isLastChunkMessage || receivedLastChunkMapping.get(chunkId) != null) {
                int sum = 0;
                int maxChunkNo = -1;
                for (BlaubotMessage chunk : messageList) {
                    final int chunkNo = chunk.getChunkNo() & 0xffff; // unsigned shorts
                    sum += chunkNo;
                    if (chunkNo > maxChunkNo) {
                        maxChunkNo = chunkNo;
                    }
                }

                // check if we have all chunkNo n * (n+1)/2 == sum (Gauss)
                boolean weHaveAllChunks = (maxChunkNo * (maxChunkNo + 1) / 2) == sum;
                if (weHaveAllChunks) {
                    // fill the completeListOfChunks variable and forget about everything
                    completeListOfChunks = messageList;

                    receivedLastChunkMapping.remove(chunkId);
                    receivedChunks.remove(chunkId);
                }
            }
        }
        if (completeListOfChunks != null) {
            BlaubotMessage msg = BlaubotMessage.fromChunks(completeListOfChunks);
            notifyListeners(msg);
        }
    }

    /**
     * Consumes the connection's byte stream and deserializes BlaubotMessages from it.
     */
    class MessageReceivingThread extends Thread {
        /**
         * Milliseconds to wait if an io exception happens on read to not block the whole system in this cases and
         * let the listeners do their onConnectionClosed magic a little faster.
         */
        private static final long SLEEP_TIME_ON_IO_FAILURE = 350;
        private final String LOG_TAG = "MessageReceivingThread";

        @Override
        public void run() {
            synchronized (receiverMonitor) {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Started receiver for connection: " + blaubotConnection);
                }
                byte[] headerBuffer;
                int headerLength = BlaubotMessage.FULL_HEADER_LENGTH;
                headerBuffer = new byte[headerLength];
                ByteBuffer headerByteBuffer = ByteBuffer.wrap(headerBuffer).order(BlaubotConstants.BYTE_ORDER);

                // Keep listening to the InputStream until an exception occurs
                while (messageReceivingThread == this && !isInterrupted()) {
                    // Read from the InputStream
                    try {
                        BlaubotMessage message = BlaubotMessage.readFromBlaubotConnection(blaubotConnection, headerByteBuffer, headerBuffer);
                        
                        // maintain stats
                        receivedMessages += 1;
                        receivedPayloadBytes += message.getPayload().length;

                        // check if we need to process a chunked message
                        boolean isChunk = message.getMessageType().isChunk();
                        if (isChunk) {
                            receivedChunkMessages += 1;
                        }
                        if (!forwardChunks && isChunk) {
                            onChunkMessageReceived(message);
                        } else {
                            // notify all listeners
                            notifyListeners(message);
                        }


                    } catch (IOException e) {
                        // on connection failure the message receiver will transition to an inactive state
                        // failed connection are handled by the connection manager and this receiver will
                        // get a deactivate() call
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "IOException (" + e.getMessage() + ") while reading from connection: " + blaubotConnection);
                        }
                        try {
                            Thread.sleep(SLEEP_TIME_ON_IO_FAILURE);
                        } catch (InterruptedException e1) {
                            break; // got interrupted, we exit
                        }
                    }
                }
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Stopped receiver for connection: " + blaubotConnection);
                }
            }
        }

    }

    /**
     * Notifies all attached message listeners about a newly received message
     *
     * @param message the message to be dispatched to the listening parties.
     */
    private void notifyListeners(BlaubotMessage message) {
        // notify listeners about new message
        for (IBlaubotMessageListener listener : messageListeners) {
            listener.onMessage(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlaubotMessageReceiver receiver = (BlaubotMessageReceiver) o;

        if (blaubotConnection != null ? !blaubotConnection.equals(receiver.blaubotConnection) : receiver.blaubotConnection != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return blaubotConnection != null ? blaubotConnection.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "BlaubotMessageReceiver{" +
                "blaubotConnection=" + blaubotConnection +
                '}';
    }
}
