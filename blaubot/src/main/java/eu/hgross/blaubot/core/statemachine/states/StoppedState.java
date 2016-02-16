package eu.hgross.blaubot.core.statemachine.states;

import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.ConnectionStateMachine;
import eu.hgross.blaubot.core.statemachine.StateMachineSession;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.events.AbstractTimeoutStateMachineEvent;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.util.Log;

/**
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class StoppedState implements IBlaubotState {

    private static final String LOG_TAG = "StoppedState";

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
        if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Stopping acceptors and beacons.");
		}
		ConnectionStateMachine connectionStateMachine = session.getConnectionStateMachine();
		BlaubotAdapterHelper.stopAcceptorsAndBeacons(connectionStateMachine.getConnectionAcceptors(), connectionStateMachine.getBeaconService());


		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Disconnecting all connections.");
		}
		// Kill all connections
		for (IBlaubotConnection conn : session.getConnectionManager().getAllConnections()) {
			conn.disconnect();
		}

		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Changing server connection manager operation mode.");
		}
		session.getServerConnectionManager().setMaster(false);
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
