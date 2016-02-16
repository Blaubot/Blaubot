package eu.hgross.blaubot.ethernet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.connector.IncompatibleBlaubotDeviceException;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.util.Log;

/**
 * Connector for ethernet
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotEthernetConnector implements IBlaubotConnector {
	private static final String LOG_TAG = "BlaubotEthernetConnector";
    private static final List<String> SUPPORTED_ACCEPTOR_TYPES = Arrays.asList(EthernetConnectionMetaDataDTO.ACCEPTOR_TYPE);
    private final IBlaubotAdapter adapter;
    private final IBlaubotDevice ownDevice;
    private IBlaubotIncomingConnectionListener incomingConnectionListener;
    private IBlaubotBeaconStore beaconStore;

    public BlaubotEthernetConnector(IBlaubotAdapter blaubotEthernetAdapter, IBlaubotDevice ownDevice) {
        this.ownDevice = ownDevice;
		this.adapter = blaubotEthernetAdapter;
	}

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener acceptorConnectorListener) {
		this.incomingConnectionListener = acceptorConnectorListener;
	}



	@Override
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) {
        final String uniqueDeviceID = blaubotDevice.getUniqueDeviceID();
        final List<ConnectionMetaDataDTO> lastKnownConnectionMetaData = beaconStore.getLastKnownConnectionMetaData(uniqueDeviceID);

        if(lastKnownConnectionMetaData == null) {
            if(Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not get connection meta data for unique device id " + uniqueDeviceID);
            }
            return null;
        }

        // take the first supported acceptor, if any
        final List<ConnectionMetaDataDTO> supportedAcceptors = BlaubotAdapterHelper.filterBySupportedAcceptorTypes(lastKnownConnectionMetaData, getSupportedAcceptorTypes());
        if(supportedAcceptors.isEmpty()) {
            if(Log.logErrorMessages()) {
                Log.e(LOG_TAG, "No supported acceptors in meta data to connect to " + uniqueDeviceID + " unfiltered list: " + lastKnownConnectionMetaData);
            }
            throw new IncompatibleBlaubotDeviceException(blaubotDevice + " could not get acceptor meta data for this device.");
        }

        EthernetConnectionMetaDataDTO ethernetConnectionMetaData = new EthernetConnectionMetaDataDTO(supportedAcceptors.get(0));
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Chosen acceptor to connect to: " + ethernetConnectionMetaData);
            Log.d(LOG_TAG, "Last DiscoveryEvent for this deviceId: "+beaconStore.getLastDiscoveryEvent(uniqueDeviceID));
        }

        return connectToBlaubotDevice(blaubotDevice, ethernetConnectionMetaData);
	}

    /**
     * Connects to the given device using the given connection params.
     * This is a specialized method to bypass some validations to enable the re-use of this connector as a delegate.
     *
     * @param blaubotDevice the device to connect to
     * @param ethernetConnectionMetaData the connection meta data to use for the connection
     * @return the connection or null, if failed
     */
    public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice, EthernetConnectionMetaDataDTO ethernetConnectionMetaData) {
        int remoteAcceptorPort = ethernetConnectionMetaData.getAcceptorPort();
        final String ipAddress = ethernetConnectionMetaData.getIpAddress();


        // connect - other side is EthernetAcceptor, see there
        Socket remoteSocket = null;
        final int connectionTimeout = adapter.getBlaubotAdapterConfig().getConnectionTimeout();
        long start = System.currentTimeMillis();
        try {
            try {
                InetAddress remoteAddress = InetAddress.getByName(ipAddress);
                remoteSocket = new Socket();
                remoteSocket.connect(new InetSocketAddress(remoteAddress, remoteAcceptorPort), connectionTimeout);

                BlaubotEthernetUtils.sendOwnUniqueIdThroughSocket(ownDevice, remoteSocket);

                BlaubotEthernetConnection connection = new BlaubotEthernetConnection(blaubotDevice, remoteSocket);

                // send our message
                final BeaconMessage currentBeaconMessage = adapter.getBlaubot().getConnectionStateMachine().getBeaconService().getCurrentBeaconMessage();
                connection.write(currentBeaconMessage.toBytes());

                if (Log.logDebugMessages()) {
                    long diff = System.currentTimeMillis() - start;
                    Log.d(LOG_TAG, "Connection successful, took " + diff + " ms");
                }

                if (incomingConnectionListener != null) {
                    incomingConnectionListener.onConnectionEstablished(connection);
                }
                return connection;
            } catch (UnknownHostException e) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Could not extract InetAddress from: " + ipAddress);
                }
                throw e;
            } catch (SocketTimeoutException e) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Timeout: Could not connect socket to remote device (after " + connectionTimeout + " ms)");
                }
            }
        } catch (IOException e) {
            if(Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Could not connect or write to " + ipAddress + ":" + remoteAcceptorPort + " (" + e.getMessage() + ")");
            }
            if(remoteSocket != null) {
                try {
                    remoteSocket.close();
                } catch (IOException e1) {
                }
            }
        }
        if(Log.logWarningMessages()) {
            long diff = System.currentTimeMillis() - start;
            Log.w(LOG_TAG, "Failed to connect to " + ipAddress + ":" + remoteAcceptorPort + " after " + diff + " ms");
        }
        return null;
    }

    @Override
    public List<String> getSupportedAcceptorTypes() {
        return SUPPORTED_ACCEPTOR_TYPES;
    }

}
