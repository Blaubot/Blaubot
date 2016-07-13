package eu.hgross.blaubot.messaging;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.core.IActionListener;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.util.Log;

/**
 * The message manager consists of a message receiving as well as a message sending part for
 * the IBlaubotConnection it is created for to handle.
 *
 */
public class BlaubotMessageManager {
    private static final String LOG_TAG = "BlaubotMessageManager";
    /**
     * The maximum period in ms after which a message sender or receiver has to be shut down after instructed to do so.
     */
    private static final long MAX_SENDER_AND_RECEIVER_SHUTDOWN_TIME = 5000;
    private final BlaubotMessageReceiver messageReceiver;
    private final BlaubotMessageSender messageSender;

    /**
     * Constructs a BlaubotMessageManager based on a pre-created receiver and sender object.
     * Note: this is used to emulate the reflexive connection when a BlaubotChannelManager is
     * acting as master.
     * 
     * @param messageSender the message sender
     * @param messageReceiver the message receiver
     */
    public BlaubotMessageManager(BlaubotMessageSender messageSender, BlaubotMessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
        this.messageSender = messageSender;
    }

    /**
     * Constructs the MessageManager based on a IBlaubotConnection. Creates the needed Receiver
     * and Sender objects by itself.
     * Note: mainly used for incoming connections
     * 
     * @param blaubotConnection the connection to be managed 
     */
    public BlaubotMessageManager(IBlaubotConnection blaubotConnection) {
        this.messageReceiver = new BlaubotMessageReceiver(blaubotConnection);
        this.messageSender = new BlaubotMessageSender(blaubotConnection);
    }

    /**
     * Activates the sender and receiver
     */
    public void activate() {
        messageReceiver.activate();
        messageSender.activate();
    }

    /**
     * Deactivates sender and receiver
     * @param actionListener a listener that is called when the receiver and sender are deactivated. may be null
     */
    public void deactivate(final IActionListener actionListener) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Deactivating ...");
        }
        final CountDownLatch receiverLatch = new CountDownLatch(1);
        final CountDownLatch senderLatch = new CountDownLatch(1);
        IActionListener receiverListener = new IActionListener() {
            @Override
            public void onFinished() {
                receiverLatch.countDown();
            }
        };
        IActionListener senderListener = new IActionListener() {
            @Override
            public void onFinished() {
                senderLatch.countDown();
            }
        };

        messageReceiver.deactivate(receiverListener);
        messageSender.deactivate(senderListener);
        if (actionListener != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // stop sender
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Awaiting sender to stop: " + messageSender);
                    }
                    try {
                        boolean timedOut = !senderLatch.await(MAX_SENDER_AND_RECEIVER_SHUTDOWN_TIME, TimeUnit.MILLISECONDS);
                        if (timedOut) {
                            throw new RuntimeException("A MessageSender did not shut down fast enough (waited " + MAX_SENDER_AND_RECEIVER_SHUTDOWN_TIME + " ms); Sender: " + messageSender);
                        }
                    } catch (InterruptedException e) {
                        // wayne
                    }

                    // stop receiver
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Awaiting receiver to stop: " + messageReceiver);
                    }
                    try {
                        boolean timedOut = !receiverLatch.await(MAX_SENDER_AND_RECEIVER_SHUTDOWN_TIME, TimeUnit.MILLISECONDS);
                        if (timedOut) {
                            if (Log.logErrorMessages()) {
                                Log.e(LOG_TAG, "A MessageReceiver did not shut down fast enough (waited " + MAX_SENDER_AND_RECEIVER_SHUTDOWN_TIME + " ms); Receiver: "+ messageReceiver);
                            }
                            /*
                                When the receiver's activate() method is called multiple times in a row, for each call
                                a new read-thread will be started reading from the connection. The old read-thread will 
                                recognize that after he completed the last message read and will terminate itself after that.
                                
                                The receiver reads from the connection using DataInputStream's readFully() method, which
                                is blocking until something is received through the connection.
                                Therefore calling deactivate() without closing the connection or interrupting the read thread
                                will result in this time out.
                                
                                Here is why we don't want any of the two possible thread terminations:
                                Closing the connection
                                    - We would disconnect healthy connections but only intend to deactivate reception of messages.
                                Interrupting the read thread
                                    - We could interrupt a message read and leaving an out of sync byte-stream behind (very bad).
                             
                                So this timeout does not harm us and is legit, but a cleaner termination strategy would be nice.
                             */
                            //throw new RuntimeException("A MessageReceiver did not shut down fast enough (waited " + MAX_SENDER_AND_RECEIVER_SHUTDOWN_TIME + " ms); Receiver: "+ messageReceiver);
                        }
                    } catch (InterruptedException e) {
                        // wayne
                    }

                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Receiver and Sender are now stopped.");
                    }
                    actionListener.onFinished();
                }
            }).start();
        }
    }

    public BlaubotMessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    public BlaubotMessageSender getMessageSender() {
        return messageSender;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlaubotMessageManager that = (BlaubotMessageManager) o;

        if (messageReceiver != null ? !messageReceiver.equals(that.messageReceiver) : that.messageReceiver != null)
            return false;
        if (messageSender != null ? !messageSender.equals(that.messageSender) : that.messageSender != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = messageReceiver != null ? messageReceiver.hashCode() : 0;
        result = 31 * result + (messageSender != null ? messageSender.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BlaubotMessageManager{" +
                "messageSender=" + messageSender +
                ", messageReceiver=" + messageReceiver +
                '}';
    }
}
