package eu.hgross.blaubot.core.statemachine.states;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import eu.hgross.blaubot.core.BlaubotConnectionManager;
import eu.hgross.blaubot.core.BlaubotKingdomConnection;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.StateMachineSession;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.events.AbstractTimeoutStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.events.DiscoveredKingEvent;
import eu.hgross.blaubot.core.statemachine.events.KingTimeoutEvent;
import eu.hgross.blaubot.core.statemachine.events.PronouncedPrinceACKTimeoutStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.states.PeasantState.ConnectionAccomplishmentType;
import eu.hgross.blaubot.admin.ACKPronouncePrinceAdminMessage;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.BowDownToNewKingAdminMessage;
import eu.hgross.blaubot.admin.CensusMessage;
import eu.hgross.blaubot.admin.PronouncePrinceAdminMessage;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.util.Log;

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
	 * We use a little ACK-Protocol to pronounce the prince.
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
						Log.w(LOG_TAG, "Got no ACK from desired prince " + pronounceMessage.getUniqueDeviceId() + " for " + ack_timeout + " ms. Pushing TimeoutEvent to Queue.");
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
			if (princeACKTimeout.getConnectionStateMachineState() != KingState.this) {
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
		final HashMap<String, State> connectedDevicesStates = new HashMap<String, State>();

		// add king device
		final IBlaubotDevice ownDevice = session.getOwnDevice();
		connectedDevicesStates.put(ownDevice.getUniqueDeviceID(), State.King);

		// add peasants and prince
		for (IBlaubotConnection conn : session.getConnectionManager().getAllConnections()) {
			String uniqueDeviceID = conn.getRemoteDevice().getUniqueDeviceID();
			State state = currentPrinceUniqueId != null && uniqueDeviceID.equals(currentPrinceUniqueId) ? State.Prince : State.Peasant;
			connectedDevicesStates.put(uniqueDeviceID, state);
		}

		// create and send the message
		final CensusMessage censusMessage = new CensusMessage(connectedDevicesStates);
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "CensusMessage: " + censusMessage);
		}
		session.getChannelManager().broadcastAdminMessage(censusMessage.toBlaubotMessage());
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


        // filter server connection(s)
        BlaubotKingdomConnection currentlyUsedServerConnection = session.getServerConnectionManager().getCurrentlyUsedServerConnection();
        if(currentlyUsedServerConnection != null) {
            ArrayList<IBlaubotConnection> toRemove = new ArrayList<>();
            for(IBlaubotConnection connection : connections) {
				final String uniqueDeviceID = connection.getRemoteDevice().getUniqueDeviceID();
				if (session.isServerUniqueDeviceId(uniqueDeviceID)) {
                    toRemove.add(connection);
                }
            }
            connections.removeAll(toRemove);
        }

		// if there is at least one connection, take the one with the highest
		// device unique id (first in list)
		if (!connections.isEmpty()) {
			IBlaubotConnection princeConnection = connections.get(0);
			String newPrinceUniqueId = princeConnection.getRemoteDevice().getUniqueDeviceID();
            final List<ConnectionMetaDataDTO> lastKnownConnectionMetaData = session.getBeaconService().getBeaconStore().getLastKnownConnectionMetaData(newPrinceUniqueId);
            PronouncePrinceAdminMessage princeAdminMessage = new PronouncePrinceAdminMessage(newPrinceUniqueId, lastKnownConnectionMetaData);
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "New prince is " + newPrinceUniqueId + ". Sending PronouncePrinceAdminMessage ...");
			}
			// first we ensure that the prince will receive the message
			BlaubotMessage pronouncePrinceBlaubotMessage = princeAdminMessage.toBlaubotMessage();

			// we broadcast the message to all (The prince will ignore this, if
			// still prince. If not, he will step down and be peasant again)
            final ConnectionStateMachineConfig connectionStateMachineConfigForDevice = session.getConnectionStateMachineConfigForDevice(princeConnection.getRemoteDevice());
            int pronouncing_ack_timeout = connectionStateMachineConfigForDevice.getPrinceAckTimeout();
            session.getChannelManager().publishToAllConnections(pronouncePrinceBlaubotMessage);
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
        int connectedDevices = countConnections();
        if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "A connection was lost/closed. We have " + connectedDevices + " connected devices now.");
		}
		this.pronouncePrince();
        if (connectedDevices != 0) {
            return this;
        }
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting timeout and awaiting new connections. We will move to FreeState, if we get no new peasants!");
		}
		createAndStartNewTimer();
		return this;
	}

    /**
     * Counts the DIRECT connections to other devices.
     * This means all connections minus the connection to the server (if any)
     * @return number of connections wihtout server connection
     */
    private int countConnections() {
        // all connections
        int connectedDevicesCount = session.getConnectionManager().getConnectedDevices().size();

        // subtract server connection, if any
        IBlaubotConnection serverConnection = session.getServerConnectionManager().getCurrentlyUsedServerConnection();
        if(serverConnection != null) {
            connectedDevicesCount -= 1;
        }
        return connectedDevicesCount;
    }

    /**
     * Starts the timer for the king timeout (no peasants for some time)
     */
	private void createAndStartNewTimer() {
		// TODO: using the first adapter available. This must be changed if we
		// use multiple adapters in the future
		ConnectionStateMachineConfig config = session.getAdapters().get(0).getConnectionStateMachineConfig();
		final int TIMEOUT_INTERVAL = config.getKingWithoutPeasantsTimeout();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
                if (session.getConnectionStateMachine().getCurrentState() != KingState.this) {
                    return;
                }

                // count connected devices without the server
                int connectedDevices = countConnections();

                if (connectedDevices > 0 || connectingToAnotherKing) {
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
            DiscoveredKingEvent discoveredKingEvent = (DiscoveredKingEvent) discoveryEvent;
			IBlaubotDevice remoteDevice = discoveryEvent.getRemoteDevice();
            if(session.isOwnDevice(remoteDevice.getUniqueDeviceID())) {
                if (Log.logDebugMessages()) {
                    Log.w(LOG_TAG, "Discovered myself - what a surprise.");
                }
                return this; // ignore own discovery
            }

            if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Got informed about another King (" + discoveredKingEvent.getRemoteDevice() + ").");
			}

			if (session.isGreaterThanOurDevice(remoteDevice)) {
				// -- we have to join this good looking king!
				connectingToAnotherKing = true; // to signal the timers to hold
												// still when all the
												// connections go down
				if (Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Found a greater king than i am :-/ Have to join the new king (" + remoteDevice + ")");
					Log.d(LOG_TAG, "Connecting to king " + remoteDevice);
				}
				// connect to the king using the exponential backoff strategy
				IBlaubotConnection conn = session.getConnectionManager().connectToBlaubotDevice(remoteDevice, BlaubotConnectionManager.AUTO_MAX_RETRIES);
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
                    final List<ConnectionMetaDataDTO> lastKnownConnectionMetaData = session.getBeaconService().getBeaconStore().getLastKnownConnectionMetaData(remoteDevice.getUniqueDeviceID());
                    final BowDownToNewKingAdminMessage bowDownMessage = new BowDownToNewKingAdminMessage(remoteDevice.getUniqueDeviceID(), lastKnownConnectionMetaData);
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
						// send bow down message only to remote device
                        session.getChannelManager().publishToSingleDevice(bowDownMessage.toBlaubotMessage(), c.getRemoteDevice().getUniqueDeviceID());
					}

					// sleep here for some time and hope the bow down messages
					// reaches all peasants in time
					// if they haven't disconnected themselves by now, the king
					// disconnects them after that.
                    final ConnectionStateMachineConfig stateMachineConfigForDevice = session.getConnectionStateMachineConfigForDevice(remoteDevice);
                    int kingdomMergeOldKingBowDownTimeout = stateMachineConfigForDevice.getKingdomMergeOldKingBowDownTimeout();
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
						// do not terminate the new connection (to the new king) and the (previously disconnected) prince connection
						if (c != conn && c != princeConnection) {
							c.disconnect();
						}
					}

					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Will now transition to PeasantState (BOWED_DOWN).");
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
                if(Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "I am the greater king, not bowing down.");
                }
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
        session.getServerConnectionManager().setMaster(true);
	}

	@Override
	public IBlaubotState onAdminMessage(AbstractAdminMessage adminMessage) {
        /**
         * The PrinceFoundAKingAdminMessage is handled by the AdminMessageBeacon now
         */

//		if (adminMessage instanceof PrinceFoundAKingAdminMessage) {
//            final PrinceFoundAKingAdminMessage princeFoundAKingAdminMessage = (PrinceFoundAKingAdminMessage) adminMessage;
//            String kingsUniqueDeviceId = princeFoundAKingAdminMessage.getKingsUniqueDeviceId();
//			if (session.isOwnDevice(kingsUniqueDeviceId)) {
//				// the idiotic prince seems to be blind - he discovered us ...
//				if (Log.logWarningMessages()) {
//					//Log.w(LOG_TAG, "Got information about another king BUT: We should buy our prince better glasses - he discovered us. Ignoring ...");
//				}
//				return this;
//			}
//			if (Log.logDebugMessages()) {
//				Log.d(LOG_TAG, "Got informed about another King (" + kingsUniqueDeviceId + ") by my prince.");
//			}
//			// TODO: we need to know, to which adapter the found king's uniqueId belongs !!
//			// just choosing the first adapter for now
//			for (IBlaubotAdapter adapter : session.getAdapters()) {
//				IBlaubotDevice discoveredKingDevice = adapter.getConnector().createRemoteDevice(kingsUniqueDeviceId);
//				if (discoveredKingDevice == null) {
//					// device with kingsUniqueDeviceId is not
//					// known/bonded/constructable by connector
//					if (Log.logWarningMessages()) {
//						Log.w(LOG_TAG, "Connector could not create IBlaubotDevice instance out of the uniqueID " + kingsUniqueDeviceId + ". This happens if the device is not bonded/known or constructable by a connector. Be concerned!");
//					}
//					continue;
//				}
//				// we emulate a discovery event
//                final List<ConnectionMetaDataDTO> connectionMetaDataList = princeFoundAKingAdminMessage.getConnectionMetaDataList();
//                DiscoveredKingEvent event = new DiscoveredKingEvent(discoveredKingDevice, connectionMetaDataList);
//				return onDeviceDiscoveryEvent(event);
//			}
//		}

        if (adminMessage instanceof ACKPronouncePrinceAdminMessage) {
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
			if (timeoutEvent.getConnectionStateMachineState() == this) {
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
