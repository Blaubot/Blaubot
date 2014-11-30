package de.hsrm.blaubot.protocol.client.channel.messagepicker;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import de.hsrm.blaubot.message.BlaubotMessage;

public class NoLimitPicker implements MessagePickerStrategy {

	@Override
	public BlaubotMessage pickMessage(LinkedBlockingDeque<BlaubotMessage> messageQueue, int timeout) throws InterruptedException {
		return messageQueue.pollFirst(timeout, TimeUnit.MILLISECONDS);
	}

}
