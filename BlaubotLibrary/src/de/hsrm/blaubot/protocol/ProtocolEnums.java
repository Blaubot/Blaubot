package de.hsrm.blaubot.protocol;

import de.hsrm.blaubot.message.BlaubotMessage;

/**
 * common enums used by the protocol layer
 * 
 * @author manuelpras
 *
 */
public class ProtocolEnums {

	/**
	 * constants for rate type of {@link BlaubotMessage} dispatch algorithms
	 * (e.g. will messages be sent with a fixed rate like 10 messages per second
	 * or is there no limit and the sender tries to send new messages as soon as
	 * they appear?)
	 * 
	 * @author manuelpras
	 *
	 */
	public enum MessageRateType {
		FIXED_DISCARD_OLD, FIXED_DISCARD_NEW, FIXED_AGGREGATE, NO_LIMIT
	}

}
