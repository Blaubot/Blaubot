package eu.hgross.blaubot.messaging;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Picks messages from the given queue one by one and does no fancy things.
 */
public class ProcessAllPickerStrategy implements IBlaubotMessagePickerStrategy {
    @Override
    public BlaubotMessage pickNextMessage(BlockingQueue<BlaubotMessage> messageQueue) {
        try {
            return messageQueue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    @Override
    public BlaubotChannelConfig.MessagePickerStrategy getConstant() {
        return BlaubotChannelConfig.MessagePickerStrategy.PROCESS_ALL;
    }
}
