package eu.hgross.blaubot.messaging;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IActionListener;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.util.Log;

/**
 * The message sender simply queues messages that are going to be sent over the IBlaubotConnection
 * for which this message sender was created for.
 * <p/>
 * The sender can be activated/deactivated, meaning stopping and starting a queue consuming thread
 * that serializes and sends the queued messages (if any) over the given IBlaubotConnection.
 * <p/>
 * TODO: handle failing connections
 */
public class BlaubotMessageSender {
    private static final String LOG_TAG = "BlaubotMessageSender";
    /**
     * Generator for chunk ids
     */
    private final AtomicShort chunkIdGenerator;

    /**
     * If a sender is asked to send an already chunked message, the chunkId of the message is mapped
     * to one of its own ids to avoid clashes on the receiver side.
     */
    private final ConcurrentHashMap<Short, Short> chunkIdMapping;

    /**
     * The message queue, prioritized by the priorityComparator
     */
    private final PriorityBlockingQueue<BlaubotMessage> queuedMessages;

    /**
     * The connection over which the messages are send
     */
    private final IBlaubotConnection blaubotConnection;

    /**
     * Thread which empties the queue and send the messages in it if possible.
     */
    private volatile MessageSendingThread messageSendingThread;

    /**
     * Generates sequence numbers for messages added to the queue to ensure that messages which are
     * sent with the same priority arrive in the sending order.
     */
    private AtomicInteger sequenceNumberGenerator;
    private long sentMessages = 0;
    private long sentPayloadBytes = 0;
    private volatile AtomicLong queuedBytes = new AtomicLong(0);

    /**
     * Comparator for the priority queue.
     * Comparing by the priority first and then the sequence number, if same priority
     */
    private static final Comparator<BlaubotMessage> priorityComparator = new Comparator<BlaubotMessage>() {
        @Override
        public int compare(BlaubotMessage o1, BlaubotMessage o2) {
            final byte o1Val = o1.getPriority().value;
            final byte o2Val = o2.getPriority().value;
            if (o1Val < o2Val) {
                return -1;
            } else if (o1Val > o2Val) {
                return 1;
            } else {
                // equal priority, the smaller sequence number wins
                if (o1.sequenceNumber < o2.sequenceNumber) {
                    return -1;
                } else if (o1.sequenceNumber > o2.sequenceNumber) {
                    return 1;
                } else {
                    return 0;
                }
            }
        }
    };

    /**
     * Monitor to avoid two MessageSendingThreads to execute at the same time on this instance.
     * (could happen on fast activate/deactivate calls)
     */
    private final Object senderMonitor = new Object();

    public BlaubotMessageSender(IBlaubotConnection blaubotConnection) {
        this.sequenceNumberGenerator = new AtomicInteger(0);
        this.chunkIdGenerator = new AtomicShort((short) 0);
        this.blaubotConnection = blaubotConnection;
        this.queuedMessages = new PriorityBlockingQueue<>(50, priorityComparator);
        this.chunkIdMapping = new ConcurrentHashMap<>();
    }

    /**
     * Queues the given message to be sent over the IBlaubotConnection this object was
     * created with.
     *
     * @param message the message to be send
     */
    public void sendMessage(BlaubotMessage message) {
        // check if we need to chunk this message
        final boolean needsToBeChunked = message.getMessageType().containsPayload() && message.getPayload().length > BlaubotConstants.MAX_PAYLOAD_SIZE;
        if (needsToBeChunked) {
            if (message.getMessageType().isChunk()) {
                throw new IllegalStateException("Already chunked messages should never be chunked again!");
            }
            final short chunkId = chunkIdGenerator.getAndIncrement();
            List<BlaubotMessage> chunkMessages = message.createChunks(chunkId);
            for (BlaubotMessage chunkMessage : chunkMessages) {
                sendMessage(chunkMessage);
            }
            return;
        }

        if (message.getMessageType().isChunk()) {
            // if the mesage is already chunked (probably received by a relay connection's mediator and resend)
            // we want to map the chunk id to a safe number and set it on the message
            final short newChunkId = chunkIdGenerator.getAndIncrement();
            Short ourChunkId = chunkIdMapping.putIfAbsent(message.getChunkId(), newChunkId);
            if (ourChunkId == null) {
                ourChunkId = newChunkId;
            }
            message.setChunkId(ourChunkId);
        }

        // apply a sequence number and add to queue
        message.sequenceNumber = sequenceNumberGenerator.incrementAndGet();
        queuedMessages.add(message);
        queuedBytes.addAndGet(message.getPayload().length);
    }

    /**
     * Activates the message receiver (reading from the connection)
     */
    public void activate() {
        MessageSendingThread mrt = new MessageSendingThread();
        mrt.setName("msg-sender-" + blaubotConnection.getRemoteDevice().getUniqueDeviceID());
        messageSendingThread = mrt;
        mrt.start();
    }


    /**
     * Deactivates the message sender (completes current message readings, if any and then shuts down).
     *
     * @param actionListener callback to be informed when the sender was closed (thread finished), can be null
     */
    public void deactivate(IActionListener actionListener) {
        MessageSendingThread mst = messageSendingThread;
        messageSendingThread = null;
        if (mst != null) {
            mst.attachFinishListener(actionListener);
            mst.interrupt();
        } else {
            if (actionListener != null) {
                actionListener.onFinished();
            }
        }
    }

    /**
     * @return sent bytes so far
     */
    public long getSentPayloadBytes() {
        return sentPayloadBytes;
    }

    /**
     * sent messages
     *
     * @return sent messages so far
     */
    public long getSentMessages() {
        return sentMessages;
    }

    class MessageSendingThread extends Thread {
        private static final long POLL_TIMEOUT = 1000;
        private static final long WAIT_TIME_ON_FAILED_SEND = 500;
        private static final String LOG_TAG = "MessageSendingThread";

        private IActionListener finishedListener;
        private boolean finished = false;
        private Object finishedMonitor = new Object();

        /**
         * Attaches a listener that gets called, if the thread finished.
         * Meaning the run() method finished once.
         * If attached after it already finished, the listener is called
         * immediately.
         *
         * @param listener the listener
         */
        public void attachFinishListener(IActionListener listener) {
            synchronized (finishedMonitor) {
                this.finishedListener = listener;
                if (finished) {
                    finishedListener.onFinished();
                }
            }
        }

        @Override
        public void run() {
            synchronized (senderMonitor) {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Started sender for connection " + blaubotConnection);
                }
                while (messageSendingThread == this && !isInterrupted()) {
                    BlaubotMessage messageToSend;
                    try {
                        messageToSend = queuedMessages.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException interruptedException) {
                        break;
                    }

                    if (messageToSend == null) {
                        continue;
                    }

                    try {
                        if (Log.logDebugMessages()) {
                            //Log.d(LOG_TAG, "Sending message: " + messageToSend);
                        }
                        final byte[] bytes = messageToSend.toBytes();
                        blaubotConnection.write(bytes);

                        // maintain stats
                        sentMessages += 1;
                        sentPayloadBytes += bytes.length;
                        queuedBytes.addAndGet(-messageToSend.getPayload().length);
                    } catch (IOException e) {
                        // back to queue on fail
                        queuedMessages.add(messageToSend);
                        try {
                            // wait an amount of time to mitigate busy waits on failed connections
                            Thread.sleep(WAIT_TIME_ON_FAILED_SEND);
                        } catch (InterruptedException interruptedException) {
                            break;
                        }
                    }
                    // send message
                }
                synchronized (finishedMonitor) {
                    finished = true;
                    if (finishedListener != null) {
                        finishedListener.onFinished();
                    }
                }
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Stopped sender for connection " + blaubotConnection);
                }
            }
        }
    }

    /**
     * The connection that is managed by this message sender.
     *
     * @return the managed connection
     */
    protected IBlaubotConnection getBlaubotConnection() {
        return blaubotConnection;
    }

    /**
     * The current amount of messages in the queue
     *
     * @return current amount of messages in the queue
     */
    protected int getQueueSize() {
        return queuedMessages.size();
    }

    /**
     * The current number of bytes in this MessageSender's queue.
     *
     * @return number of bytes
     */
    protected long getQueuedBytes() {
        return queuedBytes.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlaubotMessageSender that = (BlaubotMessageSender) o;

        if (blaubotConnection != null ? !blaubotConnection.equals(that.blaubotConnection) : that.blaubotConnection != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return blaubotConnection != null ? blaubotConnection.hashCode() : 0;
    }

    private static class AtomicShort {
        short val;

        AtomicShort(short val) {
            this.val = val;
        }

        synchronized short getAndIncrement() {
            short prev = val;
            val += 1;
            return prev;
        }
    }

    @Override
    public String toString() {
        return "BlaubotMessageSender{" +
                "blaubotConnection=" + blaubotConnection +
                '}';
    }
}
