package de.hsrm.blaubot.protocol.client.channel.messagepicker;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import de.hsrm.blaubot.message.BlaubotMessage;

public class FixedAggregatePicker implements MessagePickerStrategy {

	@Override
	public BlaubotMessage pickMessage(LinkedBlockingDeque<BlaubotMessage> messageQueue, int timeout) throws InterruptedException {
		//TODO What does aggregate mean?, for now do NO_LIMIT
		return messageQueue.pollFirst(timeout, TimeUnit.MILLISECONDS);
	}

}
