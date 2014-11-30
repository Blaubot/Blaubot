package de.hsrm.blaubot.protocol.eventdispatcher;

import de.hsrm.blaubot.core.IBlaubotConnection;

/**
 * represents the addConnection() event for the given {@link IBlaubotConnection}
 * 
 * @author manuelpras
 *
 */
public class ProtocolAddConnectionEvent extends ProtocolEvent {

	private IBlaubotConnection connection;

	public ProtocolAddConnectionEvent(IBlaubotConnection connection) {
		super(EventType.ADD_CONNECTION);
		this.connection = connection;
	}

	public IBlaubotConnection getConnection() {
		return this.connection;
	}

}
