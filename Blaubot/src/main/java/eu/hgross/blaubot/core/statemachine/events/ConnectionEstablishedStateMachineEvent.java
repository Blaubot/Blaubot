package eu.hgross.blaubot.core.statemachine.events;

import eu.hgross.blaubot.core.IBlaubotConnection;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class ConnectionEstablishedStateMachineEvent extends AbstractBlaubotStateMachineEvent {
	private IBlaubotConnection connection;

	public ConnectionEstablishedStateMachineEvent(IBlaubotConnection connection) {
		this.connection = connection;
	}

	public IBlaubotConnection getConnection() {
		return connection;
	}

	@Override
	public String toString() {
		return "ConnectionEstablishedStateMachineEvent [connection=" + connection + "]";
	}
	
	
}
