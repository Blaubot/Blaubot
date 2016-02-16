package eu.hgross.blaubot.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Picks messages from the given queue.
 * On each pick, it picks the newest message and discards all older messages.
 */
public class DiscardOldPickerStrategy implements IBlaubotMessagePickerStrategy {
    @Override
    public BlaubotMessage pickNextMessage(BlockingQueue<BlaubotMessage> messageQueue) {
        List<BlaubotMessage> oldmessages = new ArrayList<>();
        BlaubotMessage blaubotMessage;
        try {
            blaubotMessage = messageQueue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return null;
        }
        if (blaubotMessage == null) {
            return null;
        }

        oldmessages.add(blaubotMessage);
        messageQueue.drainTo(oldmessages);

        BlaubotMessage queuedMessage = null;
        if (!oldmessages.isEmpty()) {
            queuedMessage = oldmessages.get(oldmessages.size() - 1);
        }
        return queuedMessage;
    }

    @Override
    public BlaubotChannelConfig.MessagePickerStrategy getConstant() {
        return BlaubotChannelConfig.MessagePickerStrategy.DISCARD_OLD;
    }
}
