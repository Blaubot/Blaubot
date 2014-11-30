package de.hsrm.blaubot.protocol.client.channel.messagepicker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

import de.hsrm.blaubot.message.BlaubotMessage;

public class FixedDiscardNewPicker implements MessagePickerStrategy {

	@Override
	public BlaubotMessage pickMessage(LinkedBlockingDeque<BlaubotMessage> messageQueue, int timeout) throws InterruptedException {
		List<BlaubotMessage> newmessages = new ArrayList<BlaubotMessage>();
		messageQueue.drainTo(newmessages);
		BlaubotMessage queuedMessage = null;
		if (!newmessages.isEmpty()) {
			queuedMessage = newmessages.get(0);
		}
		return queuedMessage;
	}

}
