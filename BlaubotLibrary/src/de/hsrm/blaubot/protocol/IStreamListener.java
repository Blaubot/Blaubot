package de.hsrm.blaubot.protocol;

import de.hsrm.blaubot.message.BlaubotMessage;

/**
 * listens to {@link BlaubotMessage} stream events like new messages or wrong
 * (obsolete) protocol versions of incoming messages
 * 
 * @author manuelpras
 *
 */
public interface IStreamListener extends IMessageListener {

	/**
	 * gets called if the expected version differs from the actual version. this
	 * might be the case if this instance expects a newer version than the
	 * actual one.
	 * 
	 * @param expectedVersion
	 * @param actualVersion
	 */
	public void onWrongProtocolVersion(short expectedVersion, short actualVersion);

}
