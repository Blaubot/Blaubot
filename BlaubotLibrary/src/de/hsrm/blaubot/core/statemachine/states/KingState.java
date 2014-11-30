package de.hsrm.blaubot.core.statemachine.states;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.hsrm.blaubot.core.ConnectionStateMachineConfig;
import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.statemachine.BlaubotAdapterHelper;
import de.hsrm.blaubot.core.statemachine.StateMachineSession;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.events.AbstractTimeoutStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.events.DiscoveredKingEvent;
import de.hsrm.blaubot.core.statemachine.events.KingTimeoutEvent;
import de.hsrm.blaubot.core.statemachine.events.PronouncedPrinceACKTimeoutStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.states.PeasantState.ConnectionAccomplishmentType;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.admin.ACKPronouncePrinceAdminMessage;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;
import de.hsrm.blaubot.message.admin.BowDownToNewKingAdminMessage;
import de.hsrm.blaubot.message.admin.CensusMessage;
import de.hsrm.blaubot.message.admin.PrinceFoundAKingAdminMessage;
import de.hsrm.blaubot.message.admin.PronouncePrinceAdminMessage;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.client.channel.Channel;
import de.hsrm.blaubot.util.Log;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class KingState implements IBlaubotState {
	private static final String LOG_TAG = "KingState";
	private Timer noConnectionsTimer;
	private boolean connectingToAnotherKing = false; // TODO: i think this is
														// now usesless ->
														// validate
	private Object timerTaskMonitor = new Object();
	private StateMachineSession session;
	private String currentPrinceUniqueId = null;
	private PrinceWatcher princeWatcher;

	/**
	 * This listener will be called whenever we get a {@link IBlaubotConnection}
	 * in THIS {@link KingState}.
	 */
	private IBlaubotIncomingConnectionListener peasantConnectionsListener;

	/**
	 * Manages the pronounce prince and ACKPrince cycle. If a connection gets
	 * lost or is established, we pronounce a prince and
	 * {@link PronouncePrinceAdminMessage} as well as a {@link CensusMessage} is
	 * sent to all clients. The prince will then answer with a
	 * {@link ACKPronouncePrinceAdminMessage}. If there is no answer from the
	 * prince for a given time span, we pronounce the prince again.
	 * 
	 * 
	 * This is a workaround because the bloody {@link ProtocolManager}'s
	 * implementation and it's device specific {@link Channel}s are not working
	 * properly, so we use this little ACK-Protocol to pronounce the prince.
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	class PrinceWatcher {
		private static final String LOG_TAG = "PrinceWatcher";
		private PronouncePrinceAdminMessage lastPronouncedPrinceMessage;
		private Timer currentTimer;

		/**
		 * needs to be called if an {@link ACKPronouncePrinceAdminMessage}
		 * message arrives.
		 * 
		 * @param ackMessage
		 */
		synchronized void onAck(ACKPronouncePrinceAdminMessage ackMessage) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Got ACK from prince");
			}
			// if we get an ACK from the wrong prince, we assume he knows he was
			// to late
			if (lastPronouncedPrinceMessage == null || !lastPronouncedPrinceMessage.getUniqueDeviceId().equals(ackMessage.getUniqueDeviceId())) {
				if (Log.logWarningMessages()) {
					Log.w(LOG_TAG, "ACK is from invalid prince, ignoring");
				}
				return;
			}
			// -- got ack from the prince, install him
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Prince ACK is valid - intalling prince and sending CensusMessage.");
			}
			if (currentTimer != null) {
				currentTimer.cancel();
				currentTimer = null;
			}
			currentPrinceUniqueId = ackMessage.getUniqueDeviceId();
			sendCencusMessage();

		}

		/**
		 * need to be called if a {@link PronouncePrinceAdminMessage} was sent
		 * 
		 * @param pronounceMessage
		 * @param ack_timeout
		 *            the timeout after which a
		 *            {@link PronouncedPrinceACKTimeoutStateMachineEvent} is
		 *            pushed to the queue if no
		 *            {@link ACKPronouncePrinceAdminMessage} arrived from the
		 *            pronounced prince
		 */
		synchronized void onPronouncedMessageSent(final PronouncePrinceAdminMessage pronounceMessage, final int ack_timeout) {
			this.lastPronouncedPrinceMessage = pronounceMessage;
			final Timer t = new Timer();
			TimerTask task = new TimerTask() {
				@Override
				public void run() {
					// if another prince was pronounced in the meantime, we do
					// nothing
					if (lastPronouncedPrinceMessage != pronounceMessage)
						return;
					// if the timer got canceled do nothing
					if (currentTimer != t)
						return;
					if (Log.logWarningMessages()) {
						Log.d(LOG_TAG, "Got no ACK from desired prince " + pronounceMessage.getUniqueDeviceId() + " for " + ack_timeout + " ms. Pushing TimeoutEvent to Queue.");
					}
					// otherwise we push the timeout
					PronouncedPrinceACKTimeoutStateMachineEvent ev = new PronouncedPrinceACKTimeoutStateMachineEvent(KingState.this);
					session.getConnectionStateMachine().pushStateMachineEvent(ev);
				}
			};
			t.schedule(task, ack_timeout);
			this.currentTimer = t;
		}

		/**
		 * needs to be called if this state receives an
		 * {@link PronouncedPrinceACKTimeoutStateMachineEvent}.
		 * 
		 * @param princeACKTimeout
		 */
		synchronized void onTimeout(PronouncedPrinceACKTimeoutStateMachineEvent princeACKTimeout) {
			// ignore timeouts from other state instances
			if (princeACKTimeout.getState() != KingState.this) {
				return;
			}
			if (Log.logWarningMessages()) {
				Log.w(LOG_TAG, "Timeout event for prince pronouncing received: re-pronouncing");
			}
			currentPrinceUniqueId = null;
			pronouncePrince();
		}

	}

	/**
	 * Builds and sends the cencus message to all connected devices.
	 */
	private void sendCencusMessage() {
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Sending cencus message");
		}
		// the map should contain all connected devices (uniqueIds) and their
		// state (Peasant or Prince)
		HashMap<String, State> connectedDevicesStates = new HashMap<String, State>();

		// add king device
		// TODO: multiple adapters? what to do? For now we use the first
		// adapter.
		IBlaubotDevice ownDevice = session.getAdapters().get(0).getOwnDevice();
		connectedDevicesStates.put(ownDevice.getUniqueDeviceID(), State.King);

		// add peasants and prince
		for (IBlaubotConnection conn : session.getConnectionManager().getAllConnections()) {
			String uniqueDeviceID = conn.getRemoteDevice().getUniqueDeviceID();
			State state = currentPrinceUniqueId != null && uniqueDeviceID.equals(currentPrinceUniqueId) ? State.Prince : State.Peasant;
			connectedDevicesStates.put(uniqueDeviceID, state);
		}

		// create and send the message
		CensusMessage censusMessage = new CensusMessage(connectedDevicesStates);
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "CensusMessage: " + censusMessage);
		}
		session.getAdminBroadcastChannel().post(censusMessage.toBlaubotMessage());
	}

	/**
	 * Pronounces a new prince based on the currently connected devices.
	 */
	private void pronouncePrince() {
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Pronouncing new prince");
		}
		// select the new prince
		List<IBlaubotConnection> connections = session.getConnectionManager().getAllConnections();
		Collections.sort(connections, new Comparator<IBlaubotConnection>() {
			@Override
			public int compare(IBlaubotConnection o1, IBlaubotConnection o2) {
				return o1.getRemoteDevice().compareTo(o2.getRemoteDevice());
			}
		});
		Collections.reverse(connections);

		// if there is at least one connection, take the one with the highest
		// device unique id (first in list)
		if (!connections.isEmpty()) {
			IBlaubotConnection princeConnection = connections.get(0);
			String newPrinceUniqueId = princeConnection.getRemoteDevice().getUniqueDeviceID();
			PronouncePrinceAdminMessage princeAdminMessage = new PronouncePrinceAdminMessage(newPrinceUniqueId);
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "New prince is " + newPrinceUniqueId + ". Sending PronouncePrinceAdminMessage ...");
			}
			// first we ensure that the prince will receive the message
			BlaubotMessage pronouncePrinceBlaubotMessage = princeAdminMessage.toBlaubotMessage();

			// we broadcast the message to all (The prince will ignore this, if
			// still prince. If not, he will step down and be peasant again)
			int pronouncing_ack_timeout = princeConnection.getRemoteDevice().getAdapter().getConnectionStateMachineConfig().getPrinceAckTimeout();
			session.getAdminBroadcastChannel().post(pronouncePrinceBlaubotMessage);
			this.princeWatcher.onPronouncedMessageSent(princeAdminMessage, pronouncing_ack_timeout);

			// currentPrinceUniqueId = newPrinceUniqueId; // currently done by
			// princeWatcher
		} else {
			currentPrinceUniqueId = null;
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Prince could not be pronounced - i have no peasants at all!");
			}
		}
		// send census to all devices
		sendCencusMessage();
	}

	@Override
	public IBlaubotState onConnectionEstablished(IBlaubotConnection connection) {
		this.noConnectionsTimer.cancel();
		pronouncePrince();
		synchronized (listenerLock) {
			if (this.peasantConnectionsListener != null)
				this.peasantConnectionsListener.onConnectionEstablished(connection);
		}
		return this;
	}

	@Override
	public IBlaubotState onConnectionClosed(IBlaubotConnection connection) {
		int connectedDevices = session.getConnectionManager().getConnectedDevices().size();
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "A connection was lost/closed. We have " + connectedDevices + " connected devices now.");
		}
		this.pronouncePrince();
		if (connectedDevices != 0)
			return this;
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting timeout and awaiting new connections. We will move to FreeState, if we get no new peasants!");
		}
		createAndStartNewTimer();
		return this;
	}

	private void createAndStartNewTimer() {
		// TODO: using the first adapter available. This must be changed if we
		// use multiple adapters in the future
		ConnectionStateMachineConfig config = session.getAdapters().get(0).getConnectionStateMachineConfig();
		final int TIMEOUT_INTERVAL = config.getKingWithoutPeasantsTimeout();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				if (session.getConnectionStateMachine().getCurrentState() != KingState.this)
					return;
				int connectedDevices = session.getConnectionManager().getConnectedDevices().size();
				if (connectedDevices != 0 || connectingToAnotherKing) {
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "King-Timeout ignored: " + connectedDevices + " connected devices. Connecting to another king: " + connectingToAnotherKing + ", connections: " + session.getConnectionManager().getAllConnections());
					}
					return;
				}
				if (Log.logWarningMessages()) {
					Log.w(LOG_TAG, "King-Timeout event posted to event queue (No connected peasants for at least " + TIMEOUT_INTERVAL + " ms)");
				}
				KingTimeoutEvent timeoutEvent = new KingTimeoutEvent(KingState.this);
				session.getConnectionStateMachine().pushStateMachineEvent(timeoutEvent);
			}
		};
		// start new timer
		synchronized (timerTaskMonitor) {
			if (this.noConnectionsTimer != null)
				this.noConnectionsTimer.cancel();
			this.noConnectionsTimer = new Timer();
			this.noConnectionsTimer.schedule(task, TIMEOUT_INTERVAL);
		}
	}

	@Override
	public IBlaubotState onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
		if (discoveryEvent instanceof DiscoveredKingEvent) {
			IBlaubotDevice remoteDevice = discoveryEvent.getRemoteDevice();
			if (remoteDevice.isGreaterThanOurDevice()) {
				// -- we have to join this good looking king!
				connectingToAnotherKing = true; // to signal the timers to hold
												// still when all the
												// connections go down
				if (Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Found a greater king than i am :-/ Have to join the new king (" + remoteDevice + ")");
					Log.d(LOG_TAG, "Connecting to king " + remoteDevice);
				}
				// connect to the king using the exponential backoff strategy
				int maxRetries = remoteDevice.getAdapter().getBlaubotAdapterConfig().getMaxConnectionRetries();
				IBlaubotConnection conn = session.getConnectionManager().connectToBlaubotDevice(remoteDevice, maxRetries);
				boolean connect = conn != null;

				if (connect) {
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Connection to new king succeeded.");
					}
					// first prevent new connections
					BlaubotAdapterHelper.stopAcceptors(session.getConnectionStateMachine().getConnectionAcceptors());

					// tell the peasants to connect to the other king
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Commanding my peasants to bow down to the new king " + remoteDevice);
					}
					final BowDownToNewKingAdminMessage bowDownMessage = new BowDownToNewKingAdminMessage(remoteDevice);
					// send only to peasants (not our new king) and to the
					// prince first
					List<IBlaubotConnection> connections = session.getConnectionManager().getAllConnections();
					connections.remove(conn);
					Collections.sort(connections, new Comparator<IBlaubotConnection>() {
						@Override
						public int compare(IBlaubotConnection arg0, IBlaubotConnection arg1) {
							if (session.getLastCensusMessage() == null)
								return 0;
							// prince is always bigger
							if (session.getLastCensusMessage().getDeviceStates().get(arg1.getRemoteDevice().getUniqueDeviceID()) == State.Prince) {
								return -1;
							}
							return 0;
						}
					});
					Collections.reverse(connections);
					for (final IBlaubotConnection c : connections) {
						Channel channel = session.getProtocolManager().getChannelFactory().getAdminDeviceChannel(c.getRemoteDevice());
						channel.post(bowDownMessage.toBlaubotMessage());
					}

					// TODO: maybe use the send callback here?!?!!?
					// sleep here for some time and hope the bow down messages
					// reaches all peasants in time
					// if they haven't disconnected themselves by now, the king
					// disconnects them after that.
					int kingdomMergeOldKingBowDownTimeout = remoteDevice.getAdapter().getConnectionStateMachineConfig().getKingdomMergeOldKingBowDownTimeout();
					try {
						Thread.sleep(kingdomMergeOldKingBowDownTimeout);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}

					// disconnect ALL connected peasants (but spare our
					// connection to the new king)
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Disconnecting all remaining connections (if any).");
					}
					// first disconnect the prince
					IBlaubotConnection princeConnection = null;
					for (IBlaubotConnection c : session.getConnectionManager().getAllConnections()) {
						if (currentPrinceUniqueId != null && c.getRemoteDevice().getUniqueDeviceID().equals(currentPrinceUniqueId)) {
							princeConnection = c;
							princeConnection.disconnect();
							break;
						}
					}
					// now disconnect the rest
					for (IBlaubotConnection c : session.getConnectionManager().getAllConnections()) {
						if (c != conn && c != princeConnection) // do not
																// terminate the
																// new
																// connection
																// (to the new
																// king) and the
																// (previously
																// disconnected)
																// prince
																// connection
							c.disconnect();
					}

					return new PeasantState(conn, ConnectionAccomplishmentType.BOWED_DOWN);
				} else {
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Connection to new king failed.");
					}
					connectingToAnotherKing = false;
					createAndStartNewTimer(); // will go to free, if there are
												// no connections
				}
			} else {
				// we are the awesomeness in person - the other king has to join
				// us
			}
		}
		return this;
	}

	@Override
	public void handleState(StateMachineSession session) {
		this.session = session;
		this.princeWatcher = new PrinceWatcher();
		BlaubotAdapterHelper.startAcceptors(session.getConnectionStateMachine().getConnectionAcceptors());
		BlaubotAdapterHelper.setDiscoveryActivated(session.getBeaconService(), false);
		// start the timer
		createAndStartNewTimer();
		sendCencusMessage();
	}

	@Override
	public IBlaubotState onAdminMessage(AbstractAdminMessage adminMessage) {
		if (adminMessage instanceof PrinceFoundAKingAdminMessage) {
			String kingsUniqueDeviceId = ((PrinceFoundAKingAdminMessage) adminMessage).getKingsUniqueDeviceId();
			if (session.isDeviceOneOfOurs(kingsUniqueDeviceId)) {
				// the idiotic prince seems to be blind - he discovered us ...
				if (Log.logWarningMessages()) {
					Log.w(LOG_TAG, "Got information about another king BUT: We should buy our prince better glasses - he discovered us. Ignoring ...");
				}
				return this;
			}
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Got informed about another King (" + kingsUniqueDeviceId + ") by my prince.");
			}
			// TODO: we need to know, to which adapter the found king's uniqueId
			// belongs !!
			// just choosing the first adapter for now
			for (IBlaubotAdapter adapter : session.getAdapters()) {
				IBlaubotDevice discoveredKingDevice = adapter.getConnector().createRemoteDevice(kingsUniqueDeviceId);
				if (discoveredKingDevice == null) {
					// device with kingsUniqueDeviceId is not
					// known/bonded/constructable by connector
					if (Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Connector could not create IBlaubotDevice instance out of the uniqueID " + kingsUniqueDeviceId + ". This happens if the device is not bonded/known or constructable by a connector. Be concerned!");
					}
					continue;
				}
				// we could construct the device for the found king and emulate
				// a discovery event
				DiscoveredKingEvent event = new DiscoveredKingEvent(discoveredKingDevice);
				return onDeviceDiscoveryEvent(event);
			}
		} else if (adminMessage instanceof ACKPronouncePrinceAdminMessage) {
			this.princeWatcher.onAck((ACKPronouncePrinceAdminMessage) adminMessage);
		}
		return this;
	}

	@Override
	public String toString() {
		return "KingState";
	}

	@Override
	public IBlaubotState onTimeoutEvent(AbstractTimeoutStateMachineEvent timeoutEvent) {
		if (timeoutEvent instanceof KingTimeoutEvent) {
			if (timeoutEvent.getState() == this) {
				ConnectionStateMachineConfig config = session.getAdapters().get(0).getConnectionStateMachineConfig();
				final int TIMEOUT_INTERVAL = config.getKingWithoutPeasantsTimeout();
				if (Log.logWarningMessages()) {
					Log.w(LOG_TAG, "King-Timeout event occured (No connected peasants for at least " + TIMEOUT_INTERVAL + " ms)");
				}
				return new FreeState();
			}
		} else if (timeoutEvent instanceof PronouncedPrinceACKTimeoutStateMachineEvent) {
			this.princeWatcher.onTimeout((PronouncedPrinceACKTimeoutStateMachineEvent) timeoutEvent);
		}

		return this;

	}

	/**
	 * Set the {@link IBlaubotConnectionListener} that will be called if we get
	 * a new {@link IBlaubotConnection} from a peasant.
	 * 
	 * @param peasantConnectionsListener
	 */
	private Object listenerLock = new Object();

	public void setPeasantConnectionsListener(IBlaubotIncomingConnectionListener peasantConnectionsListener) {
		synchronized (listenerLock) {
			this.peasantConnectionsListener = peasantConnectionsListener;
		}
	}

}
