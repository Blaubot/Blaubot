package de.hsrm.blaubot.core.connector;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;

/**
 * Connects to other {@link IBlaubotDevice}s (to their {@link IBlaubotConnectionAcceptor}) and
 * informs attached {@link IBlaubotIncomingConnectionListener}s of successfully established
 * connections.
 * 
 * @TODO: add a method getProtocol which should return ble, tcp, nfc, ... to determine for which this connector is applicable (maybe add this to the acceptors as well?) 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public interface IBlaubotConnector {
	/**
	 * Callback that will be triggered when this connector successfully established
	 * a connection to a remote device.
	 *  
	 * @param acceptorConnectorListener the listener instance
	 */
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener acceptorConnectorListener);

	/**
	 * Try to connect to the given {@link IBlaubotDevice}.
	 * 
	 * @param blaubotDevice
	 * @return connection object, if the connection could be established - null otherwise
	 * @throws IncompatibleBlaubotDeviceException
	 *             if the given device can not be handled by this connector
	 *             (e.g. if a bluetooth device is handed to a WiFi connector)
	 */
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) throws IncompatibleBlaubotDeviceException;
	
	
	/**
	 * Creates a {@link IBlaubotDevice} instance by the uniqueId if possible.
	 * The unique id will be the mac address or a similar unique identification 
	 * feature of this device. This method returns a ready to use {@link IBlaubotDevice}
	 * instance if the device IS KNOWN (bonded) or the {@link IBlaubotConnector} is able
	 * to create devices from uniqueIds. If not the result will be null.
	 * 
	 * @param uniqueId the device's unique id
	 * @return the {@link IBlaubotDevice} or null, if the device is not known (bonded) or suitable for the connector's concrete network interface
	 */
	public IBlaubotDevice createRemoteDevice(String uniqueId);
	
}
