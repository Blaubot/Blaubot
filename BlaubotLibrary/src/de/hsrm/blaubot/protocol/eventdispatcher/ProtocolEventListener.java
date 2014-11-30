package de.hsrm.blaubot.protocol.eventdispatcher;

/**
 * can be attached to the {@link ProtocolEventDispatcher} in order to receive a
 * call when there is a new event
 * 
 * @author manuelpras
 *
 */
public interface ProtocolEventListener {

	/**
	 * callback for new protocol event
	 * 
	 * @param event
	 */
	public void onProtocolEvent(ProtocolEvent event);

}
