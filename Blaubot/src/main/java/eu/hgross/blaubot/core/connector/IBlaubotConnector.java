package eu.hgross.blaubot.core.connector;

import java.util.List;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;

/**
 * Connects to other {@link IBlaubotDevice}s (to their {@link IBlaubotConnectionAcceptor}) and
 * informs attached {@link IBlaubotIncomingConnectionListener}s of successfully established
 * connections.
 * 
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public interface IBlaubotConnector {
    /**
     * Get the adapter of which this connector is part of.
     * @return the adapter
     */
    public IBlaubotAdapter getAdapter();

    /**
     * Setter method for the dependency injection of a beacon store.
     * It is guaranteed to be called before any createRemoteDevice() or
     * connectToBlaubotDevice() calls.
     * @param beaconStore the beacon store implementation
     */
    public void setBeaconStore(IBlaubotBeaconStore beaconStore);

	/**
	 * Setter method for a callback that will be triggered when this
     * connector successfully established a connection to a remote device.
	 *  
	 * @param acceptorConnectorListener the listener instance
	 */
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener acceptorConnectorListener);

	/**
	 * Try to connect to the given {@link IBlaubotDevice}.
	 * 
	 * @param blaubotDevice the device to connect to
	 * @return connection object, if the connection could be established - null otherwise
     */
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice);
	
	
    /**
     * Returns a list of supported acceptors to connect to.
     *
     * @return the list of supported acceptors
     */
    public List<String> getSupportedAcceptorTypes();

	
}
