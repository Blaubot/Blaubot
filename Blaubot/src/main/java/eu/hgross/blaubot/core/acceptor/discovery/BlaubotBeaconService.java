package eu.hgross.blaubot.core.acceptor.discovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.BlaubotConnectionManager;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.ConnectionStateMachine;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import eu.hgross.blaubot.util.Log;

/**
 * This service handles the beacons on all interfaces. The beacon implementations simply hands the accepted connections
 * to this object and the actual conversation is generalized at this object's level.
 * 
 * After receiving a {@link IBlaubotConnection} from a {@link IBlaubotBeacon} the BeaconService exchanges {@link State}s
 * with the remote peer.
 * 
 * The remote beacon implementation will most likely use the {@link ExchangeStatesTask} to exchange their state with the
 * {@link BeaconConnectionHandler} of this objects.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotBeaconService {
	private static final String LOG_TAG = "BlaubotBeaconService";
    private final List<IBlaubotConnectionAcceptor> connectionAcceptors;
    private final IBlaubotDevice ownDevice;
    private List<IBlaubotBeacon> blaubotBeacons;
	private ExecutorService executorService;
	private BlaubotConnectionManager beaconConnectionManager;
	private volatile BeaconMessage currentBeaconMessage; // maintained through onStateChanged(..) calls from the StateMachine
    private final List<IBlaubotDiscoveryEventListener> discoveryEventListeners; // proxy listeners
    private final ConnectionStateMachine connectionStateMachine;
    private final IBlaubotBeaconStore beaconStore;


    /**
     * @param blaubotBeacons the list of beacons to be managed
     * @param connectionAcceptors the acceptors to be exposed by the beacons
     * @param connectionStateMachine the connection state machine which state should be exposed by the beacons
     */
	public BlaubotBeaconService(IBlaubotDevice ownDevice, List<IBlaubotBeacon> blaubotBeacons, List<IBlaubotConnectionAcceptor> connectionAcceptors, ConnectionStateMachine connectionStateMachine) {
        this.connectionStateMachine = connectionStateMachine;
        this.ownDevice = ownDevice;
        this.currentBeaconMessage = new BeaconMessage(ownDevice.getUniqueDeviceID(), State.Stopped, BlaubotAdapterHelper.getConnectionMetaDataList(connectionAcceptors));
        this.blaubotBeacons = blaubotBeacons;
        this.executorService = Executors.newCachedThreadPool();
        this.discoveryEventListeners = new CopyOnWriteArrayList<>();
        this.beaconStore = new BlaubotBeaconStore();
        this.addDiscoveryEventListener((BlaubotBeaconStore)this.beaconStore);
        this.connectionAcceptors = connectionAcceptors;
		this.beaconConnectionManager = new BlaubotConnectionManager(new ArrayList<IBlaubotConnectionAcceptor>(blaubotBeacons), new ArrayList<IBlaubotConnector>());
		this.beaconConnectionManager.addConnectionListener(new IBlaubotConnectionManagerListener() {
			@Override
			public void onConnectionEstablished(IBlaubotConnection connection) {
				handleBeaconConnection(connection);
			}

			@Override
			public void onConnectionClosed(IBlaubotConnection connection) {
                // ignore
			}
		});

        // listen to all the used beacons
        for (IBlaubotBeacon beaconInterface : blaubotBeacons) {
            beaconInterface.setDiscoveryEventListener(discoveryEventListener);
        }
	}

    /**
     * Listener is attached to all beacons and will therefore receive all discovery events
     */
    private IBlaubotDiscoveryEventListener discoveryEventListener = new IBlaubotDiscoveryEventListener() {
        @Override
        public void onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
            IBlaubotState currentState = connectionStateMachine.getCurrentState();
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "[Current state: " + currentState + "] Got discovery Event " + discoveryEvent);
            }
			if (discoveryEvent.getRemoteDevice().getUniqueDeviceID().equals(ownDevice.getUniqueDeviceID())) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "[Current state: " + currentState + "] Discovery event was our own event, ignoring");
				}
				// dont dispatch events of our own
				return;
			}

            // We forward the discovery events of all beacons to the registered listeners.
            for(IBlaubotDiscoveryEventListener listener : discoveryEventListeners) {
                listener.onDeviceDiscoveryEvent(discoveryEvent);
            }

            // inject the currentState
            discoveryEvent.setConnectionStateMachineState(currentState);
            connectionStateMachine.pushStateMachineEvent(discoveryEvent);

        }
    };

    /**
     * Adds an {@link IBlaubotDiscoveryEventListener} to the beacon service.
     * The listeners are called for each discovery event of any {@link IBlaubotBeacon}.
     * @param discoveryEventListener
     */
    public void addDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
        this.discoveryEventListeners.add(discoveryEventListener);
    }

    /**
     * Removes a listener
     * @param discoveryEventListener
     */
    public void removeDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
        this.discoveryEventListeners.remove(discoveryEventListener);
    }

	private void handleBeaconConnection(IBlaubotConnection beaconConnection) {
		BeaconConnectionHandler task = new BeaconConnectionHandler(beaconConnection);
		this.executorService.execute(task);
	}

	public void startBeaconInterfaces() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting beacon interfaces ... ");
		}
		for (IBlaubotBeacon beaconInterface : this.blaubotBeacons) {
			if (beaconInterface.isStarted()) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Beacon " + beaconInterface + " is already started - skipping.");
				}
				continue;
			}
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Starting beacon interface " + beaconInterface + " ... ");
			}
			beaconInterface.startListening();
		}
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Beacons started.");
		}
	}

	public void stopBeaconInterfaces() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Stopping beacon interfaces - going through all beacons ...");
		}
		for (IBlaubotBeacon beaconInterface : this.blaubotBeacons) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "\tStopping beacon interface: " + beaconInterface);
			}
			beaconInterface.stopListening();
		}
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Beacon interfaces stopped.");
		}
	}

	/**
	 * A Task that handles the conversation with beacon clients.
	 * It is the counterpart of the {@link ExchangeStatesTask}.
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	private class BeaconConnectionHandler implements Runnable {
		private static final String LOG_TAG = "BeaconConnectionHandler";
		private IBlaubotConnection connection;

		public BeaconConnectionHandler(IBlaubotConnection connection) {
			this.connection = connection;
		}

		@Override
		public void run() {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Starting to handle beacon connection " + connection);
			}
			/*
			 * TODO: add this to documentation: 
			 * The beacon message protocol is as follows: 
			 *  1. The connecting side retrieves our state.
			 *  2. After that it sends us it's state which we will retrieve.
			 *  3. The connection closes
			 */
			
			// Send them our state
			BeaconMessage ourMessage = currentBeaconMessage;
			try {
				connection.write(ourMessage.toBytes());
			} catch (IOException e) {
				if(Log.logErrorMessages()) {
					Log.e(LOG_TAG, "Failed to send message to connected beacon client. Closing connection");
				}
				connection.disconnect();
				return;
			}
			// Get their state
			BeaconMessage theirMessage = BeaconMessage.fromBlaubotConnection(connection);
			connection.disconnect();
			
			// propagate events
			if(theirMessage != null) {

                IBlaubotDevice remoteDevice = connection.getRemoteDevice();
                State theirState = theirMessage.getCurrentState();

                AbstractBlaubotDeviceDiscoveryEvent discoveryEvent = theirState.createDiscoveryEventForDevice(remoteDevice, theirMessage.getOwnConnectionMetaDataList());
                discoveryEventListener.onDeviceDiscoveryEvent(discoveryEvent);

                if(theirMessage.getCurrentState() == State.Peasant || theirMessage.getCurrentState() == State.Prince) {
                    // if they are peasant or prince they should have a king and have sent us their uniqueDeviceId as well as the kings acceptor meta data
                    // so we generate another discovery event from that without bothering for any beacon transactions
                    if(Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "The other side has a king, generating the king event for the remote king");
                    }
                    String kingDeviceUniqueId = theirMessage.getKingDeviceUniqueId();
                    IBlaubotDevice kingDevice = new BlaubotDevice(kingDeviceUniqueId);
                    AbstractBlaubotDeviceDiscoveryEvent kingDiscoveryEvent = State.King.createDiscoveryEventForDevice(kingDevice, theirMessage.getKingsConnectionMetaDataList());
                    discoveryEventListener.onDeviceDiscoveryEvent(kingDiscoveryEvent);
                }
			}
			connection.disconnect();
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Done handling beacon connection " + connection);
			}
		}

	}

	/**
	 * Informs the BlaubotBeaconService that the blaubot connection state has changed.
	 * 
	 * @param newState
	 */
	public void onStateChanged(IBlaubotState newState) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "State changed, building new BeaconMessage");
        }

        // build the beacon message
        final State state = State.getStateByStatemachineClass(newState.getClass());
        final List<ConnectionMetaDataDTO> ownConnectionMetaDataList = BlaubotAdapterHelper.getConnectionMetaDataList(connectionAcceptors);
        final String ownDeviceUniqueDeviceID = ownDevice.getUniqueDeviceID();
        if (newState instanceof IBlaubotSubordinatedState) { // prince or peasant state
            final String kingUniqueId = ((IBlaubotSubordinatedState) newState).getKingUniqueId();
            final List<ConnectionMetaDataDTO> kingConnectionMetaDataList = beaconStore.getLastKnownConnectionMetaData(kingUniqueId);
            if (kingConnectionMetaDataList == null) {
                throw new IllegalStateException("Could not get connection metadata information for our king but we are in a subordinate state!");
            }
            currentBeaconMessage = new BeaconMessage(ownDeviceUniqueDeviceID, state, ownConnectionMetaDataList, kingUniqueId, kingConnectionMetaDataList);
        } else {
            currentBeaconMessage = new BeaconMessage(ownDeviceUniqueDeviceID, state, ownConnectionMetaDataList);
        }

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "New cached BeaconMessage: " + currentBeaconMessage);
        }

    }

    /**
     * Get all managed beacons
     * @return the list of managed beacons
     */
	public List<IBlaubotBeacon> getBeacons() {
		return blaubotBeacons;
	}

    /**
     * Get the beacon store corresponding to the managed beacons
     * @return the store
     */
    public IBlaubotBeaconStore getBeaconStore() {
        return beaconStore;
    }

    /**
     * The current beacon message holding our state and connection meta data
     * @return the beacon message ready to send
     */
    public BeaconMessage getCurrentBeaconMessage() {
        return currentBeaconMessage;
    }
}
