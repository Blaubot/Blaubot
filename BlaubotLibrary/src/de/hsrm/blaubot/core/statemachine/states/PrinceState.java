package de.hsrm.blaubot.core.statemachine.states;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.statemachine.BlaubotAdapterHelper;
import de.hsrm.blaubot.core.statemachine.StateMachineSession;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.events.AbstractTimeoutStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.events.DiscoveredKingEvent;
import de.hsrm.blaubot.core.statemachine.states.PeasantState.ConnectionAccomplishmentType;
import de.hsrm.blaubot.message.admin.ACKPronouncePrinceAdminMessage;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;
import de.hsrm.blaubot.message.admin.BowDownToNewKingAdminMessage;
import de.hsrm.blaubot.message.admin.PrinceFoundAKingAdminMessage;
import de.hsrm.blaubot.message.admin.PronouncePrinceAdminMessage;
import de.hsrm.blaubot.protocol.client.channel.Channel;
import de.hsrm.blaubot.util.Log;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class PrinceState implements IBlaubotState, IBlaubotSubordinatedState {
	private static final int MAX_RETRIES_ON_BOW_DOWN_CONNECTION = 4;
	private static final String LOG_TAG = "PrinceState";
	private StateMachineSession session;
	private IBlaubotConnection kingConnection;

	/**
	 * @param kingConnection
	 *            the connection to our king
	 */
	public PrinceState(IBlaubotConnection kingConnection) {
		this.kingConnection = kingConnection;
		if (kingConnection == null)
			throw new NullPointerException();
	}

	@Override
	public IBlaubotState onConnectionEstablished(IBlaubotConnection connection) {
		return this;
	}

	@Override
	public IBlaubotState onConnectionClosed(IBlaubotConnection connection) {
		if (connection == kingConnection) {
			// -- we are prince and lost our king connection
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "We lost a connection in PrinceState. We assume our king to be lost. We are the prince so we change into the KingState");
			}
			return new KingState();
		}
		return this;
	}

	@Override
	public IBlaubotState onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
		if (discoveryEvent instanceof DiscoveredKingEvent) {
			IBlaubotDevice remoteDevice = discoveryEvent.getRemoteDevice();
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "I discovered another king and will inform our king");
			}
			int connectedDevices = session.getConnectionManager().getAllConnections().size();
			// assert there is only the king connection
			if (connectedDevices != 1) {
				throw new IllegalStateException("Incosistant state! There are " + connectedDevices + " connected devices (should be exactly 1)");
			}
			final IBlaubotDevice kingDevice = session.getConnectionManager().getAllConnections().get(0).getRemoteDevice();
			final PrinceFoundAKingAdminMessage princeFoundAKingAdminMessage = new PrinceFoundAKingAdminMessage(remoteDevice.getUniqueDeviceID());
			// send messsage to (and only to) king device
			Channel channel = session.getProtocolManager().getChannelFactory().getAdminDeviceChannel(kingDevice);
			channel.post(princeFoundAKingAdminMessage.toBlaubotMessage());
		}
		return this;
	}

	@Override
	public void handleState(StateMachineSession session) {
		this.session = session;
		BlaubotAdapterHelper.stopAcceptors(session.getConnectionStateMachine().getConnectionAcceptors());
		BlaubotAdapterHelper.setDiscoveryActivated(session.getBeaconService(), true);
	}

	@Override
	public IBlaubotState onAdminMessage(AbstractAdminMessage adminMessage) {
		if (adminMessage instanceof PronouncePrinceAdminMessage) {
			PronouncePrinceAdminMessage ppam = (PronouncePrinceAdminMessage) adminMessage;
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "We got a PronouncePrinceAdminMessage. The new prince is " + ppam.getUniqueDeviceId());
			}
			String newPrinceUniqueId = ppam.getUniqueDeviceId();
			// check if we are NOT pronounced as prince and need to back off
			boolean weArePrince = false;
			for (IBlaubotDevice d : session.getConnectionStateMachine().getOwnDevicesSet()) {
				if (d.getUniqueDeviceID().equals(newPrinceUniqueId)) {
					// we are the new prince! -> change state
					weArePrince = true;
					break;
				}
			}
			if (weArePrince) {
				if (Log.logDebugMessages()) {
					Log.d(LOG_TAG, "We are still prince, sending ACK and remaining in prince state.");
				}
				String ourUniqueId = newPrinceUniqueId;
				ACKPronouncePrinceAdminMessage ackMsg = new ACKPronouncePrinceAdminMessage(ourUniqueId);
				session.getAdminBroadcastChannel().post(ackMsg.toBlaubotMessage());
			} else {
				if (Log.logDebugMessages()) {
					Log.d(LOG_TAG, "We are not prince anymore - changing to peasant state.");
				}
				return new PeasantState(kingConnection, ConnectionAccomplishmentType.DEGRADATION);
			}
		} else if (adminMessage instanceof BowDownToNewKingAdminMessage) {
			BowDownToNewKingAdminMessage bowDownToNewKingAdminMessage = (BowDownToNewKingAdminMessage) adminMessage;
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "We got a BowDownToNewKingAdminMessage. The new king is " + bowDownToNewKingAdminMessage.getNewKingsUniqueDeviceId());
				Log.d(LOG_TAG, "Trying to connect to new king ...");
			}

			IBlaubotConnection conn = session.getConnectionManager().connectToBlaubotDevice(bowDownToNewKingAdminMessage.getNewKingsUniqueDeviceId(), MAX_RETRIES_ON_BOW_DOWN_CONNECTION);
			boolean result = conn != null;
			if (result) {
				if (Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Connection to new king successful. Changing to peasant state.");
				}

				return new PeasantState(kingConnection, ConnectionAccomplishmentType.BOWED_DOWN);
			} else {
				if (Log.logDebugMessages()) {
					Log.w(LOG_TAG, "Connection to new king failed! Oh my, now we are an outlaw :-(. Changing to FreeState to find a new king.");
				}
				return new FreeState();
			}
		}
		return this;
	}

	@Override
	public String toString() {
		return "PrinceState";
	}

	@Override
	public String getKingUniqueId() {
		return kingConnection.getRemoteDevice().getUniqueDeviceID();
	}

	@Override
	public IBlaubotState onTimeoutEvent(AbstractTimeoutStateMachineEvent timeoutEvent) {
		return this;
	}

	@Override
	public IBlaubotConnection getKingConnection() {
		return kingConnection;
	}

}
