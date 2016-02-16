package eu.hgross.blaubot.messaging;

import java.util.concurrent.BlockingQueue;

/**
 * A strategy to pick messages from a queue
 */
public interface IBlaubotMessagePickerStrategy {
    /**
     * The default timeout for polling operations on the blocking queue.
     */
    long POLL_TIMEOUT = 500;

    /**
     * Picks a message from the given queue.
     * The implementation has to ensure, that it returns regularly, even when there is no message available.
     * The preferred time for this is specified by the POLL_TIMEOUT constant.
     *
     * @param messageQueue the queue to pick the next message from
     * @return the picked message or null, if nothing is available to be sent.
     */
    BlaubotMessage pickNextMessage(BlockingQueue<BlaubotMessage> messageQueue);

    /**
     * @return the enum constant that fits tis implementation
     */
    BlaubotChannelConfig.MessagePickerStrategy getConstant();

}
