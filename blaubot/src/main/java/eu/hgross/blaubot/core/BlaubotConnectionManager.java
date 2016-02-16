package eu.hgross.blaubot.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.connector.IncompatibleBlaubotDeviceException;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.util.Log;

/**
 * Manager to store and retrieve Connections related to {@link IBlaubotDevice} instances.
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 */
public class BlaubotConnectionManager {
    /**
     * Can be used in conjunction with connectToDevice(IBlaubotDevice, int) to let the ConnectionManager
     * decide how much retries to use.
     */
    public static final int AUTO_MAX_RETRIES = -1;
    private static final String LOG_TAG = "BlaubotConnectionManager";
    private final ConcurrentHashMap<IBlaubotDevice, List<IBlaubotConnection>> connections = new ConcurrentHashMap<IBlaubotDevice, List<IBlaubotConnection>>();
    private final List<IBlaubotConnectionManagerListener> connectionListeners = new ArrayList<>();
    private final List<IBlaubotConnectionAcceptor> connectionAcceptors;
    private final List<IBlaubotConnector> connectionConnectors;
    private final IBlaubotConnectionManagerListener connectionListener; // manager's own listener listening on
    private IBlaubotBeaconStore beaconStore;

    /**
     * Creates a new {@link BlaubotConnectionManager} instance managing the given acceptors and connectors for incoming
     * and outgoing connections.
     *
     * @param acceptors  the acceptors to handle
     * @param connectors the connectors to be used to connect to other devices
     */
    public BlaubotConnectionManager(List<IBlaubotConnectionAcceptor> acceptors, List<IBlaubotConnector> connectors) {
        this.connectionConnectors = connectors;
        this.connectionAcceptors = acceptors;
        this.connectionListener = new IBlaubotConnectionManagerListener() {

            @Override
            public void onConnectionEstablished(IBlaubotConnection connection) {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Got onConnectionEstablished: " + connection);
                }
                addConnection(connection);
            }

            @Override
            public void onConnectionClosed(IBlaubotConnection connection) {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Got onConnectionClosed: " + connection);
                }
                removeConnection(connection);
            }
        };

        // We set up a listener to each acceptor to get informed about new connections
        for (IBlaubotConnectionAcceptor acceptor : connectionAcceptors) {
            acceptor.setAcceptorListener(connectionListener);
        }
        for (IBlaubotConnector connector : connectionConnectors) {
            connector.setIncomingConnectionListener(connectionListener);
        }
    }

    protected void addConnection(IBlaubotConnection connection) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Adding connection " + connection);
        }
        List<IBlaubotConnection> deviceConnections = new CopyOnWriteArrayList<>();
        this.connections.putIfAbsent(connection.getRemoteDevice(), deviceConnections);
        deviceConnections = this.connections.get(connection.getRemoteDevice());
        deviceConnections.add(connection);
        connection.addConnectionListener(connectionListener);

        // proxy event to our listeners
        for (IBlaubotConnectionManagerListener listener : connectionListeners) {
            listener.onConnectionEstablished(connection);
        }
    }

    protected void removeConnection(IBlaubotConnection connection) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Removing connection for device " + connection.getRemoteDevice());
        }
        List<IBlaubotConnection> deviceConnections = this.connections.get(connection.getRemoteDevice());
        if (deviceConnections == null) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Tried to remove a connection for device " + connection.getRemoteDevice() + " but no connection for this device was registered.");
            }
            return;
        }
        boolean removed = deviceConnections.remove(connection);
        if (!removed) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Tried to remove a non existant connection for device " + connection.getRemoteDevice() + " from ConnectionManager but connection was not registered.");
            }
        }
        // -- list was removed
        // note: we leave the empty list in the map!
        connection.removeConnectionListener(connectionListener);

        // proxy event to our listeners
        for (IBlaubotConnectionManagerListener listener : connectionListeners) {
            listener.onConnectionClosed(connection);
        }
    }

    /**
     * @param blauBotDevice the list of {@link IBlaubotDevice}s of this device.
     * @return the connection object (a socket, bluetoothsocket ...) or null, if nothing there for blauBotDevice
     */
    public List<IBlaubotConnection> getConnections(IBlaubotDevice blauBotDevice) {
        return this.connections.get(blauBotDevice);
    }

    /**
     * @return a list of devices with at least one active connection to our device
     */
    public List<IBlaubotDevice> getConnectedDevices() {
        ArrayList<IBlaubotDevice> devices = new ArrayList<>();
        // there could be devices with no connection -> filter them out
        for (IBlaubotDevice d : connections.keySet()) {
            if (!connections.get(d).isEmpty()) {
                devices.add(d);
            }
        }
        return devices;
    }

    /**
     * @return list of all active connections
     */
    public List<IBlaubotConnection> getAllConnections() {
        ArrayList<IBlaubotConnection> allConnections = new ArrayList<>();
        for (List<IBlaubotConnection> connections : this.connections.values()) {
            allConnections.addAll(connections);
        }
        return allConnections;
    }

    public void addConnectionListener(IBlaubotConnectionManagerListener listener) {
        this.connectionListeners.add(listener);
    }

    public void removeConnectionListener(IBlaubotConnectionManagerListener listener) {
        this.connectionListeners.remove(listener);
    }

    /**
     * @return list of connectors associated with this manager
     */
    public List<IBlaubotConnector> getConnectionConnectors() {
        return connectionConnectors;
    }

    /**
     * Tries to find a connector for the given uniqueDeviceId by determinig the appropriate connectors
     * via the IBlaubotBeaconStores meta data (if a beaconstore was provided).
     *
     * @param uniqueDeviceId the unique device id to connect to
     * @return the connector able to connect to one of the device's acceptors, or null, if no acceptor meta data or connector is available for this device
     */
    public IBlaubotConnector getConnectorForDevice(String uniqueDeviceId) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Searching an appropriate connector for device" + uniqueDeviceId);
        }
        if (beaconStore == null) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "I have no BeaconStore and therefore can't connect anywhere.");
            }
            return null;
        }
        // search for metadata
        final List<ConnectionMetaDataDTO> lastKnownConnectionMetaData = beaconStore.getLastKnownConnectionMetaData(uniqueDeviceId);
        if (lastKnownConnectionMetaData == null || lastKnownConnectionMetaData.isEmpty()) {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Never got acceptor meta data for this device: " + uniqueDeviceId + " and therefore can not chose a connector");
            }
            return null;
        }

        final List<String> supportedConnectionTypes = BlaubotAdapterHelper.extractSupportedConnectionTypes(connectionConnectors);
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Looking for acceptors with connection types: " + supportedConnectionTypes + "; in meta data: " + lastKnownConnectionMetaData);
        }
        for (ConnectionMetaDataDTO acceptorMetaData : lastKnownConnectionMetaData) {
            final String connectionType = acceptorMetaData.getConnectionType();
            if (supportedConnectionTypes.contains(connectionType)) {
                // find the connector with this connection type
                for (IBlaubotConnector connector : connectionConnectors) {
                    if (connector.getSupportedAcceptorTypes().contains(connectionType)) {
                        // choose this
                        return connector;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Tries to connect to the given {@link IBlaubotDevice}.
     * If not successful after maxRetries, null will be returned.
     * The retry mechanism uses the exponential backoff method which
     * waiting time is configured by the {@link BlaubotAdapterConfig}
     * for this device's adapter.
     *
     * @param device     the {@link IBlaubotDevice} to connect to
     * @param maxRetries max number of retries or BlaubotConnectionManager.AUTO_MAX_RETRIES to let the manager decide
     * @return an {@link IBlaubotConnection} or null, if no connection could be established after maxRetries
     */
    public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice device, int maxRetries) {
        final IBlaubotConnector connectorForDevice = getConnectorForDevice(device.getUniqueDeviceID());

        // connector could be null!
        if (connectorForDevice == null) {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not retrieve connector for device " + device);
            }
            return null;
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Using connector " + connectorForDevice + " to connect to device");
        }

        // use technology specific timings
        final BlaubotAdapterConfig adapterConfig = connectorForDevice.getAdapter().getBlaubotAdapterConfig();
        float backoffFactor = adapterConfig.getExponentialBackoffFactor();
        int backoffTimeout = adapterConfig.getConnectorRetryTimeout();

        // check max retries
        if (maxRetries == BlaubotConnectionManager.AUTO_MAX_RETRIES) {
            maxRetries = adapterConfig.getMaxConnectionRetries();
        }

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Trying to connect to device " + device + " using exponential backoff and max " + maxRetries + " retries.");
        }

        int outStandingRetries = maxRetries;
        while (outStandingRetries-- > 0) {
            IBlaubotConnection conn = connectToBlaubotDevice(device, connectorForDevice);
            if (conn != null) {
                return conn;
            }

            // backoff
            try {
                if (outStandingRetries == 0)
                    break;
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Backing of - outstanding retries: " + outStandingRetries);
                }
                Thread.sleep(backoffTimeout);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            backoffTimeout *= backoffFactor;
        }
        if (Log.logWarningMessages()) {
            Log.w(LOG_TAG, "Connection to " + device + " could not be established after " + maxRetries + " retries.");
        }
        return null;
    }

    /**
     * Tries to connect to the {@link IBlaubotDevice} corresponding to the given uniqueId
     * by aksing all connectors if for a device object belonging to this uniqueId and trying
     * to connect to this device.
     * <p/>
     * If not successful after maxRetries, null will be returned.
     * The retry mechanism uses the exponential backoff method which
     * waiting time is configured by the {@link BlaubotAdapterConfig}
     * for this device's adapter.
     *
     * @param uniqueId   the device's uniqueId
     * @param maxRetries max number of retries or BlaubotConnectionManager.AUTO_MAX_RETRIES to let the manager decide
     * @return an {@link IBlaubotConnection} or null, if no connection could be established after maxRetries
     */
    public IBlaubotConnection connectToBlaubotDevice(String uniqueId, int maxRetries) {
        IBlaubotDevice device = createBlaubotDeviceFromUniqueId(uniqueId);
        if (device == null) {
            return null;
        }
        return connectToBlaubotDevice(device, maxRetries);
    }

    /**
     * Tries to find a connector able to connect to the device, then tries to connect.
     *
     * @param device    the remote device to connect to
     * @param connector the connector to use
     * @return blaubot connection, if the connection was successful - false otherwise
     */
    private IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice device, IBlaubotConnector connector) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Trying to connect to device " + device + " using the connector: " + connector);
        }
        try {
            IBlaubotConnection conn = connector.connectToBlaubotDevice(device);
            boolean result = conn != null;
            if (Log.logDebugMessages()) {
                if (result)
                    Log.d(LOG_TAG, "Connection was successful");
                else
                    Log.d(LOG_TAG, "Connection failed.");
            }
            return conn;
        } catch (IncompatibleBlaubotDeviceException e) {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Connector " + connector + " not compatible.");
            }
        }
        if (Log.logErrorMessages()) {
            Log.e(LOG_TAG, "Could not connect to remote device " + device + " with connector " + connector);
        }
        return null;
    }

    /**
     * Goes through all connected devices and tries to find an IBlaubotDevice instance with this uniqueId.
     * If the device was found, it is returned. If not, a generic BlaubotDevice instance is returned.
     *
     * @param uniqueDeviceId the unique device id to create a blaubot device for
     * @return the corresponding {@link IBlaubotDevice} instance
     */
    public IBlaubotDevice createBlaubotDeviceFromUniqueId(String uniqueDeviceId) {
        if (uniqueDeviceId == null) {
            throw new NullPointerException("uniqueDeviceId can't be null");
        }
        // check if we know this device and return or create a new instance and return
        for (IBlaubotDevice device : getConnectedDevices()) {
            if (device.getUniqueDeviceID().equals(uniqueDeviceId)) {
                return device;
            }
        }
        return new BlaubotDevice(uniqueDeviceId);
    }

    /**
     * Sets the beacon store to be used to get the last beacon states and connectivity meta data
     *
     * @param beaconStore the store instance
     */
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }
}
