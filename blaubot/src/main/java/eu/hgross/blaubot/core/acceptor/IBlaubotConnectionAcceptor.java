package eu.hgross.blaubot.core.acceptor;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;

/**
 * Defines an abstraction for any kind of connection accepting mechanisms.
 * This could be a TCP,UDP, BluetoothSocket ...
 * 
 * The state, whether the acceptor is listening for incoming connections or
 * not is populated through events send to the registered {@link IBlaubotListeningStateListener}.
 *
 * A connection is successfully established, if the the connection could be accepted and the
 * client send his state but more importantly his connection meta data via a BeaconMessage through
 * the just established connection.
 *
 * The received BeaconMessage MUST then be wrapped in a AbstractBlaubotDiscoveryEvent and be
 * populated to the BeaconStore, in case the just connected client will be pronounced prince and
 * the other peasants have to know the connection meta data.
 * This is crucial because if a client connected to the king without being discovered via a beacon
 * before, the king lacks the connection info which has to be send to his peasants in case he
 * dies.
 * Note that the BeaconMessage already has helper methods to send and receive itself through a
 * IBlaubotConnection and there is not really much to be hand written on that behalf.
 *
 * If a client successfully established a connection it will then be populated through the
 * registered {@link IBlaubotIncomingConnectionListener}.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotConnectionAcceptor {
    /**
     * Dependency injecton for the BeaconStore.
     * It is guaranteed that this is set before any call to startListening or stopListening
     * @param beaconStore the beaconStore
     */
    public void setBeaconStore(IBlaubotBeaconStore beaconStore);

    /**
     * Get the adapter of which this acceptor is part of.
     * TODO: beacons really should not have this method!
     *
     * @return the adapter
     */
    public IBlaubotAdapter getAdapter();

    /**
     * Starts the connection acceptor.
     * The start method has to be idempotent.
     */
	public void startListening();

    /**
     * Stops a running acceptor.
     * The stop method has to be idempotent.
     */
	public void stopListening();

    /**
     * Retrieves the started state of this acceptor.
     * @return true iff the acceptor is started and now or soon ready to accept incoming connections
     */
	public boolean isStarted();

    /**
     * Sets a listener that receives callbacks, when the acceptor started accepting connections (is listening).
     * That is if a typically (commonly needed) internal accept thread was started or stopped.
     *
     * @param stateListener the listner to set
     */
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener);

    /**
     * Sets a listener that receives callbacks whenever a connection was accepted by the acceptor.
     *
     * @param acceptorListener the listener to be set
     */
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener);

    /**
     * Get the connection meta data needed to connect to this connector
     * This infos can range from mac addresses over ip addresses to port numbers.
     *
     * @return the connection meta data
     */
    public ConnectionMetaDataDTO getConnectionMetaData();
}
