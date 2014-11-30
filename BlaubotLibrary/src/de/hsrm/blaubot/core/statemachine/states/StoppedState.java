package de.hsrm.blaubot.core.statemachine.states;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.statemachine.BlaubotAdapterHelper;
import de.hsrm.blaubot.core.statemachine.ConnectionStateMachine;
import de.hsrm.blaubot.core.statemachine.StateMachineSession;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.events.AbstractTimeoutStateMachineEvent;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;

/**
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class StoppedState implements IBlaubotState {
	
	@Override
	public IBlaubotState onConnectionEstablished(IBlaubotConnection connection) {
		return this;
	}

	@Override
	public IBlaubotState onConnectionClosed(IBlaubotConnection connection) {
		return this;
	}

	@Override
	public IBlaubotState onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
		return this;
	}

	@Override
	public void handleState(StateMachineSession session) {
		ConnectionStateMachine connectionStateMachine = session.getConnectionStateMachine();
		BlaubotAdapterHelper.stopAcceptorsAndBeacons(connectionStateMachine.getConnectionAcceptors(), connectionStateMachine.getBeaconService());
		for(IBlaubotConnection conn : session.getConnectionManager().getAllConnections())
			conn.disconnect();
	}
	

	@Override
	public IBlaubotState onAdminMessage(AbstractAdminMessage adminMessage) {
		return this;
	}

	@Override
	public String toString() {
		return "StoppedState";
	}

	@Override
	public IBlaubotState onTimeoutEvent(AbstractTimeoutStateMachineEvent timeoutEvent) {
		return this;
	}
}
