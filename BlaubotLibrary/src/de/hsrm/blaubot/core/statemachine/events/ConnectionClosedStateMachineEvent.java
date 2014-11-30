package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.IBlaubotConnection;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class ConnectionClosedStateMachineEvent extends AbstractBlaubotStateMachineEvent {
	private IBlaubotConnection connection;

	public ConnectionClosedStateMachineEvent(IBlaubotConnection connection) {
		this.connection = connection;
	}

	public IBlaubotConnection getConnection() {
		return connection;
	}

	@Override
	public String toString() {
		return "ConnectionClosedStateMachineEvent [connection=" + connection + "]";
	}
	
	
}
