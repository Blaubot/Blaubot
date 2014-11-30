package de.hsrm.blaubot.protocol.eventdispatcher;

/**
 * base class for protocol events
 * 
 * @author manuelpras
 *
 */
public abstract class ProtocolEvent {

	/**
	 * type of the protocol event
	 * 
	 * @author manuelpras
	 *
	 */
	public enum EventType {
		ADD_CONNECTION, SET_MASTER, ACTIVATE, DEACTIVATE, CONNECTION_CLOSED
	}

	protected final EventType eventType;

	public ProtocolEvent(EventType eventType) {
		this.eventType = eventType;
	}

	public EventType getEventType() {
		return this.eventType;
	}

}
