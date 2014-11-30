package de.hsrm.blaubot.protocol.client.channel.messagepicker;

import java.util.concurrent.LinkedBlockingDeque;

import de.hsrm.blaubot.message.BlaubotMessage;

/**
 * interface for different message picking strategies. depending on the
 * strategy, {@link BlaubotMessage}s are picked in different ways from the given queue
 * 
 * @author manuelpras
 * 
 */
public interface MessagePickerStrategy {

	/**
	 * returns a message from the given queue depending on the implemented strategy
	 * @param messageQueue
	 * @param timeout
	 * @return
	 * @throws InterruptedException
	 */
	public BlaubotMessage pickMessage(LinkedBlockingDeque<BlaubotMessage> messageQueue, int timeout) throws InterruptedException;

}
