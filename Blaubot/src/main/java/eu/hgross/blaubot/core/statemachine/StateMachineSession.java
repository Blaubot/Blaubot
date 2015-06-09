package eu.hgross.blaubot.core.statemachine;

import java.util.List;

import eu.hgross.blaubot.core.BlaubotServerConnector;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.BlaubotConnectionManager;
import eu.hgross.blaubot.core.ServerConnectionManager;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.StoppedState;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.CensusMessage;
import eu.hgross.blaubot.admin.PronouncePrinceAdminMessage;
import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.util.Log;

/**
 * Session object holding data which is shared across state changes.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class StateMachineSession {
	protected static final String LOG_TAG = "StateMachineSession";
	private final ConnectionStateMachine connectionStateMachine;
	private final BlaubotChannelManager channelManager;
	private final BlaubotConnectionManager connectionManager;
	private final BlaubotBeaconService beaconService;
    private final IBlaubotDevice ownDevice;

    private CensusMessage lastCensusMessage;
	private PronouncePrinceAdminMessage lastPronouncePrinceAdminMessage;
    private ServerConnectionManager serverConnectionManager;

    public StateMachineSession(ConnectionStateMachine stateMachine, IBlaubotDevice ownDevice, ServerConnectionManager serverConnectionManager) {
        this.serverConnectionManager = serverConnectionManager;
		this.ownDevice = ownDevice;
        this.connectionStateMachine = stateMachine;
		this.connectionManager = stateMachine.blaubot.getConnectionManager();
		this.beaconService = stateMachine.getBeaconService();
		this.channelManager= connectionStateMachine.blaubot.getChannelManager();
		channelManager.addAdminMessageListener(new IBlaubotAdminMessageListener() {
            @Override
            public void onAdminMessage(AbstractAdminMessage adminMessage) {
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

    public BlaubotChannelManager getChannelManager() {
        return channelManager;
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

    /**
     * The server connection manager
     * @return
     */
    public ServerConnectionManager getServerConnectionManager() {
        return serverConnectionManager;
    }

    /**
     * Tries to find out the if the given unique device id is a server unique device id.
     *
     * @return the uniqueDeviceId or null, if unknown or not available
     */
    public boolean isServerUniqueDeviceId(String uniqueDeviceId) {
        final BlaubotServerConnector serverConnector = getServerConnectionManager().getServerConnector();
        if (serverConnector != null) {
            final boolean isServerUniqueDeviceId = serverConnector.getServerUniqueDeviceId().equals(uniqueDeviceId);
            if (isServerUniqueDeviceId) {
                return true;
            }
        }
        // no server connector attached or does not match the server unique device id but we could still be king and using a relay connection
        // check all available server connections (if any)
        for (IBlaubotConnection connection : getServerConnectionManager().getConnectionsToServer()) {
            if (connection.getRemoteDevice().getUniqueDeviceID().equals(uniqueDeviceId)) {
                return true;
            }
        }
        return false;
    }

    /**
	 * @param uniqueId
	 *            the unique id to check
	 * @return true, if we are the device with this uniqueId
	 */
	public boolean isOwnDevice(String uniqueId) {
        return getOwnDevice().getUniqueDeviceID().equals(uniqueId);
	}

    /**
     * Checks whether the other device is greater as our own device or nots.
     *f
     * @param blaubotDevice the blaubot device to check
     * @return true iff blaubotDevice is greater than our own device
     */
    public boolean isGreaterThanOurDevice(IBlaubotDevice blaubotDevice) {
        if(ownDevice == blaubotDevice) {
            Log.w(LOG_TAG, "Comparing with myself");
            return true;
        }
        // a.compareTo(b)
        // a<b  -> <0
        // a==b ->  0
        // a>b  -> >0
        return ownDevice.compareTo(blaubotDevice) < 0;
    }

    public IBlaubotDevice getOwnDevice() {
        return ownDevice;
    }

    /**
     * Retrieves a guessed ConnectionStateMachineConfig based on the BeaconStore informations from the
     * registered connector's adapters.
     *
     * @param device the device to get the config for
     * @return the retrieved config or a default config, if not retrievable
     */
    public ConnectionStateMachineConfig getConnectionStateMachineConfigForDevice(IBlaubotDevice device) {
        final List<ConnectionMetaDataDTO> lastKnownConnectionMetaData = beaconService.getBeaconStore().getLastKnownConnectionMetaData(device.getUniqueDeviceID());
        // TODO: this gets the FIRST appropriate connector - there could be more
        final IBlaubotConnector connectorForDevice = connectionManager.getConnectorForDevice(device.getUniqueDeviceID());
        ConnectionStateMachineConfig connectionStateMachineConfig;
        if(connectorForDevice == null) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Could not retrieve specific config for device " + device + ", falling back to the most suitable config from attached connectors.");
            }
            List<IBlaubotConnector> connectionConnectors = connectionManager.getConnectionConnectors();
            if (connectionConnectors.isEmpty()) {
                throw new RuntimeException("Could neither receive a specific csm config for device " + device + " nor a fallback csm config!");
            }
            connectionStateMachineConfig = connectionConnectors.get(0).getAdapter().getConnectionStateMachineConfig();
        } else {
            connectionStateMachineConfig = connectorForDevice.getAdapter().getConnectionStateMachineConfig();
        }

        return connectionStateMachineConfig;
    }
}
