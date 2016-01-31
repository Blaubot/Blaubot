package eu.hgross.blaubot.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.CloseRelayConnectionAdminMessage;
import eu.hgross.blaubot.admin.RelayAdminMessage;
import eu.hgross.blaubot.admin.ServerConnectionAvailableAdminMessage;
import eu.hgross.blaubot.admin.ServerConnectionDownAdminMessage;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.BlaubotMessageReceiver;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;
import eu.hgross.blaubot.util.Log;

/**
 * Manages the connection to the BlaubotServer (if set).
 * It makes use of the BlaubotChannelManager to listen and send admin messages to other devices.
 * <p/>
 * It operates in two modes:
 * Client and Master (as the BlaubotChannelManager).
 * <p/>
 * It always sends admin messages if a connection to the server is available or down.
 * <p/>
 * In client mode it listens to RelayAdminMessages and sends them to the available server connection (if any).
 * <p/>
 * In server mode it listens to the Available/Down messages and collects possible connections to the
 * server as there could be more than just one.
 */
public class ServerConnectionManager extends LifecycleListenerAdapter {
    private static final String LOG_TAG = "ServerConnectionManager";
    /**
     * The period to check if a connection path to the server exists and needs to be chosen
     * (master mode only).
     */
    private static final long CONNECTION_SELECT_INTERVAL = 1500;
    /**
     * Milliseconds to await termination of the connect scheduler
     */
    private static final long SHUTDOWN_TERMINATION_TIMEOUT = 2500;

    /**
     * The main blaubot channel manager
     */
    private final BlaubotChannelManager channelManager;

    /**
     * our own device
     */
    private final IBlaubotDevice ownDevice;

    /**
     * The MAIN blaubot connection manager (the manager used for the actual blaubot network).
     * Don't confuse this with the connectionManager used for serverConnector connections.
     */
    private final BlaubotConnectionManager mainBlaubotConnectionManager;

    /**
     * The connection manager holding the connections established from serverConnectors.
     * Don't confuse this with the mainBlaubotConnectionManager.
     * The connections stored here are simple connections and have to be upgraded to KingdomConnections
     * before usage.
     */
    private final BlaubotConnectionManager connectionManager;

    /**
     * The serverConnector injected via the getter. May be null.
     */
    private BlaubotServerConnector serverConnector;


    /**
     * Indicates whether this manager operates in master mode or not
     */
    private volatile boolean isMaster = false;

    /**
     * The facade connection added to the main channel manager if operating as master. May be null.
     */
    private volatile IBlaubotConnection facadeConnection;

    /**
     * The current king device set by the LifecycleListener.
     * May be null.
     */
    private IBlaubotDevice currentKingDevice;

    /**
     * Current connection to the server, if in Master mode. May be null.
     */
    private BlaubotKingdomConnection currentServerConnection;
    /**
     * synchronizes the creation and shutdowns of the RelayMessageMediator
     */
    private Object serverConnectionLock = new Object();

    /**
     * Current RelayMessageMediator in client mode. May be null.
     * Takes a connection and uses the connection to speak to the server.
     * Takes admin relay messages and sends them through the server connection.
     * Receives data from the server, wraps them into RelayMessages and sends them
     * to the king as admin message.
     * On the King side, they will be made available as bytestream to the usual
     * channel manager.
     */
    private RelayMessageMediator relayMessageMediator;
    private Object relayMessageMediatorLock = new Object();


    /**
     * Schedules the connectionSelectionTask.
     * Is created and shut down on setMaster(false/true)
     */
    private volatile ScheduledExecutorService connectionSelectionExecutorService;

    /**
     * A TimerTask that checks if a connection exists and if not tries to connect to the
     * server.
     */
    private Runnable connectionSelectionTask = new Runnable() {
        @Override
        public void run() {
            try {
                if(!isMaster) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Not master, nothing to do.");
                    }
                    return;
                }

                synchronized (serverConnectionLock) {
                    final boolean connected = currentServerConnection != null;
                    if (!connected) {
                        if (Log.logDebugMessages()) {
//                            Log.d(LOG_TAG, "Currently no connection to the server available. Selecting ...");
                        }
                        List<IBlaubotConnection> allConnections = connectionManager.getAllConnections();
                        if (allConnections.isEmpty()) {
                            if (Log.logDebugMessages()) {
//                                Log.d(LOG_TAG, "No connection path to the server known ... could not select a connection.");
                            }
                            return;
                        }

                        // -- we have connections
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "There are " + allConnections.size() + " connections to the server available, selecting one out of these: " + allConnections);
                        }

                        // choose and add disconnection handling
                        /**
                         * TODO: prioritize a connection from ourselve (king), because it is a direct connection. If we don'T have a direct connection prioritize peasant relay connections over prince relay connections!
                         */
                        IBlaubotConnection chosenConnection = allConnections.get(0);

                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Chosen connection: " + chosenConnection);
                        }

                        // upgrade to KingdomConnection
                        final BlaubotKingdomConnection kingdomConnection = BlaubotKingdomConnection.createFromOutboundConnection(chosenConnection, ownDevice.getUniqueDeviceID());
                        currentServerConnection = kingdomConnection;
                        kingdomConnection.addConnectionListener(new IBlaubotConnectionListener() {
                            @Override
                            public void onConnectionClosed(IBlaubotConnection connection) {
                                synchronized (serverConnectionLock) {
                                    if (currentServerConnection == kingdomConnection) {
                                        currentServerConnection = null;
                                    }
                                }
                            }
                        });

                        // add to the main blaubot connection manager, it will bobble up from there to the channel manager
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Adding the kingdom connection to the Blaubot connection manager");
                        }
                        mainBlaubotConnectionManager.addConnection(kingdomConnection);
                    }
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
     *
     * @param channelManager the main blaubot channel manager
     * @param ownDevice our own device
     * @param mainBlaubotConnectionManager the main blaubot connection manager used by the blaubot instance itself
     */
    public ServerConnectionManager(final BlaubotChannelManager channelManager, IBlaubotDevice ownDevice, final BlaubotConnectionManager mainBlaubotConnectionManager) {
        this.ownDevice = ownDevice;
        this.mainBlaubotConnectionManager = mainBlaubotConnectionManager;
        this.connectionManager = new BlaubotConnectionManager(new ArrayList<IBlaubotConnectionAcceptor>(), new ArrayList<IBlaubotConnector>());
        this.channelManager = channelManager;
        this.channelManager.addAdminMessageListener(new IBlaubotAdminMessageListener() {
            @Override
            public void onAdminMessage(AbstractAdminMessage adminMessage) {
                if (adminMessage instanceof ServerConnectionAvailableAdminMessage) {
                    // create relay connection if master
                    if(isMaster) {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "A server connection is available through mediator device " + ((ServerConnectionAvailableAdminMessage) adminMessage).getMediatorUniqueDeviceId());
                        }
                        final String mediatorUniqueDeviceId = ((ServerConnectionAvailableAdminMessage) adminMessage).getMediatorUniqueDeviceId();
                        final String recipientUniqueDeviceId = ((ServerConnectionAvailableAdminMessage) adminMessage).getRecipientUniqueDeviceId();
                        final BlaubotServerRelayConnection conn = new BlaubotServerRelayConnection(mediatorUniqueDeviceId, recipientUniqueDeviceId);

                        // remember the connection
                        onConnectionAvailable(conn);
                    }
                } else if (adminMessage instanceof ServerConnectionDownAdminMessage) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "A server connection is NOT available anymore through mediator device " + ((ServerConnectionDownAdminMessage) adminMessage).getMediatorUniqueDeviceId());
                    }
                    // only interesting for the master
                    // the master is only interested in this information, if he is using the connection actively or if he wants to know, which devices have a connection to the server
                    // all this is handled through the usual blaubot connection manager (this.connectionManager) and the wrapping connection
                    // BlaubotServerRelayConnection, which is also listening to this admin message to trigger te onDisconnect listeners.
                    // so nothing to do here
                } else if (adminMessage instanceof RelayAdminMessage || adminMessage instanceof CloseRelayConnectionAdminMessage) {
                    if(!isMaster) {

                        /**
                         * If we are not master, we have the mediator role.
                         *  important here:
                         *  Do we already have a mediator?
                         *      if yes -> do nothing, the mediator should do the rest
                         *      if no -> Get a serverconnection from the ConnectionManager. There has to be a connection because RelayAdminMessages and CloseRelayConnectionAdminMessage
                         *               are only send after we sent ServerConnectionAvailable but of course there could be some timing problems and the connection is not there.
                         *               Then create the mediator.
                         */
                        synchronized (relayMessageMediatorLock) {
                            if(relayMessageMediator == null) {
                                if (Log.logDebugMessages()) {
                                    Log.d(LOG_TAG, "Got a RelayAdminMessage and had no relayMessageMediator. Creating one.");
                                }

                                List<IBlaubotConnection> allServerConnections = ServerConnectionManager.this.connectionManager.getAllConnections();
                                if(!allServerConnections.isEmpty()) {
                                    IBlaubotConnection connection = allServerConnections.get(0);
                                    if(connection instanceof BlaubotServerRelayConnection) {
                                        // TODO: concurreny problem here on change to master mode (got a websocket connection here)
                                        // TODO sync isMaster flag and setMaster()
                                        throw new RuntimeException(""+allServerConnections);
                                    }
                                    final RelayMessageMediator mediator = new RelayMessageMediator(connection);

                                    // maintain reference
                                    connection.addConnectionListener(new IBlaubotConnectionListener() {
                                        @Override
                                        public void onConnectionClosed(IBlaubotConnection connection) {
                                            if(relayMessageMediator == mediator) {
                                                relayMessageMediator = null;
                                            }
                                        }
                                    });

                                    relayMessageMediator = mediator;
                                    mediator.activate();
                                    // kick the current admin message in
                                    mediator.onAdminMessage(adminMessage);
                                    if (Log.logDebugMessages()) {
                                        Log.d(LOG_TAG, "Created a RelayMessageMediator for connection " + connection);
                                    }
                                } else {
                                    if (Log.logErrorMessages()) {
                                        Log.e(LOG_TAG, "No mediator to resend the received RelayAdminMessage.");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }


    /**
     * Handles everything if we get aware of a available connection
     * @param connection the newly available connection
     */
    private void onConnectionAvailable(IBlaubotConnection connection) {
        if(!isMaster) {
            // send up message to king, if we are not the king
            ServerConnectionAvailableAdminMessage availableAdminMessage = new ServerConnectionAvailableAdminMessage(ownDevice.getUniqueDeviceID(), connection.getRemoteDevice().getUniqueDeviceID());
            channelManager.publishToAllConnections(availableAdminMessage.toBlaubotMessage());
            connectionManager.addConnection(connection);
        } else {
            // if king, we store the connection in the connection manager, where it will be automatically removed, if not available anymore.
            connectionManager.addConnection(connection);
        }
    }


    /**
     * sets the server connector
     *
     * @param serverConnector the connector
     */
    public synchronized void setServerConnector(BlaubotServerConnector serverConnector) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "ServerConnector was injected: " + serverConnector);
        }
        if (this.serverConnector != null) {
            // -- there is already a serverConnector
            // shut it down first
            BlaubotServerConnector curServerConnector = this.serverConnector;
            this.serverConnector = null;
            curServerConnector.setIncomingConnectionListener(null);
            curServerConnector.deactivateServerConnector();
            final IBlaubotConnection serverConnection = curServerConnector.getServerConnection();
            if (serverConnection != null) {
                serverConnection.disconnect();
            }
        }
        this.serverConnector = serverConnector;
        this.serverConnector.setIncomingConnectionListener(new IBlaubotIncomingConnectionListener() {
            @Override
            public void onConnectionEstablished(IBlaubotConnection connection) {
                // add the listener to remove everything if the connections is disconnected
                connection.addConnectionListener(new IBlaubotConnectionListener() {
                    @Override
                    public void onConnectionClosed(IBlaubotConnection connection) {
                        if(!isMaster) {
                            // send down message to king, if we are not king/master to let him handle any dependencies on this connection (through RelayConnection for example)
                            ServerConnectionDownAdminMessage downAdminMessage = new ServerConnectionDownAdminMessage(ownDevice.getUniqueDeviceID());
                            channelManager.publishToAllConnections(downAdminMessage.toBlaubotMessage());
                        }
                    }
                });

                // add the connection to the connectionManager (only the server connections)
                onConnectionAvailable(connection);
            }
        });
        if (isConnected) {
            this.serverConnector.activateServerConnector();
        }
    }

    /**
     * @return the serverConnector or null, if never set by the user
     */
    public BlaubotServerConnector getServerConnector() {
        return serverConnector;
    }

    /**
     * Indicates, whether we are connected to a blaubot network or not.
     * Mainly used to activate a ServerConnector, that is attached after blaubot was already
     * started.
     */
    private volatile boolean isConnected = false;
    @Override
    public void onConnected() {
        isConnected = true;
        if (this.serverConnector != null) {
            this.serverConnector.activateServerConnector();
        }
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
        if (this.serverConnector != null) {
            this.serverConnector.deactivateServerConnector();
        }
        clear(); // disconnects the connection, if any
        currentKingDevice = null;
    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
        if(isMaster) {
            // send an on connection down for this device to myself to be sure, that it is handled properly
            ServerConnectionDownAdminMessage downAdminMessage = new ServerConnectionDownAdminMessage(blaubotDevice.getUniqueDeviceID());
            channelManager.publishToSingleDevice(downAdminMessage.toBlaubotMessage(), ownDevice.getUniqueDeviceID());
        }
    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "King device changed. Clearing state and disconnecting all connections.");
        }
        clear();
        this.currentKingDevice = newKing;
    }


    /**
     * clears the state and disconnects all server connections
     */
    private void clear() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Clearing state: Close connections");
        }
        for(IBlaubotConnection conn : this.connectionManager.getAllConnections()) {
            conn.disconnect();
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Clearing state: RelayMessageMediator");
        }
        synchronized (relayMessageMediatorLock) {
            if(this.relayMessageMediator != null) {
                relayMessageMediator.serverConnection.disconnect();
                this.relayMessageMediator.deactivate();
                this.relayMessageMediator = null;
            }
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Clearing state: ServerConnection");
        }
        synchronized (serverConnectionLock) {
            if(this.currentServerConnection != null) {
                this.currentServerConnection.disconnect();
                this.currentServerConnection = null;
            }
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "State cleared.");
        }
    }

    /**
     * Sets whether this manager operates in master mode or not
     * @param isMaster true iff operating as master
     */
    public void setMaster(final boolean isMaster) {
        // TODO possible concurrency problems here. Maybe use a single threaded queue to synchronize this things
        final boolean changed = isMaster != this.isMaster;
        if(changed) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Changed operating mode to " + (isMaster ? "master":"client"));
            }
            clear();
            if(isMaster) {
                // if there was a mediator from a former peasant/prince state, shut it down
                synchronized (relayMessageMediatorLock) {
                    if(relayMessageMediator != null) {
                        relayMessageMediator.serverConnection.disconnect();
                        relayMessageMediator.deactivate();
                        relayMessageMediator = null;
                    }
                }
                // activate scheduler
                if (this.connectionSelectionExecutorService != null) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Executer already started ...");
                    }
                    return; // already started
                }
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Starting up the server connection selection task scheduler.");
                }
                this.connectionSelectionExecutorService = Executors.newSingleThreadScheduledExecutor();
                this.connectionSelectionExecutorService.scheduleAtFixedRate(connectionSelectionTask, 0, CONNECTION_SELECT_INTERVAL, TimeUnit.MILLISECONDS);
            } else {
                // deactivate scheduler
                if (this.connectionSelectionExecutorService != null) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Shutting down the executor service ...");
                    }
                    this.connectionSelectionExecutorService.shutdownNow();
                    try {
                        final boolean timedOut = !this.connectionSelectionExecutorService.awaitTermination(SHUTDOWN_TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS);
                        if (timedOut && Log.logErrorMessages()) {
                            Log.e(LOG_TAG, "ExecutorService termination timeout");
                        }
                    } catch (InterruptedException e) {
                                            
                    } finally {
                        this.connectionSelectionExecutorService = null;
                    }
                }

                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Disconnecting all server connections.");
                }
                // disconnect all connections
                for(IBlaubotConnection conn : this.connectionManager.getAllConnections()) {
                    conn.disconnect();
                }
            }
        }
        this.isMaster = isMaster;
    }


    /**
     * @return the currently USED server connection by the channel manager or null, if no connection exists
     */
    public BlaubotKingdomConnection getCurrentlyUsedServerConnection() {
        synchronized (serverConnectionLock) {
            return currentServerConnection;
        }
    }

    /**
     *
     * @return list of blaubotconnections the server collected by the manager
     */
    public List<IBlaubotConnection> getConnectionsToServer() {
        return new ArrayList<>(connectionManager.getAllConnections());
    }

    /**
     * The mediator that actually sends the relay messages if not in master mode.
     * Is created for one kingdom connection.
     * Note that it registers itself to the channel manager in the constructor and therefore misses all
     * previously received admin messages. The initial admin message has therefore to be injected
     * via onAdminMessage().
     *
     * It manages the real connection to the server (the direct connection).
     * To do that, it uses a MessageSender and MessageReceiver pair (for chunking).
     */
    private class RelayMessageMediator implements IBlaubotAdminMessageListener {
        private static final String LOG_TAG = "RelayMessageMediator";
        private final IBlaubotConnection serverConnection;
        /**
         * receives messages from the server
         */
        private final BlaubotMessageReceiver messageReceiver;

        /**
         * @param serverConnection the serverConnection to be relayed
         */
        public RelayMessageMediator(final IBlaubotConnection serverConnection) {
            this.serverConnection = serverConnection;
            this.messageReceiver = new BlaubotMessageReceiver(serverConnection);
            this.messageReceiver.setForwardChunks(true); // forward chunked messages (don't inspect them)
            // handles broken connections
            serverConnection.addConnectionListener(new IBlaubotConnectionListener() {
                @Override
                public void onConnectionClosed(IBlaubotConnection connection) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Server connection closed. Deactivating messageReceiver.");
                    }
                    messageReceiver.deactivate(null);
                    channelManager.removeAdminMessageListener(RelayMessageMediator.this);
                }
            });

            // listen to messages from the server
            this.messageReceiver.addMessageListener(new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage blaubotMessage) {
//                    if (Log.logDebugMessages()) {
//                        Log.d(LOG_TAG, "Got a message from the server connection, dispatching it via RelayMessage");
//                    }
                    // got a message from the server, relay to the king
                    RelayAdminMessage relayAdminMessage = new RelayAdminMessage(blaubotMessage.toBytes());
                    channelManager.publishToAllConnections(relayAdminMessage.toBlaubotMessage());
                }
            });

            // listens to messages from the king
            channelManager.addAdminMessageListener(this);
        }


        public void activate() {
            messageReceiver.activate();
        }

        public void deactivate() {
            messageReceiver.deactivate(null);
        }

        @Override
        public void onAdminMessage(AbstractAdminMessage adminMessage) {
            if (adminMessage instanceof RelayAdminMessage) {
                if(Log.logDebugMessages()) {
                    BlaubotMessage unwrappedMessage = ((RelayAdminMessage) adminMessage).getAsBlaubotMessage();
                    //if (!unwrappedMessage.getMessageType().isKeepAliveMessage()) {
                    //    Log.d(LOG_TAG, "Dispatching a relay message to the server connection: " + unwrappedMessage);
                    //}
                }

                byte[] messageBytes = ((RelayAdminMessage) adminMessage).getMessageBytes();
                try {
                    serverConnection.write(messageBytes);
                } catch (IOException e) {
                    // handled by the connection manager
                }
            } else if(adminMessage instanceof CloseRelayConnectionAdminMessage) {
                String mediatorUniqueDeviceId = ((CloseRelayConnectionAdminMessage) adminMessage).getMediatorUniqueDeviceId();
                if (mediatorUniqueDeviceId.equals(ownDevice.getUniqueDeviceID())) {
                    if(Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Got instructions to close the relay connection. Disconnecting direct connection to the server ...");
                    }
                    serverConnection.disconnect();
                    // will notify listeners and finally send an down admin message to the king ...
                }
            }
        }
    }



    /**
     * A relay connection is a connection that uses admin messages to transport bytes from
     * A to C using an intermediate participant B (mediator).
     * There are connections A to B and B to C and the relay connection uses an established
     * network to make the transitive connection between A and C (recipient).
     *
     * The BlaubotServerRelayConnection is only created on A (the king).
     * It needs the RelayMessageMediator instance running on B.
     * B needs to have a IBlaubotConnection to C.
     *
     * Note that it only seems like the connection is capable of sending arbitrary bytes through
     * it but since we are using MessageReceivers internally it is only capable of sending and
     * receiving full blaubot message byte packages!
     */
    public class BlaubotServerRelayConnection extends BlaubotConnectionQueueMock implements IBlaubotAdminMessageListener {
        private UUID uuid = UUID.randomUUID();
        private static final String LOG_TAG = "BlaubotServerRelayConnection";
        /**
         * The unique device id of the device over which the relay messages are relayed
         */
        private final String mediatorUniqueDeviceId;
        /**
         * The final destination where data is sent to on write(*).. calls.
         */
        private final String recipientUniqueDeviceId;
        /**
         * Everything thaht is written to this connection via it's write(*) methods will be received
         * by this receiver locally and then be wrapped into a RelayMessage to be send as admin
         * message via the ChannelManager.
         */
        private final BlaubotMessageReceiver messageReceiver;


        /**
         * Note: starts a thread!
         * @param mediatorUniqueDeviceId the uniqueDeviceId to which the data should be send (via relay message) when using the write(*) methods.
         * @param recipientUniqueDeviceId the recipient's unique device id (Server/King)
         */
        public BlaubotServerRelayConnection(final String mediatorUniqueDeviceId, String recipientUniqueDeviceId) {
            super(new BlaubotDevice(recipientUniqueDeviceId));
            /*
             * This connection can read the things written via write() to this connection instance.
             */
            final BlaubotConnectionQueueMock dummyConnection = getOtherEndpointConnection(new BlaubotDevice("Internal BlaubotServerRelayConnection DummyDevice "));
            this.messageReceiver = new BlaubotMessageReceiver(dummyConnection);
            this.messageReceiver.setForwardChunks(true); // we want to just forward junks (not collect them as whole to be chunked again later)
            this.messageReceiver.activate();
            this.messageReceiver.addMessageListener(new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage blaubotMessage) {
                    // wrap the message into a relay admin message
                    final RelayAdminMessage relayAdminMessage = new RelayAdminMessage(blaubotMessage.toBytes());
                    // create a blaubot mesasge from the relay admin message (= blaubot message containing which is a relay admin message, which contains a blaubotmessage ;-))
                    final BlaubotMessage msg = relayAdminMessage.toBlaubotMessage();
//                    if (!blaubotMessage.getMessageType().isKeepAliveMessage()) {
//                        Log.d(LOG_TAG, "Dispatching ... Sending RelayAdminMessage to mediator: " + msg);
//                    }
                    // send it to the mediator
                    channelManager.publishToSingleDevice(msg, mediatorUniqueDeviceId);
                }
            });
            this.recipientUniqueDeviceId = recipientUniqueDeviceId;
            this.mediatorUniqueDeviceId = mediatorUniqueDeviceId;

            // listener handling (cleanup and wiring)
            channelManager.addAdminMessageListener(this);
            this.addConnectionListener(new IBlaubotConnectionListener() {
                @Override
                public void onConnectionClosed(IBlaubotConnection connection) {
                    channelManager.removeAdminMessageListener(BlaubotServerRelayConnection.this);
                }
            });

            addConnectionListener(new IBlaubotConnectionListener() {
                @Override
                public void onConnectionClosed(IBlaubotConnection connection) {
                    messageReceiver.deactivate(null);
                }
            });


        }

        @Override
        public void onAdminMessage(AbstractAdminMessage adminMessage) {
            if (adminMessage instanceof ServerConnectionDownAdminMessage) {
                // this block only happens on the master
                if(((ServerConnectionDownAdminMessage) adminMessage).getMediatorUniqueDeviceId().equals(mediatorUniqueDeviceId)) {
                    if(Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "A relay connection is down (" + mediatorUniqueDeviceId + ") ");
                    }
                    // if the mediator has no active connection to the server anymore, we disconnect the connection.
                    // note that this message will also be received if an onDeviceLeft() for this device occurs.
                    _disconnect(); // triggers its own listeners
                }
            } else if (adminMessage instanceof RelayAdminMessage) {
//                if(Log.logDebugMessages()) {
//                    Log.d(LOG_TAG, "Dispatching relay admin message of " + ((RelayAdminMessage) adminMessage).getMessageBytes().length + " bytes to input stream of the relay connection");
//                }
                // put data to the queue. This bytes can then be read via the read(*) methods.
                byte[] messageBytes = ((RelayAdminMessage) adminMessage).getMessageBytes();
                writeMockDataToInputStream(messageBytes);
            }
        }

        private volatile boolean notifiedDisconnect = false;
        @Override
        protected void notifyDisconnected() {
            if (notifiedDisconnect) {
                return;
            }
            super.notifyDisconnected();
            notifiedDisconnect = true;
        }

        @Override
        public void disconnect() {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "disconnect() called, sending CloseRelayConnectionAdminMessage");
            }
            // send disconnect instruction to mediator
            CloseRelayConnectionAdminMessage adminMessage = new CloseRelayConnectionAdminMessage(mediatorUniqueDeviceId);
            final boolean sent = channelManager.publishToSingleDevice(adminMessage.toBlaubotMessage(), mediatorUniqueDeviceId);
            if (!sent) {
                if (Log.logWarningMessages()) {
                    Log.w(LOG_TAG, "CloseRelayConnectionAdminMessage could not be send on disconnect(). Assuming connectivity broken and notifying listener that this connection is disconnected.");
                }
                // if we are not able to send this instruction, disconnect locally
                _disconnect();
            }

            // the actual disconnect will be announced by an admin message (either on device left or a direct down message)
            // and then call _disconnect();
        }

        private void _disconnect() {
            super.disconnect();
        }



        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            BlaubotServerRelayConnection that = (BlaubotServerRelayConnection) o;

            if (uuid != null ? !uuid.equals(that.uuid) : that.uuid != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("BlaubotServerRelayConnection{");
            sb.append("mediatorUniqueDeviceId='").append(mediatorUniqueDeviceId).append('\'');
            sb.append(", recipientUniqueDeviceId='").append(recipientUniqueDeviceId).append('\'');
            sb.append('}');
            return sb.toString();
        }

        public String getMediatorUniqueDeviceId() {
            return mediatorUniqueDeviceId;
        }

        public String getRecipientUniqueDeviceId() {
            return recipientUniqueDeviceId;
        }
    }
}
