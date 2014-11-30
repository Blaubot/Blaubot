package de.hsrm.blaubot.core.acceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.hsrm.blaubot.core.BlaubotAdapterConfig;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;
import de.hsrm.blaubot.core.connector.IncompatibleBlaubotDeviceException;
import de.hsrm.blaubot.util.Log;

/**
 * Manager to store and retrieve Connections related to {@link IBlaubotDevice} instances.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotConnectionManager {
	private static final String LOG_TAG = "BlauBotConnectionManager";
	private final ConcurrentHashMap<IBlaubotDevice, List<IBlaubotConnection>> connections = new ConcurrentHashMap<IBlaubotDevice, List<IBlaubotConnection>>();
	private final List<IBlaubotConnectionManagerListener> connectionListeners = new ArrayList<IBlaubotConnectionManagerListener>();
	private final List<IBlaubotConnectionAcceptor> connectionAcceptors;
	private final List<IBlaubotConnector> connectionConnectors;
	private final IBlaubotConnectionManagerListener connectionListener; // manager's own listener listening on
																		// acceptor/connector instances
	/**
	 * Creates a new {@link BlaubotConnectionManager} instance managing the given acceptors and connectors for incoming
	 * and outgoing connections.
	 * 
	 * @param acceptors
	 *            the acceptors to handle
	 * @param connectors
	 *            the connectors to be used to connect to other devices
	 */
	public BlaubotConnectionManager(List<IBlaubotConnectionAcceptor> acceptors, List<IBlaubotConnector> connectors) {
		this.connectionConnectors = connectors;
		this.connectionAcceptors = acceptors;
		this.connectionListener = new IBlaubotConnectionManagerListener() {

			@Override
			public void onConnectionEstablished(IBlaubotConnection connection) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got onConnectionEstablished: " + connection);
				}
				addConnection(connection);
				// proxy event to our listeners
				for (IBlaubotConnectionManagerListener listener : connectionListeners) {
					listener.onConnectionEstablished(connection);
				}
			}

			@Override
			public void onConnectionClosed(IBlaubotConnection connection) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got onConnectionClosed: " + connection);
				}
				removeConnection(connection);
				// proxy event to our listeners
				for (IBlaubotConnectionManagerListener listener : connectionListeners) {
					listener.onConnectionClosed(connection);
				}
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
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Adding connection" + connection);
		}
		List<IBlaubotConnection> deviceConnections = new CopyOnWriteArrayList<IBlaubotConnection>();
		this.connections.putIfAbsent(connection.getRemoteDevice(), deviceConnections);
		deviceConnections = this.connections.get(connection.getRemoteDevice());
		deviceConnections.add(connection);
		connection.addConnectionListener(connectionListener);
	};

	protected void removeConnection(IBlaubotConnection connection) {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Removing connection for device " + connection.getRemoteDevice());
		}
		List<IBlaubotConnection> deviceConnections = this.connections.get(connection.getRemoteDevice());
		if (deviceConnections == null) {
			if(Log.logWarningMessages()) {
				Log.w(LOG_TAG, "Tried to remove a connection for device " + connection.getRemoteDevice() + " but no connection for this device was registered.");
			}
			return;
		}
		boolean removed = deviceConnections.remove(connection);
		if (!removed) {
			if(Log.logWarningMessages()) {
				Log.w(LOG_TAG, "Tried to remove a non existant connection for device " + connection.getRemoteDevice() + " from ConnectionManager but connection was not registered.");
			}
		} else {
			// -- list was removed
			// note: we leave the empty list in the map! 
		}
		connection.removeConnectionListener(connectionListener);
	};

	/**
	 * @param blauBotDevice
	 *            the list of {@link IBlaubotDevice}s of this device.
	 * @return the connection object (a socket, bluetoothsocket ...) or null, if nothing there for blauBotDevice
	 */
	public List<IBlaubotConnection> getConnections(IBlaubotDevice blauBotDevice) {
		return this.connections.get(blauBotDevice);
	}

	/**
	 * @return a list of devices with at least one active connection to our device
	 */
	public List<IBlaubotDevice> getConnectedDevices() {
		ArrayList<IBlaubotDevice> devices = new ArrayList<IBlaubotDevice>();
		// there could be devices with no connection -> filter them out
		for(IBlaubotDevice d : connections.keySet()) {
			if(!connections.get(d).isEmpty()) {
				devices.add(d);
			}
		}
		return devices;
	}

	/**
	 * @return list of all active connections
	 */
	public List<IBlaubotConnection> getAllConnections() {
		ArrayList<IBlaubotConnection> allConnections = new ArrayList<IBlaubotConnection>();
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
	 * Tries to connect to the given {@link IBlaubotDevice}.
	 * If not successful after maxRetries, null will be returned.
	 * The retry mechanism uses the exponential backoff method which
	 * waiting time is configured by the {@link BlaubotAdapterConfig}
	 * for this device's adapter. 
	 * 
	 * @param device the {@link IBlaubotDevice} to connect to
	 * @param maxRetries max number of retries 
	 * @return an {@link IBlaubotConnection} or null, if no connection could be established after maxRetries
	 */
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice device, int maxRetries) {
		float backoffFactor = device.getAdapter().getBlaubotAdapterConfig().getExponentialBackoffFactor();
		int backoffTimeout = device.getAdapter().getBlaubotAdapterConfig().getConnectorRetryTimeout();
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Trying to connect to device " + device + " using exponential backoff and max " + maxRetries + " retries.");
		}
		int outStandingRetries = maxRetries;
		while (outStandingRetries-- > 0) {
			IBlaubotConnection conn = connectToBlaubotDevice(device);
			if(conn != null) {
				return conn;
			}
			
			// backoff
			try {
				if(outStandingRetries==0)
					break;
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Backing of - outstanding retries: " + outStandingRetries);
				}
				Thread.sleep(backoffTimeout);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			backoffTimeout *= backoffFactor;
		}
		if(Log.logWarningMessages()) {
			Log.w(LOG_TAG, "Connection to " + device + " could not be established after " + maxRetries + " retries.");
		}
		return null;
	}
	
	/**
	 * Tries to connect to the {@link IBlaubotDevice} corresponding to the given uniqueId
	 * by aksing all connectors if for a device object belonging to this uniqueId and trying
	 * to connect to this device.
	 * 
	 * If not successful after maxRetries, null will be returned.
	 * The retry mechanism uses the exponential backoff method which
	 * waiting time is configured by the {@link BlaubotAdapterConfig}
	 * for this device's adapter. 
	 * 
	 * @param device the {@link IBlaubotDevice} to connect to
	 * @param maxRetries max number of retries 
	 * @return an {@link IBlaubotConnection} or null, if no connection could be established after maxRetries
	 */
	public IBlaubotConnection connectToBlaubotDevice(String uniqueId, int maxRetries) {
		IBlaubotDevice device = getBlaubotDeviceFromUniqueId(uniqueId);
		return connectToBlaubotDevice(device, maxRetries);
	}
	
	/**
	 * Tries to find a connector able to connect to the device, then tries to connect.
	 * 
	 * @param device
	 *            the remote device to connect to
	 * @return blaubot connection, if the connection was successful - false otherwise
	 */
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice device) {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Trying to connect to device " + device + ". Searching for appropriate connector ...");
		}
		for (IBlaubotConnector connector : this.connectionConnectors) {
			try {
				IBlaubotConnection conn = connector.connectToBlaubotDevice(device);
				boolean result = conn != null;
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Found a compatible connector.");
				}
				if(Log.logDebugMessages()) {
					if (result)
						Log.d(LOG_TAG, "Connection was successful");
					else
						Log.d(LOG_TAG, "Connection failed.");
				}
				return conn;
			} catch (IncompatibleBlaubotDeviceException e) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Connector " + connector + " not compatible.");
				}
				continue;
			}
		}
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Could not find a connector to connect to remote device " + device);
		}
		return null;
	}

	/**
	 * Asks all connectors if they know this uniqueId. If successful, the connectors return a {@link IBlaubotDevice}
	 * instance ready to use for connections. Then the connection manager tries to connect to the returned device.
	 * 
	 * @param uniqueId
	 *            the remote device's uniqueId to connect to
	 * @return connection object, if the connection was successful - null otherwise
	 */
	public IBlaubotConnection connectToBlaubotDevice(String uniqueId) {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Trying to find a suitable connector able to connect the device with uniqueId " + uniqueId);
		}
		IBlaubotDevice device = getBlaubotDeviceFromUniqueId(uniqueId);
		if(device != null)
			return connectToBlaubotDevice(device);
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "The device with the uniqueId " + uniqueId + " were unknown to all connectors. Could not connect to this device.");
		}
		return null;
	}
	
	/**
	 * Goes through the known {@link IBlaubotConnector}s and tries to get an instance of 
	 * {@link IBlaubotDevice} from them based on the given uniqueId string.
	 * 
	 * @param uniqueId
	 * @return the corresponding {@link IBlaubotDevice} instance or null, if no connector could handle the uniqueId string.
	 */
	public IBlaubotDevice getBlaubotDeviceFromUniqueId(String uniqueId) {
		if(uniqueId == null) {
			throw new NullPointerException("uniqueId can't be null");
		}
		// TODO: maybe introduce a method to the connectors ala bool:isAbleToHandle(device)
		for (IBlaubotConnector connector : this.connectionConnectors) {
			IBlaubotDevice device = connector.createRemoteDevice(uniqueId);
			if (device != null) {
				if (Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Connector " + connector + " claims to be able to handle the device (" + device + "). Trying to connect.");
				}
				return device;
			}
		}
		return null;
	}
	
}
