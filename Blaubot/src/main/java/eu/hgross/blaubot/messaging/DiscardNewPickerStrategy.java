package eu.hgross.blaubot.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Picks messages from the given queue.
 * On each pick, it picks the oldest message and discards all newer messages.
 */
public class DiscardNewPickerStrategy implements IBlaubotMessagePickerStrategy {
    @Override
    public BlaubotMessage pickNextMessage(BlockingQueue<BlaubotMessage> messageQueue) {
        List<BlaubotMessage> messages = new ArrayList<>();
        BlaubotMessage blaubotMessage;
        try {
            blaubotMessage = messageQueue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return null;
        }
        if (blaubotMessage == null) {
            return null;
        }

        messageQueue.drainTo(messages);
        BlaubotMessage queuedMessage = null;
        if (!messages.isEmpty()) {
            queuedMessage = messages.get(0);
        }
        return queuedMessage;
    }

    @Override
    public BlaubotChannelConfig.MessagePickerStrategy getConstant() {
        return BlaubotChannelConfig.MessagePickerStrategy.DISCARD_NEW;
    }
}
