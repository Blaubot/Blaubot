package de.hsrm.blaubot.protocol.client.channel.messagepicker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

import de.hsrm.blaubot.message.BlaubotMessage;

public class FixedDiscardOldPicker implements MessagePickerStrategy {

	@Override
	public BlaubotMessage pickMessage(LinkedBlockingDeque<BlaubotMessage> messageQueue, int timeout) throws InterruptedException {
		List<BlaubotMessage> oldmessages = new ArrayList<BlaubotMessage>();
		messageQueue.drainTo(oldmessages);
		BlaubotMessage queuedMessage = null;
		if (!oldmessages.isEmpty()) {
			queuedMessage = oldmessages.get(oldmessages.size() - 1);
		}
		return queuedMessage;
	}

}
