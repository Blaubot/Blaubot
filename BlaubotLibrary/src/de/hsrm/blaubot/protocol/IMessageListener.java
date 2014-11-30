package de.hsrm.blaubot.protocol;

import de.hsrm.blaubot.message.BlaubotMessage;

/**
 * callback interface for notifications about new messages. implement this
 * interface if the implementation shall be notified as soon as new
 * {@link BlaubotMessage}s are available.
 * 
 * @author manuelpras
 *
 */
public interface IMessageListener {

	/**
	 * @param message
	 *            received {@link BlaubotMessage}
	 */
	public void onMessage(BlaubotMessage message);

}
