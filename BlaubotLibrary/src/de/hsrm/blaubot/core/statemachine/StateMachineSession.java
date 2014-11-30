package de.hsrm.blaubot.core.statemachine;

import java.util.List;

import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.BlaubotConnectionManager;
import de.hsrm.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import de.hsrm.blaubot.core.statemachine.states.FreeState;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.core.statemachine.states.StoppedState;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;
import de.hsrm.blaubot.message.admin.AdminMessageFactory;
import de.hsrm.blaubot.message.admin.CensusMessage;
import de.hsrm.blaubot.message.admin.PronouncePrinceAdminMessage;
import de.hsrm.blaubot.protocol.IMessageListener;
import de.hsrm.blaubot.protocol.IProtocolManager;
import de.hsrm.blaubot.protocol.client.channel.IChannel;
import de.hsrm.blaubot.util.Log;

/**
 * Session object holding data which is shared across state changes.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class StateMachineSession {
	protected static final String LOG_TAG = "StateMachineSession";
	private final ConnectionStateMachine connectionStateMachine;
	private final IProtocolManager protocolManager;
	private final BlaubotConnectionManager connectionManager;
	private final BlaubotBeaconService beaconService;

	private CensusMessage lastCensusMessage;
	private PronouncePrinceAdminMessage lastPronouncePrinceAdminMessage;

	public StateMachineSession(ConnectionStateMachine stateMachine) {
		this.connectionStateMachine = stateMachine;
		this.connectionManager = stateMachine.blaubot.getConnectionManager();
		this.beaconService = stateMachine.getBeaconService();
		this.protocolManager = connectionStateMachine.blaubot.getProtocolManager();
		stateMachine.getAdminBroadcastChannel().subscribe(new IMessageListener() {

			@Override
			public void onMessage(BlaubotMessage message) {
				AbstractAdminMessage adminMessage = AdminMessageFactory.createAdminMessageFromRawMessage(message);
				if (adminMessage instanceof CensusMessage) {
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Got a CencusMessage containing " + ((CensusMessage) adminMessage).getDeviceStates().size() + " devices.");
					}
					lastCensusMessage = (CensusMessage) adminMessage;
				} else if (adminMessage instanceof PronouncePrinceAdminMessage) {
					lastPronouncePrinceAdminMessage = (PronouncePrinceAdminMessage) adminMessage;
				}
			}
		});
		
		this.connectionStateMachine.addConnectionStateMachineListener(new IBlaubotConnectionStateMachineListener() {

			@Override
			public void onStateMachineStopped() {

			}

			@Override
			public void onStateMachineStarted() {

			}

			@Override
			public void onStateChanged(IBlaubotState oldState, IBlaubotState state) {
				// we clear the cached messages if we go "offline" to free or stopped state
				if (state instanceof FreeState || state instanceof StoppedState) {
					clear();
				}
			}
		});
	}

	private void clear() {
		lastCensusMessage = null;
		lastPronouncePrinceAdminMessage = null;
	}

	public CensusMessage getLastCensusMessage() {
		return lastCensusMessage;
	}

	public PronouncePrinceAdminMessage getLastPronouncePrinceAdminMessage() {
		return lastPronouncePrinceAdminMessage;
	}

	public ConnectionStateMachine getConnectionStateMachine() {
		return connectionStateMachine;
	}

	public IProtocolManager getProtocolManager() {
		return protocolManager;
	}

	public BlaubotConnectionManager getConnectionManager() {
		return connectionManager;
	}

	public BlaubotBeaconService getBeaconService() {
		return beaconService;
	}

	public List<IBlaubotAdapter> getAdapters() {
		return connectionStateMachine.blaubot.getAdapters();
	}
	
	public IChannel getAdminBroadcastChannel() {
		return connectionStateMachine.getAdminBroadcastChannel();
	}

	/**
	 * @param uniqueId
	 *            the unique id to check
	 * @return true, if we own a device with this uniqueId
	 */
	public boolean isDeviceOneOfOurs(String uniqueId) {
		for (IBlaubotDevice d : getConnectionStateMachine().getOwnDevicesSet()) {
			if (d.getUniqueDeviceID().equals(uniqueId)) {
				return true;
			}
		}
		return false;
	}

}
