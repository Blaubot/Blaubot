package de.hsrm.blaubot.protocol.eventdispatcher;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.protocol.ProtocolManager;

/**
 * Called when a connection was closed.
 * Note that the listener for connection close is assigend in {@link ProtocolManager#addConnection(IBlaubotConnection)}
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class ProtocolConnectionClosedEvent extends ProtocolEvent {

	private IBlaubotConnection connection;

	public ProtocolConnectionClosedEvent(IBlaubotConnection connection) {
		super(EventType.CONNECTION_CLOSED);
		this.connection = connection;
	}

	public IBlaubotConnection getConnection() {
		return connection;
	}
}
