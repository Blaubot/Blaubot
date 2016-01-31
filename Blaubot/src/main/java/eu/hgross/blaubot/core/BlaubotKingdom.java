package eu.hgross.blaubot.core;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.util.KingdomCensusLifecycleListener;

/**
 * The root object for all BlaubotKingdoms connected to the BlaubotServer.
 * It holds the ChannelManager and all connections to this kingdom in a ConnectionManager.
 */
public class BlaubotKingdom {
    private static final String LOG_TAG = "BlaubotKingdom";
    /**
     * The king device. It is used to address this kindom by the king's uniqueDeviceId.
     */
    private final IBlaubotDevice kingDevice;
    /**
     * Dispatches the onDeviceLeft/onJoin events to lifecycle listeners.
     */
    private final LifeCycleEventDispatcher lifeCycleEventDispatcher;

    /**
     * our own device
     */
    private final IBlaubotDevice ownDevice;

    /**
     * The always client mode ChannelManager responsible for the kingdom
     */
    private BlaubotChannelManager channelManager;

    /**
     * The managed connection.
     * May be null, if manageConnection was never called.
     */
    private IBlaubotConnection managedConnection;

    /**
     * Listens to lifecycle events to have the kingdom's state at hand.
     */
    private KingdomCensusLifecycleListener kingdomCensusLifecycleListener;


    /**
     * @param ownDevice  the own device
     * @param kingDevice the device object for the kingdom's king device
     */
    public BlaubotKingdom(IBlaubotDevice ownDevice, IBlaubotDevice kingDevice) {
        this.ownDevice = ownDevice;
        this.kingDevice = kingDevice;

        // create components
        this.channelManager = new BlaubotChannelManager(ownDevice.getUniqueDeviceID());
        this.lifeCycleEventDispatcher = new LifeCycleEventDispatcher(ownDevice);
        this.kingdomCensusLifecycleListener = new KingdomCensusLifecycleListener(ownDevice);

        // wire components
        this.channelManager.addAdminMessageListener(lifeCycleEventDispatcher);
//        // debug listener
//        this.channelManager.addAdminMessageListener(new IBlaubotAdminMessageListener() {
//            @Override
//            public void onAdminMessage(AbstractAdminMessage adminMessage) {
//                System.out.println("Kingdom of " + BlaubotKingdom.this.kingDevice.getUniqueDeviceID() + " Got admin message: " + adminMessage);
//            }
//        });
        this.lifeCycleEventDispatcher.addLifecycleListener(this.kingdomCensusLifecycleListener);
    }

    /**
     * Choses the connection to the kingDevice and starts the kingdom management.
     *
     * @throws IllegalStateException if manageConncetion was called before
     */
    protected void manageConnection(BlaubotKingdomConnection connection) {
        if (this.managedConnection != null) {
            throw new IllegalStateException("There was already a connection.");
        }
        this.channelManager.setMaster(false);
        this.channelManager.activate();
        // if the connection dies, we stop the channel manager
        connection.addConnectionListener(new IBlaubotConnectionListener() {
            @Override
            public void onConnectionClosed(IBlaubotConnection connection) {
                // onConncected/onDisconnected has to be triggered by the LifeCycleEventDispatcher on disconnect of the managed connection
                if (managedConnection != null) {
                    final String kingUniqueDeviceID = getKingDevice().getUniqueDeviceID();
                    lifeCycleEventDispatcher.notifyDisconnectedFromNetwork(kingUniqueDeviceID);
                }
                disconnectKingdom();
            }
        });
        this.managedConnection = connection;
        this.channelManager.addConnection(connection);
        this.lifeCycleEventDispatcher.notifyConnectedToNetwork();
    }

    /**
     * Adds a listener that gets invoked, if this kingdom disconnected
     *
     * @param disconnectListener the listener
     */
    public void addDisconnectListener(IBlaubotConnectionListener disconnectListener) {
        if (this.managedConnection != null) {
            this.managedConnection.addConnectionListener(disconnectListener);
        }
    }

    /**
     * Closes all connections and releases resources regarding this kingdom.
     */
    public void disconnectKingdom() {
        if (this.managedConnection != null) {
            this.managedConnection.disconnect();
        }
        this.channelManager.reset();
        this.channelManager.deactivate();
    }

    /**
     * Adds a listener to this kingdom's life cycle events
     *
     * @param lifecycleListener
     */
    public void addLifecycleListener(ILifecycleListener lifecycleListener) {
        lifeCycleEventDispatcher.addLifecycleListener(lifecycleListener);
    }

    /**
     * Removes a listener to this kingdom's life cycle events
     *
     * @param lifecycleListener
     */
    public void removeLifecycleListener(ILifecycleListener lifecycleListener) {
        lifeCycleEventDispatcher.removeLifecycleListener(lifecycleListener);
    }

    /**
     * The channel manage to manage channels.
     *
     * @return channel manager
     */
    public BlaubotChannelManager getChannelManager() {
        return channelManager;
    }

    /**
     * Returns the king device, which is the discriminator for a kingdom.
     *
     * @return king device
     */
    public IBlaubotDevice getKingDevice() {
        return kingDevice;
    }

    /**
     * our own device
     *
     * @return device
     */
    public IBlaubotDevice getOwnDevice() {
        return ownDevice;
    }

    /**
     * A KingdomCensusLifecycleListener keeping track of the connected devices.
     * You can poll the devices, current prince, ... from here.
     * @return the census listener
     */
    public KingdomCensusLifecycleListener getKingdomCensusLifecycleListener() {
        return kingdomCensusLifecycleListener;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlaubotKingdom that = (BlaubotKingdom) o;

        if (!kingDevice.equals(that.kingDevice)) return false;
        if (managedConnection != null ? !managedConnection.equals(that.managedConnection) : that.managedConnection != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = kingDevice.hashCode();
        result = 31 * result + (managedConnection != null ? managedConnection.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BlaubotKingdom{");
        sb.append("kingDevice=").append(kingDevice);
        sb.append(", managedConnection=").append(managedConnection);
        sb.append('}');
        return sb.toString();
    }
}
