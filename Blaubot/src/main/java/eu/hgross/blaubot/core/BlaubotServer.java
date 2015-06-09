package eu.hgross.blaubot.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.util.Log;

/**
 * The Blaubot server
 */
public class BlaubotServer {
    private static final String LOG_TAG = "BlaubotServer";
    /**
     * Max time for a kingdom to get disconnected after a call of disconnectKingdom() was made.
     */
    private static final long KINGDOM_DISCONNECT_TIMEOUT = 5000;
    private final BlaubotConnectionManager connectionManager;
    /**
     * UniqueDeviceId -> BlaubotKingdom
     * The connected Kingdoms.
     */
    private ConcurrentHashMap<String, BlaubotKingdom> kingdoms;

    /**
     * The attached acceptors over which connections from kingdoms are accepted
     */
    private List<IBlaubotConnectionAcceptor> acceptors;

    /**
     * Lock for start/stop logic
     */
    private final Object startStopMonitor = new Object();

    /**
     * Lock for incoming connections regarding kingdom creation
     */
    private final Object connectionLock = new Object();

    /**
     * Listener to get informed about connects and disconnects of kingdoms.
     */
    private CopyOnWriteArrayList<IBlaubotServerLifeCycleListener> blaubotServerLifeCycleListeners;

    /**
     * @param ownDevice the own device containing this server's uniqueDeviceId
     * @param acceptors acceptors
     */
    public BlaubotServer(final IBlaubotDevice ownDevice, IBlaubotConnectionAcceptor... acceptors) {
        this.kingdoms = new ConcurrentHashMap<>();
        this.blaubotServerLifeCycleListeners = new CopyOnWriteArrayList<>();
        this.acceptors = Arrays.asList(acceptors);
        this.connectionManager = new BlaubotConnectionManager(this.acceptors, new ArrayList<IBlaubotConnector>());
        this.connectionManager.addConnectionListener(new IBlaubotConnectionManagerListener() {

            @Override
            public void onConnectionClosed(IBlaubotConnection connection) {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "A connection was closed (" + connection.getRemoteDevice().getUniqueDeviceID() + "). ");
                }
            }

            @Override
            public void onConnectionEstablished(final IBlaubotConnection connection) {
                // create a thread waiting for the KngdomConnection to be created.
                // the thread will terminate either with a successfully created kingdom connection or because the connection was lost
                // if successfully created, the connection will be used
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final BlaubotKingdomConnection kingdomConnection = BlaubotKingdomConnection.createFromInboundConnection(connection);
                            synchronized (connectionLock) {
                                // check if a connection for this unique id exists
                                final BlaubotKingdom blaubotKingdom = kingdoms.get(connection.getRemoteDevice().getUniqueDeviceID());
                                if (blaubotKingdom != null) {
                                    if (Log.logDebugMessages()) {
                                        Log.d(LOG_TAG, "There was already a kingdom with king " + connection.getRemoteDevice().getUniqueDeviceID() + ". Disconnecting that kingdom first.");
                                    }
                                    final CountDownLatch discLatch = new CountDownLatch(1);
                                    blaubotKingdom.addDisconnectListener(new IBlaubotConnectionListener() {
                                        @Override
                                        public void onConnectionClosed(IBlaubotConnection connection) {
                                            discLatch.countDown();
                                        }
                                    });
                                    blaubotKingdom.disconnectKingdom();
                                    try {
                                        boolean timedOut = !discLatch.await(KINGDOM_DISCONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
                                        if (timedOut) {
                                            throw new RuntimeException("Kingdom did not disconnect fast enough (max " + KINGDOM_DISCONNECT_TIMEOUT + " ms)");
                                        }
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }

                                // create and start management of BlaubotKingdom
                                if (Log.logDebugMessages()) {
                                    Log.d(LOG_TAG, "Got new connection and creating a new kingdom with king " + connection.getRemoteDevice().getUniqueDeviceID() + " ...");
                                }
                                IBlaubotDevice remoteKingDevice = kingdomConnection.getRemoteDevice();
                                final String kingUniqueDeviceId = remoteKingDevice.getUniqueDeviceID();
                                final BlaubotKingdom newKingdom = new BlaubotKingdom(ownDevice, remoteKingDevice);
                                kingdomConnection.addConnectionListener(new IBlaubotConnectionListener() {
                                    @Override
                                    public void onConnectionClosed(IBlaubotConnection connection) {
                                        synchronized (connectionLock) {
                                            notifyKingdomDisconnected(newKingdom);
                                            BlaubotKingdom curKingdom = kingdoms.get(kingUniqueDeviceId);
                                            if (curKingdom == newKingdom) {
                                                kingdoms.remove(kingUniqueDeviceId, newKingdom);
                                            }
                                            if (Log.logDebugMessages()) {
                                                Log.d(LOG_TAG, "There are now " + kingdoms.size() + " kingdoms connected to this server.");
                                            }
                                        }
                                    }
                                });
                                BlaubotKingdom previous = kingdoms.put(remoteKingDevice.getUniqueDeviceID(), newKingdom);
                                if (previous != null) {
                                    // already a kingdom for this unique device id. Should have been disconnected above...
                                    throw new IllegalStateException("Inconsistant state. There was already a kingdom for this king.");
                                }

                                newKingdom.manageConnection(kingdomConnection);
                                notifyKingdomConnected(newKingdom);

                            }
                        } catch (IOException e) {
                            if (Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "A kingdom connection was not created (closed before handshake completion.");
                            }
                        }
                    }
                }).start();


            }
        });
    }

    /**
     * Starts the blaubot server.
     * Idempotent.
     */
    public void startBlaubotServer() {
        synchronized (startStopMonitor) {
            if (isStarted()) {
                return;
            }

            // start all acceptors
            BlaubotAdapterHelper.startAcceptors(acceptors);
        }
    }

    /**
     * Stops the blaubot server.
     * Idempotent.
     */
    public void stopBlaubotServer() {
        synchronized (startStopMonitor) {
            if (!_isStarted()) {
                return;
            }

            // stop all acceptors
            BlaubotAdapterHelper.stopAcceptors(acceptors);

            // disconnect all pending connections, if any
            for (IBlaubotConnection con : connectionManager.getAllConnections()) {
                con.disconnect();
            }

            // disconnect all kingdoms, if any
            for (BlaubotKingdom kingdom : kingdoms.values()) {
                kingdom.disconnectKingdom();
            }
        }
    }

    /**
     * Check the started state
     *
     * @return true iff the server is started
     */
    public boolean isStarted() {
        synchronized (startStopMonitor) {
            return _isStarted();
        }
    }

    /**
     * @return true if at least one acceptor is started
     */
    private boolean _isStarted() {
        boolean started = false;
        for (IBlaubotConnectionAcceptor acceptor : acceptors) {
            if (acceptor.isStarted()) {
                started = true;
                break;
            }
        }
        return started;
    }

    /**
     * Removes a previously attached listener from the server
     *
     * @param blaubotServerLifeCycleListener the listener to be removed
     */
    public void removeServerLifeCycleListener(IBlaubotServerLifeCycleListener blaubotServerLifeCycleListener) {
        this.blaubotServerLifeCycleListeners.remove(blaubotServerLifeCycleListener);
    }

    /**
     * Adds a listener to the server's lifecycle
     *
     * @param blaubotServerLifeCycleListener the listener to be added
     */
    public void addServerLifeCycleListener(IBlaubotServerLifeCycleListener blaubotServerLifeCycleListener) {
        this.blaubotServerLifeCycleListeners.add(blaubotServerLifeCycleListener);
    }

    /**
     * notifies all attached listeners that a kingdom connected
     *
     * @param kingdom the connected kingdom
     */
    private void notifyKingdomConnected(BlaubotKingdom kingdom) {
        for (IBlaubotServerLifeCycleListener lifeCycleListener : blaubotServerLifeCycleListeners) {
            lifeCycleListener.onKingdomConnected(kingdom);
        }
    }

    /**
     * notifies all attached listeners that a kingdom disconnected
     *
     * @param kingdom the disconnected kingdom
     */
    private void notifyKingdomDisconnected(BlaubotKingdom kingdom) {
        if (kingdom == null) {
            throw new NullPointerException();
        }
        for (IBlaubotServerLifeCycleListener lifeCycleListener : blaubotServerLifeCycleListeners) {
            lifeCycleListener.onKingdomDisconnected(kingdom);
        }
    }

    /**
     * @return the used acceptors
     */
    public List<IBlaubotConnectionAcceptor> getAcceptors() {
        return acceptors;
    }

    public static void main(String[] args) {
        /**
         * Sample usage
         */
        /*
        // Define the app's uuid and create own uniqueDeviceID
        UUID appUuid = UUID.fromString("de506eef-d894-4c18-97c3-d877ff26ca38");
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUuid);
        IBlaubotDevice ownDevice = new BlaubotDevice("Server1");

        // we need an acceptor, so we create an adapter and use it's acceptor
        BlaubotWebsocketAdapter websocketAdapter = new BlaubotWebsocketAdapter(ownDevice, "0.0.0.0", 8080);
        List<IBlaubotConnectionAcceptor> acceptors = new ArrayList<>();
        acceptors.add(websocketAdapter.getConnectionAcceptor());

        // create and start the Blaubot server
        BlaubotServer server = new BlaubotServer(ownDevice, acceptors);
        server.addServerLifeCycleListener(new IBlaubotServerLifeCycleListener() {
            @Override
            public void onKingdomConnected(final BlaubotKingdom kingdom) {
                System.out.printf("onKingdomConnected: " + kingdom);
                // register to this kingdom's life cycle events
                kingdom.addLifecycleListener(new ILifecycleListener() {
                    @Override
                    public void onConnected() {
                        System.out.println("onConnected() - " + kingdom);
                    }

                    @Override
                    public void onDisconnected() {
                        System.out.println("onDisconnected() - " + kingdom);
                    }

                    @Override
                    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
                        System.out.println("onDeviceJoined(" + blaubotDevice + ") - " + kingdom);
                    }

                    @Override
                    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
                        System.out.println("onDeviceLeft(" + blaubotDevice + ") - " + kingdom);
                    }

                    @Override
                    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
                        System.out.println("onPrinceDeviceChanged(" + oldPrince + ", " + newPrince + ") - " + kingdom);
                    }

                    @Override
                    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
                        System.out.println("onPrinceDeviceChanged(" + oldKing + ", " + newKing + ") - " + kingdom);
                    }
                });

                // register to the ping channel (from the debug view)
                final short pingChannelId = Short.MAX_VALUE;
                IBlaubotChannel channel = kingdom.getChannelManager().createOrGetChannel(pingChannelId);
                channel.subscribe(new IBlaubotMessageListener() {
                    @Override
                    public void onMessage(BlaubotMessage blaubotMessage) {
                        String message = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                        System.out.println("[king: "+kingdom.getKingDevice().getUniqueDeviceID()+"] GOT MESSAGE: " + message);
                    }
                });

                // send a ping once
                channel.publish("Ping!".getBytes(BlaubotConstants.STRING_CHARSET));
            }

            @Override
            public void onKingdomDisconnected(BlaubotKingdom kingdom) {
                System.out.println("onKingdomDisconnected: " + kingdom);
            }
        });
        server.startBlaubotServer();
        */
    }

}
