package eu.hgross.blaubot.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.ConnectionStateMachine;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import eu.hgross.blaubot.core.statemachine.states.KingState;
import eu.hgross.blaubot.core.statemachine.states.PeasantState;
import eu.hgross.blaubot.core.statemachine.states.PeasantState.ConnectionAccomplishmentType;
import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.util.Log;

/**
 * The top level Blaubot object. Consists of the
 * {@link BlaubotConnectionManager}, {@link ConnectionStateMachine},
 * {@link eu.hgross.blaubot.messaging.BlaubotChannelManager} and a bunch of {@link IBlaubotAdapter}s.
 * <p/>
 * The {@link BlaubotConnectionManager} takes {@link IBlaubotConnection}s that
 * are established by the {@link IBlaubotAdapter} implementations which receive
 * incoming connections ({@link IBlaubotConnectionAcceptor}) and create
 * connections ({@link IBlaubotConnector}).
 * <p/>
 * The {@link ConnectionStateMachine} manages the {@link IBlaubotState}s by
 * receiving {@link AbstractBlaubotStateMachineEvent}s created from incoming
 * admin {@link eu.hgross.blaubot.messaging.BlaubotMessage}s (from {@link IBlaubotConnection}s),
 * {@link AbstractBlaubotDeviceDiscoveryEvent} (from
 * {@link eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon}s) as well as new or closed connections (via
 * {@link IBlaubotConnectionManagerListener}s and their events).
 * <p/>
 * Based on the {@link ConnectionStateMachine}s state changes, the
 * {@link eu.hgross.blaubot.messaging.BlaubotChannelManager} will be set up to act as a Master or a Slave, reset
 * it's context or stop/start it's services.
 * <p/>
 * <h1>USAGE:</h1> To create {@link IBlaubotChannel}s for communication a user can use
 * the {@link Blaubot#createChannel(short)} method. Developers
 * should attach {@link ILifecycleListener}s to the {@link Blaubot} instance to
 * get high level information events about the currently formed {@link Blaubot}
 * network (Joins/Leaves of devices, established or broken networks, ...)
 * <p/>
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 */
public class Blaubot implements Closeable {
    private static final String LOG_TAG = "Blaubot";

    private final ConcurrentHashMap<IBlaubotConnection, KeepAliveSender> keepAliveSenders;
    private final BlaubotConnectionManager connectionManager;
    private final ConnectionStateMachine connectionStateMachine;
    private final BlaubotChannelManager channelManager;
    private final List<IBlaubotAdapter> adapters;
    private final IBlaubotDevice ownDevice;
    private final BlaubotUUIDSet uuidSet;
    private BlaubotServerConnector serverConnector;
    private ServerConnectionManager serverConnectionManager;

    /**
     * Receives events from the connection state machine and the BlaubotConnectionManager to generate
     * the user api lifecycle events.
     * Has to be attached to the connection state machine and the the channel manager.
     */
    private final LifeCycleEventDispatcher lifeCycleEventDispatchingListener;

    /**
     * Creates a blaubot instance upon the given adapters and beacons.
     *
     * @param ownDevice the own device with the unique device id for this instance
     * @param uuidSet   the uuid set containing the beacon and app uuid
     * @param adapters  the adapters to be used (currently limited to 1)
     * @param beacons   the beacons to be used
     */
    public Blaubot(IBlaubotDevice ownDevice, BlaubotUUIDSet uuidSet, List<IBlaubotAdapter> adapters, List<IBlaubotBeacon> beacons) {
        if (adapters.size() != 1) {
            throw new IllegalArgumentException("No or too much adapters given. Only one adapter supported at the moment.");
        }
        this.uuidSet = uuidSet;
        final AdminMessageBeacon adminMessageBeacon = new AdminMessageBeacon(); // has to have priority on admin messages
        beacons = new ArrayList<>(beacons);
        beacons.add(adminMessageBeacon);
        this.ownDevice = ownDevice;
        this.adapters = adapters;

        // Dependency injection of blaubot
        for (IBlaubotAdapter adapter : adapters) {
            adapter.setBlaubot(this);
        }

        this.keepAliveSenders = new ConcurrentHashMap<>();

        this.connectionManager = new BlaubotConnectionManager(BlaubotAdapterHelper.getConnectionAcceptors(adapters), BlaubotAdapterHelper.getConnectors(adapters));
        this.channelManager = new BlaubotChannelManager(ownDevice.getUniqueDeviceID());
        this.channelManager.addAdminMessageListener(adminMessageBeacon);

        // create and connect the dispatcher for life cycle events
        this.lifeCycleEventDispatchingListener = new LifeCycleEventDispatcher(ownDevice);

        // the server connection management
        this.serverConnectionManager = new ServerConnectionManager(channelManager, ownDevice, connectionManager);
        this.addLifecycleListener(serverConnectionManager);
        // note: the lifecycle (setMaster(true/false)) is managed by the states of the state machine

        // state machine
        this.connectionStateMachine = new ConnectionStateMachine(ownDevice, connectionManager, adapters, beacons, this, serverConnectionManager);
        this.connectionStateMachine.addConnectionStateMachineListener(lifeCycleChannelManager);
        this.connectionManager.setBeaconStore(this.connectionStateMachine.getBeaconService().getBeaconStore());

        // connect listeners
        this.connectionManager.addConnectionListener(new ConnectionManagerListener());
        this.connectionStateMachine.addConnectionStateMachineListener(new ConnectionStateMachineListener());

        this.channelManager.addAdminMessageListener(lifeCycleEventDispatchingListener);
        this.connectionStateMachine.addConnectionStateMachineListener(lifeCycleEventDispatchingListener);


        // dependency injection of beacon store for connectors, acceptors and beacons
        IBlaubotBeaconStore beaconStore = this.connectionStateMachine.getBeaconService().getBeaconStore();
        for (IBlaubotAdapter adapter : adapters) {
            adapter.getConnector().setBeaconStore(beaconStore);
            adapter.getConnectionAcceptor().setBeaconStore(beaconStore);
        }
        for (IBlaubotBeacon beacon : beacons) {
            beacon.setBeaconStore(beaconStore);
            beacon.setBlaubot(this);
        }
    }

    /**
     * Sets the server connector to be used.
     *
     * @param serverConnector the server connector
     */
    public void setServerConnector(final BlaubotServerConnector serverConnector) {
        if (serverConnector == null) {
            throw new NullPointerException("serverConnector may not be null");
        }
//		else if(this.serverConnector != null) {
//            throw new RuntimeException("The server connector can not be changed.");
//        } else if(isStarted()) {
//            throw new RuntimeException("The server connector can not be added to a started blaubot instance. Add it before start.");
//        }
        this.serverConnector = serverConnector;
        this.serverConnectionManager.setServerConnector(serverConnector);
    }

    /**
     * The attached server connector
     *
     * @return the server connector or null, if setServerConnector was never called.
     */
    public BlaubotServerConnector getServerConnector() {
        return serverConnector;
    }

    /**
     * The device object identifying this blaubot instance.
     *
     * @return
     */
    public IBlaubotDevice getOwnDevice() {
        return ownDevice;
    }

    /**
     * Starts blaubot. If blaubot is already started, it will be RESTARTED.
     * (stop, then start).
     */
    public void startBlaubot() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Starting Blaubot, ChannelManager and ConnectionStateMachine... ");
        }
        channelManager.activate();
        connectionStateMachine.startEventDispatcher();
        connectionStateMachine.startStateMachine();
    }

    public void stopBlaubot() {
        if (Log.logDebugMessages())
            Log.d(LOG_TAG, "Stopping ConnectionStateMachine ... ");
        connectionStateMachine.stopStateMachine();
        // ChannelManager will be stopped in callback to stop of
        // connectionStateMachine.stopEventDispatcher();
    }

    public BlaubotConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public ConnectionStateMachine getConnectionStateMachine() {
        return connectionStateMachine;
    }

    public boolean isStarted() {
        return connectionStateMachine.isStateMachineStarted();
    }

    public BlaubotChannelManager getChannelManager() {
        return channelManager;
    }

    public List<IBlaubotAdapter> getAdapters() {
        return adapters;
    }

    /**
     * Creates the channel with the given id.
     *
     * @param channelId the channel's id
     * @return a channel object that is usable, when blaubot is connected.
     */
    public IBlaubotChannel createChannel(short channelId) {
        return channelManager.createOrGetChannel(channelId);
    }


    /**
     * Adds an {@link ILifecycleListener} to this {@link Blaubot} instance.
     *
     * @param lifecycleListener the listener to add
     */
    public void addLifecycleListener(ILifecycleListener lifecycleListener) {
        this.lifeCycleEventDispatchingListener.addLifecycleListener(lifecycleListener);
    }

    /**
     * Removes an {@link ILifecycleListener} from this {@link Blaubot} instance.
     *
     * @param lifecycleListener the listener to remove
     */
    public void removeLifecycleListener(ILifecycleListener lifecycleListener) {
        this.lifeCycleEventDispatchingListener.removeLifecycleListener(lifecycleListener);
    }

    @Override
    public String toString() {
        return "Blaubot [ownDevice=" + getOwnDevice() + "]";
    }

    @Override
    public void close() throws IOException {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "close() wa called. Closing all blaubot components.");
        }
        stopBlaubot();
        ArrayList<Object> components = new ArrayList<>();
        components.addAll(BlaubotAdapterHelper.getConnectionAcceptors(adapters));
        components.addAll(BlaubotAdapterHelper.getConnectors(adapters));
        components.addAll(getConnectionStateMachine().getBeaconService().getBeacons());
        for (Object component : components) {
            if (component instanceof Closeable) {
                ((Closeable) component).close();
            }
        }
    }

    /**
     * This listener handles the creation, start and stop of
     * {@link KeepAliveSender}s for all connected {@link IBlaubotDevice}s.
     *
     * @author Henning Gross <mail.to@henning-gross.de>
     */
    class ConnectionManagerListener implements IBlaubotConnectionManagerListener {
        /**
         * Is used instead of the config, if the config is not retrievable
         */
        private static final int DEFAULT_KEEP_ALIVE_PERIOD = 500;

        @Override
        public void onConnectionEstablished(IBlaubotConnection connection) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Got onConnectionEstablished from CONNECTIONMANAGER: " + connection);
                Log.d(LOG_TAG, "Current connections: " + connectionManager.getAllConnections());
                Log.d(LOG_TAG, "Connected devices: " + connectionManager.getConnectedDevices());
            }
            startKeepAlives(connection);
        }

        /**
         * Starts sending keep alive packets periodically through the connection.
         *
         * @param connection the connection
         */
        private void startKeepAlives(IBlaubotConnection connection) {
            // send keep alive to all connected devices (king: to all peasants,
            // peasant: to king)
            final IBlaubotDevice remoteDevice = connection.getRemoteDevice();
            final IBlaubotConnector connectorForDevice = connectionManager.getConnectorForDevice(remoteDevice.getUniqueDeviceID());
            final int keepAlivePeriod;
            if (connectorForDevice == null) {
                // we never got infos from our beacon, so we use a default period
                keepAlivePeriod = DEFAULT_KEEP_ALIVE_PERIOD;
            } else {
                keepAlivePeriod = connectorForDevice.getAdapter().getBlaubotAdapterConfig().getKeepAliveInterval();
            }
            KeepAliveSender keepAliveSender = new KeepAliveSender(remoteDevice, channelManager, keepAlivePeriod);
            keepAliveSender.start();
            keepAliveSenders.put(connection, keepAliveSender);
        }

        @Override
        public void onConnectionClosed(IBlaubotConnection connection) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Got onConnectionClosed from CONNECTIONMANAGER: " + connection);
                Log.d(LOG_TAG, "Current connections: " + connectionManager.getAllConnections());
                Log.d(LOG_TAG, "Connected devices: " + connectionManager.getConnectedDevices());
            }

            // handle keep alive
            KeepAliveSender keepAliveSender = keepAliveSenders.get(connection);
            if (keepAliveSender != null) {
                keepAliveSender.stop();
                keepAliveSenders.remove(connection);
            }
        }
    }

    /**
     * Just for logging purposes.
     *
     * @author Henning Gross <mail.to@henning-gross.de>
     */
    static class ConnectionStateMachineListener implements IBlaubotConnectionStateMachineListener {
        @Override
        public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "ConnectionStateMachineListener got onStateChange(). New state: " + newState);
            }
        }

        @Override
        public void onStateMachineStopped() {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "StateMachine stopped");
            }
        }

        @Override
        public void onStateMachineStarted() {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "StateMachine started");
            }
        }
    }

    /**
     * Listens to the {@link ConnectionStateMachine} to set the
     * {@link eu.hgross.blaubot.messaging.BlaubotChannelManager}'s state.
     */
    private IBlaubotConnectionStateMachineListener lifeCycleChannelManager = new IBlaubotConnectionStateMachineListener() {
        private static final String LOG_TAG = "ChannelManagerGlue";

        @Override
        public void onStateMachineStopped() {
            // channelManager must be put into a defined state and be stopped
            // after ConnectionStateMachineStop
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Setting PM CLIENT mode, resetting and deactivating (CSM stopped)");
            }

            channelManager.setMaster(false);
            channelManager.reset();
            channelManager.deactivate();
        }

        @Override
        public void onStateMachineStarted() {
            // channelmanager must have been started before
            // ConnectionStateMachineStart (in BlaubotStart)
        }

        /**
         * Sets up the {@link eu.hgross.blaubot.messaging.BlaubotChannelManager} as master including the listener
         * wiring to inform the channel manager of new
         * {@link IBlaubotConnection}s.
         *
         * @param oldState
         * @param newState
         */
        private void onChangedToMaster(IBlaubotState oldState, IBlaubotState newState) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Setting CM to MASTER mode");
            }
            // set to master if new state instanceof KingState
            channelManager.setMaster(true);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "CM now in MASTER mode");
            }

            // We are king and await incoming connections from peasants
            // We register a listener to the KingState to inform the
            // ChannellManager about new Peasant connections
            KingState kingState = (KingState) newState;
            kingState.setPeasantConnectionsListener(new IBlaubotIncomingConnectionListener() {
                @Override
                public void onConnectionEstablished(IBlaubotConnection connection) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Got new connection as King -> ChannelManager.addConnection()");
                    }
                    channelManager.addConnection(connection);
                }
            });
        }

        @Override
        public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
            if (oldState == newState) {
                // do nothing if the state remains the same
                return;
            }

            if (oldState instanceof KingState) {
                // remove listeners from obsolete KingStates
                // should not be necessary but we like it to be on safe on this
                ((KingState) oldState).setPeasantConnectionsListener(null);
            }

            boolean isNowInKingState = newState instanceof KingState;
            // iff state class has changed, set the master state and register
            // listeners
            if (stateClassChanged(oldState, newState) && isNowInKingState) {
                onChangedToMaster(oldState, newState);
                return;
            }
            // check if the new state is a subordinate state and handle it
            onChangedToClient(oldState, newState);
        }

        /**
         * Sets up the {@link eu.hgross.blaubot.messaging.BlaubotChannelManager} as client and adds the king's
         * {@link IBlaubotConnection}.
         *
         * @param oldState
         * @param newState
         */
        private void onChangedToClient(IBlaubotState oldState, IBlaubotState newState) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Setting CM to CLIENT mode");
            }
            channelManager.setMaster(false);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "CM now in CLIENT mode");
            }

            // Iff we are subordinate, we have exactly one connection to our
            // king
            // Check and inform channel manager only, if the connection to the
            // king has changed
            if (newState instanceof IBlaubotSubordinatedState) {
                // -- we are peasant or prince
                IBlaubotConnection kingConnection = ((IBlaubotSubordinatedState) newState).getKingConnection();
                if (oldState instanceof PeasantState && newState instanceof PeasantState && newState != oldState) {
                    PeasantState ps = (PeasantState) oldState;
                    if (ps.getConnectionAccomplishmentType() == ConnectionAccomplishmentType.BOWED_DOWN) {
                        Log.d(LOG_TAG, "We bowed down to a new king with this state change -> ChannelManager.reset()");
                        channelManager.reset();
                    }
                }
                if (oldState instanceof IBlaubotSubordinatedState) {
                    IBlaubotConnection oldKingConnection = ((IBlaubotSubordinatedState) oldState).getKingConnection();
                    if (oldKingConnection == kingConnection) {
                        // -- the king connection has not changed
                        // do not inform the channel manager
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "The new state's kingConnection is the same as the old state's kingConnection. Readding to ChannelManager.");
                        }
                        return;
                    }
                }
                // -- the new state is a subordinate state and the
                // KingConnection has changed
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Adding king connection to ChannelManager.");
                }
                channelManager.addConnection(kingConnection);
            }
        }

        private boolean stateClassChanged(IBlaubotState oldState, IBlaubotState newState) {
            if (oldState == null) {
                return true;
            }
            return (!newState.getClass().equals(oldState.getClass()));
        }
    };

    /**
     * The uuid set created upon the app uuid.
     *
     * @return the uuid set
     */
    public BlaubotUUIDSet getUuidSet() {
        return uuidSet;
    }

    /**
     * The server connection managing connections to a server that are created from the ServerConnector
     * of this blaubot instance or another blaubot instance from a connected network.
     *
     * @return the manager
     */
    public ServerConnectionManager getServerConnectionManager() {
        return serverConnectionManager;
    }

    /**
     * Just for hashcode and equals
     */
    private UUID guid = UUID.randomUUID(); 

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Blaubot other = (Blaubot) obj;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        return true;
    }

}
