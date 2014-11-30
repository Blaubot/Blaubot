package de.hsrm.blaubot.protocol.eventdispatcher;

/**
 * represents the deactivate() event
 * 
 * @author manuelpras
 *
 */
public class ProtocolDeactivateEvent extends ProtocolEvent {

	public ProtocolDeactivateEvent() {
		super(EventType.DEACTIVATE);
	}

}
