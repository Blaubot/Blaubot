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
    private final BlaubotChannelManager channelManager;

    /**
     * Constructs a BlaubotMessageManager based on a pre-created receiver and sender object.
     * Note: this is used to emulate the reflexive connection when a BlaubotChannelManager is
     * acting as master.
     * @param messageSender
     * @param messageReceiver
     */
    public BlaubotMessageManager(BlaubotMessageSender messageSender, BlaubotMessageReceiver messageReceiver, BlaubotChannelManager channelManager) {
        this.messageReceiver = messageReceiver;
        this.messageSender = messageSender;
        this.channelManager = channelManager;
    }

    /**
     * Constructs the MessageManager based on a IBlaubotConnection. Creates the needed Receiver
     * and Sender objects by itself.
     * Note: mainly used for incoming connections
     * @param blaubotConnection
     * @param channelManager
     */
    public BlaubotMessageManager(IBlaubotConnection blaubotConnection, BlaubotChannelManager channelManager) {
        this.channelManager = channelManager;
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
                            // TODO: there is a bug regarding .disconnect() call on queue connections which causes the own connection's receiver not to be called here? Looks like setMaster(false); on the channel manager is called multiple times anyways -> check that
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
