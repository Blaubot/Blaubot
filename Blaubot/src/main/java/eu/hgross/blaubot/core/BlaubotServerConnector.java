package eu.hgross.blaubot.core;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.util.Log;

/**
 * This class connects to BlaubotServer instances.
 * It consists of a BeaconStore holding the immutable connection params for the server and
 * a set of connectors to be used to connect to the given connection params.
 * <p/>
 * It is designed to be started from a lifecycle listener and should be activated/deactivated by
 * the connect/disconnect events.
 */
public class BlaubotServerConnector  {
    private static final String LOG_TAG = "BlaubotServerConnector";
    /**
     * Min Interval between timer task executions that check the server status
     */
    private static final long CHECK_CONNECTIVITY_INTERVAL = 1000;
    private static final long SHUTDOWN_TERMINATION_TIMEOUT = 6000;
    /**
     * Connectors to be used to connect to the server
     */
    private final List<IBlaubotConnector> connectors;
    /**
     * The beacon store containing connection information to connect to the serverDevice
     */
    private final IBlaubotBeaconStore beaconStore;
    /**
     * The server device to connect to
     */
    private final IBlaubotDevice serverDevice;

    /**
     * Connection to the server.
     * If not null, a connection to the server was successfully established
     */
    private volatile IBlaubotConnection serverConnection;
    /**
     * Locks acccess to the serverConnection
     */
    private Object serverConnectionLock = new Object();

    /**
     * Schedules the connectTimerTask.
     * Is created and shut down on activate/deactivate.
     */
    private volatile ScheduledExecutorService scheduledExecutorService;

    /**
     * This is the switch which activates or deactivates the server connection.
     * It can be set via setDoConnect(true/false).
     * If not active, the connection will not be established.
     * If active the connector tries to connect to the server and allows the network to use
     * this connection as the relay to the server.
     */
    private volatile boolean doConnect = true;

    /**
     * A TimerTask that checks if a connection exists and if not tries to connect to the
     * server.
     */
    private Runnable connectTimerTask = new Runnable() {
        @Override
        public void run() {
            try {
                if (Log.logDebugMessages()) {
//                    Log.d(LOG_TAG, "ConnectTimerTask running ...");
                }

                if(!doConnect) {
                    // we don't want to connect (or act as relay)
                    return;
                }

                if (!isConnected()) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Not connected. Connecting ...");
                    }
                    boolean result = connectToServer();

                    if (result) {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Now connected to " + serverConnection.getRemoteDevice());
                        }
                    } else {
                        if (Log.logWarningMessages()) {
                            Log.w(LOG_TAG, "Failed to connect to " + serverDevice);
                        }
                    }
                }

                if (Log.logDebugMessages()) {
//                    Log.d(LOG_TAG, "ConnectTimerTask done.");
                }
            } catch (Throwable t) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Task failed", t);
                }
                t.printStackTrace();
            }
        }
    };

    /**
     * A listener that is attached by a user to get informed about new connections established by
     * the connector.
     * May be null.
     */
    private IBlaubotIncomingConnectionListener incomingConnectionListener;

    /**
     * @param serverUniqueDeviceId the server's static unqiueDevice id
     * @param beaconStore          the beacon store to be used. Should already contain connection meta data to conncet to serverUniqueDeviceId
     * @param connectors           the list of connectors to be used to establish a connection to the server device
     */
    public BlaubotServerConnector(String serverUniqueDeviceId, IBlaubotBeaconStore beaconStore, List<IBlaubotConnector> connectors) {
        this.serverDevice = new BlaubotDevice(serverUniqueDeviceId);
        this.beaconStore = beaconStore;
        this.connectors = connectors;
        for (IBlaubotConnector connector : connectors) {
            connector.setBeaconStore(this.beaconStore);
            connector.setIncomingConnectionListener(connectorListener);
        }
    }

    /**
     * The configured server's unique device id
     * @return server's uniqueDeviceId
     */
    public String getServerUniqueDeviceId() {
        return serverDevice.getUniqueDeviceID();
    }

    /**
     * Gets called if a conneciton was established by one of the attached connectors
     */
    private final IBlaubotIncomingConnectionListener connectorListener = new IBlaubotIncomingConnectionListener() {
        @Override
        public void onConnectionEstablished(IBlaubotConnection connection) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "ConnectionEstablished: " + connection);
            }
            synchronized (serverConnectionLock) {
                serverConnection = connection;
            }

            // if a user attached a listener, call
            if (incomingConnectionListener != null) {
                incomingConnectionListener.onConnectionEstablished(connection);
            }
        }
    };

    /**
     * Tries to connect to the server device using all connectors sequentially until a connection could
     * be established.
     *
     * @return true iff connected to the server
     */
    private boolean connectToServer() {
        if (isConnected()) {
            return true;
        }

        // try to connect to the server
        for (IBlaubotConnector connector : connectors) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Trying to connect to server " + serverDevice + " with connector " + connector);
            }
            IBlaubotConnection connection = connector.connectToBlaubotDevice(serverDevice);
            boolean connected = connection != null;
            if (connected) {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Got a connection, attaching close listener ... ");
                }
                connection.addConnectionListener(new IBlaubotConnectionListener() {
                    @Override
                    public void onConnectionClosed(IBlaubotConnection connection) {
                        synchronized (serverConnectionLock) {
                            if(BlaubotServerConnector.this.serverConnection == connection) {
                                BlaubotServerConnector.this.serverConnection = null;
                            }
                        }
                    }
                });
                return true;
            } else {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Connection with connector " + connector + " failed.");
                }
            }
        }
        if (Log.logWarningMessages()) {
            Log.w(LOG_TAG, "Failed to connect to server " + serverDevice);
        }
        return false;
    }

    /**
     * Indicates whether a connection to the server is available or not.
     * If true, the connection is retrievable via getServerConnection()
     *
     * @return true iff a connection to the server is available
     */
    public boolean isConnected() {
        synchronized (serverConnectionLock) {
            return serverConnection != null;
        }
    }

    /**
     * Returns the connection to the server, if available
     *
     * @return the connection to the server or null, if not connected
     */
    public IBlaubotConnection getServerConnection() {
        return serverConnection;
    }

    /**
     * Activates the connector.
     * The connector will poll the connectivity state and initiate a connection to the server if
     * not currently connected.
     */
    public void activateServerConnector() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Activating server connector");
        }
        if (this.scheduledExecutorService != null) {
            return; // already started
        }
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.scheduledExecutorService.scheduleAtFixedRate(connectTimerTask, 0, CHECK_CONNECTIVITY_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Deactivates the connector and the connectivity polling.
     */
    public void deactivateServerConnector() {
        Log.d(LOG_TAG, "Deactivating server connector");
        if (this.scheduledExecutorService != null) {
            this.scheduledExecutorService.shutdownNow();
            final boolean timedOut;
            try {
                timedOut = !this.scheduledExecutorService.awaitTermination(SHUTDOWN_TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS);
                if (timedOut && Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "ExecutorService termination timeout");
                }
            } catch (InterruptedException e) {
                
            } finally {
                this.scheduledExecutorService = null;
            }
        }
    }

    /**
     * Sets the incoming connection listener which is called if a connection to the server was established.
     *
     * @param incomingConnectionListener
     */
    public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener incomingConnectionListener) {
        this.incomingConnectionListener = incomingConnectionListener;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BlaubotServerConnector{");
        sb.append("connectors=").append(connectors);
        sb.append(", beaconStore=").append(beaconStore);
        sb.append(", serverDevice=").append(serverDevice);
        sb.append('}');
        return sb.toString();
    }

    public static void main(String[] args) {
        /**
         * Sample usage of the connector.
         * Use the main of BlaubotServer as server.
         */

        /*
        // client device
        IBlaubotDevice ownDevice = new BlaubotDevice("A_Client");

        // server data
        final String SERVER_UNIQUE_DEVICE_ID = "Server1";
        WebsocketConnectionMetaDataDTO connectionMetaData = new WebsocketConnectionMetaDataDTO("127.0.0.1", "/blaubot", 8080);

        // supply connectors by creating adapters
        BlaubotWebsocketAdapter websocketAdapter = new BlaubotWebsocketAdapter(ownDevice, "0.0.0.0", 8080);
        ArrayList<IBlaubotConnector> connectors = new ArrayList<>();
        connectors.add(websocketAdapter.getConnector());

        // provide a beacon store with the server's connect meta data inside
        BlaubotBeaconStore beaconStore = new BlaubotBeaconStore();
        List<ConnectionMetaDataDTO> connectionMetaDataList = new ArrayList<>();
        connectionMetaDataList.add(connectionMetaData);
        beaconStore.putConnectionMetaData(SERVER_UNIQUE_DEVICE_ID, connectionMetaDataList);

        // create the server connector
        BlaubotServerConnector bsc = new BlaubotServerConnector(SERVER_UNIQUE_DEVICE_ID, beaconStore, connectors);

        // start it
        bsc.activateServerConnector();
        */

    }

    /**
     * This is the switch which activates or deactivates the server connection.
     * If not active, the connection will not be established.
     * If active the connector tries to connect to the server and allows the network to use
     * this connection as the relay to the server.
     * If changing from active to inactive, (doConnect == false), then a possibly established connection
     * will be disconnected.
     * @param doConnect true if the connector should connect, false if not.
     */
    public void setDoConnect(boolean doConnect) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Changed doConnect to " + doConnect);
        }
        final boolean changed = this.doConnect != doConnect;
        if (changed) {
            this.doConnect = doConnect;
            synchronized (serverConnectionLock) {
                if (this.serverConnection != null) {
                    this.serverConnection.disconnect();
                }
            }
        }
    }

    /**
     *
     * @return true iff the serverConnector is trying to establish a connection
     */
    public boolean getDoConnect() {
        return doConnect;
    }
}
