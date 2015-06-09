package eu.hgross.blaubot.core;

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.ethernet.BlaubotBonjourBeacon;
import eu.hgross.blaubot.ethernet.BlaubotEthernetAdapter;
import eu.hgross.blaubot.ethernet.BlaubotEthernetFixedDeviceSetBeacon;
import eu.hgross.blaubot.ethernet.BlaubotEthernetMulticastBeacon;
import eu.hgross.blaubot.ethernet.FixedDeviceSetHelper;
import eu.hgross.blaubot.util.Log;

/**
 *
 * A factory that creates Blaubot, adapter, beacon, BlaubotServer and BlaubotServerConnector instances.
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotFactory {
	private static final String LOG_TAG = "BlaubotFactory";

	abstract class CreateRunnable implements Runnable {
		public Blaubot mBlaubot;
	}

	/**
	 * Creates a ethernet blaubot instance with the default ports 17171, 17172, 17173
	 * and does some dirty stuff to get around android's no network on main thread
	 * policy.
	 *
	 * @param appUUID the app's uuid
	 * @return the blaubot instance
	 */
	public static Blaubot createEthernetBlaubot(final UUID appUUID) {
		final CountDownLatch cdl = new CountDownLatch(1);
		CreateRunnable cr = new BlaubotFactory().new CreateRunnable() {
			@Override
			public void run() {
				InetAddress inetAddr= getLocalIpAddress();
				if(inetAddr == null)
					throw new RuntimeException("Failed to get local ip address.");
				mBlaubot = createEthernetBlaubot(appUUID, 17171, 17172, 17173, inetAddr);
				cdl.countDown();
			}
		};
		new Thread(cr).start();
		try {
			cdl.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return cr.mBlaubot;
	}

    /**
     * Creates a blaubot instance using the bonjour beacon.
     * Note: requires broadcast capable network
     *
     * @param appUUID the app's uuid
     * @param acceptorPort the port of the connector's accepting socket
     * @param  beaconPort the port for the beacon to accept connections on
     * @param ownInetAddress the own {@link InetAddress} of the network to act on
     * @return
     */
    public static Blaubot createEthernetBlaubotWithBonjourBeacon (UUID appUUID, int acceptorPort, int beaconPort, InetAddress ownInetAddress) {
		if (ownInetAddress == null || appUUID == null) {
			throw new NullPointerException();
		}
		IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
		BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
		BlaubotEthernetAdapter ethernetAdapter = new BlaubotEthernetAdapter(ownDevice, uuidSet, acceptorPort, ownInetAddress);
		BlaubotBonjourBeacon blaubotBonjourBeacon = new BlaubotBonjourBeacon(ownInetAddress, beaconPort);
		return createBlaubot(appUUID, ownDevice, ethernetAdapter, blaubotBonjourBeacon);
	}

	/**
	 * Creates a blaubot instance using the multicast ethernet adapter.
	 * Note: requires broadcast capable network
	 *
	 * @param appUUID the app's uuid
	 * @param acceptorPort the port of the connector's accepting socket
	 * @param beaconPort the port of the beacon's accepting socket
	 * @param beaconBroadcastPort the broadcast port. Has to be the same for all instances.
	 * @param ownInetAddress the own {@link InetAddress} of the network to act on
	 * @return
	 */
	public static Blaubot createEthernetBlaubot (UUID appUUID, int acceptorPort, int beaconPort, int beaconBroadcastPort, InetAddress ownInetAddress) {
        if (ownInetAddress == null || appUUID == null) {
            throw new NullPointerException();
        }
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        BlaubotEthernetAdapter ethernetAdapter = new BlaubotEthernetAdapter(ownDevice, uuidSet, acceptorPort, ownInetAddress);
        BlaubotEthernetMulticastBeacon multicastBeacon = new BlaubotEthernetMulticastBeacon(beaconPort, beaconBroadcastPort);
        return createBlaubot(appUUID, ownDevice, ethernetAdapter, multicastBeacon);
    }

	/**
	 * Creates a blaubot instance using the ethernet adapter.
	 * This instance will only operate on the given fixedDevicesSet and does not do any discovery.
	 *
	 * @param appUUID the app's uuid
     * @param ownDevice the own device unique id. Note that it's uniqueId has to be present in the fixed device set
	 * @param acceptorPort the port of the connector's accepting socket
	 * @param beaconPort the port of the beacon's accepting socket
	 * @param ownInetAddress the own {@link InetAddress} of the network to act on
	 * @param fixedDevicesSet a set of fixed devices to connect to (represented as uniqueIdStrings)
	 * @return
	 */
	public static Blaubot createEthernetBlaubotWithFixedDevicesBeacon(UUID appUUID, IBlaubotDevice ownDevice, int acceptorPort, int beaconPort, InetAddress ownInetAddress, Set<String> fixedDevicesSet) throws UnknownHostException {
		if(ownInetAddress == null || appUUID == null)
			throw new NullPointerException();
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        BlaubotEthernetAdapter ethernetAdapter = new BlaubotEthernetAdapter(ownDevice, uuidSet, acceptorPort, ownInetAddress);
        final Set<BlaubotEthernetFixedDeviceSetBeacon.FixedDeviceSetBlaubotDevice> fixedDeviceSetInstances = FixedDeviceSetHelper.createFixedDeviceSetInstances(fixedDevicesSet, ethernetAdapter.getConnector());
        final BlaubotEthernetFixedDeviceSetBeacon fixedDeviceSetBeacon = new BlaubotEthernetFixedDeviceSetBeacon(fixedDeviceSetInstances, beaconPort);
		return createBlaubot(appUUID, ownDevice, ethernetAdapter, fixedDeviceSetBeacon);
	}



	/**
	 * Creates a Blaubot instance from a given adapter and multiple beacons.
	 * @param appUuid the app's unique uuid
	 * @param ownDevice the own device containing this device's unique identifier
	 * @param adapter the adapter to be used (Sockets, WebSockets, Bluetooth, ...)
	 * @param beacons the becaons to be used (Bluetooth, NFC, Multicast, Bonjour, ...)
	 * @return the blaubot instance
	 */
	public static Blaubot createBlaubot(UUID appUuid, IBlaubotDevice ownDevice, IBlaubotAdapter adapter, IBlaubotBeacon... beacons) {
		BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUuid);
		List<IBlaubotAdapter> adapters = new ArrayList<>();
		adapters.add(adapter);
		Blaubot blaubot = new Blaubot(ownDevice, uuidSet, adapters, Arrays.asList(beacons));
		return blaubot;
	}

    /**
     * Creates a Blaubot instance from a given adapter and multiple beacons using a random unique device id for the ownDevice.
     * @param appUuid the app's unique uuid
     * @param adapter the adapter to be used (Sockets, WebSockets, Bluetooth, ...)
     * @param beacons the becaons to be used (Bluetooth, NFC, Multicast, Bonjour, ...)
     * @return the blaubot instance
     */
    public static Blaubot createBlaubot(UUID appUuid, IBlaubotAdapter adapter, IBlaubotBeacon... beacons) {
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUuid);
        List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
        adapters.add(adapter);
        Blaubot blaubot = new Blaubot(new BlaubotDevice(), uuidSet, adapters, Arrays.asList(beacons));
        return blaubot;
    }


	/**
	 * TODO: move to another location
	 * @return the most likely SiteLocal inetAddress or null, if everything went wrong
	 */
	public static InetAddress getLocalIpAddress() {
		try {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Searching interface to use.");
			}
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				if(intf.isLoopback() || !intf.isUp()) {
					continue;
				}
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress() && !inetAddress.isAnyLocalAddress() && inetAddress.isSiteLocalAddress()) {
						if(Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Using interface " + intf + " (IP: " + inetAddress + ")");
						}
						return inetAddress;
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(LOG_TAG, ex.toString());
		}

		return null;
	}

    /**
     * Sets up a default {@link Blaubot} instance using a Jsr82 bluetooth stack for connections and a multicast beacon (ip network) for discovery.
     * TODO: jsr82 beacon!
     *
     * Note that you have to include a Jsr82 compliant implementation into your classpath (i.e. bluecove (windows, mac); bluecove-gpl (linux); bluez)
     *
     * @param appUUID the app's unique uuid
     * @return blaubot instance
     * @throws RuntimeException if no Jsr82 implementation is available at runtime or cannot access the bluetooth stack
     * @throws ClassNotFoundException if the blaubot-jsr82 jar is not available from the classpath
     */
    public static Blaubot createJsr82BluetoothBlaubot(UUID appUUID) throws RuntimeException, ClassNotFoundException {
        IBlaubotDevice ownDevice = new BlaubotDevice();
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        IBlaubotAdapter adapter = createJsr82Adapter(uuidSet, ownDevice);
        BlaubotEthernetMulticastBeacon multicastBeacon = new BlaubotEthernetMulticastBeacon(17173, 17175);
        return createBlaubot(appUUID, ownDevice, adapter, multicastBeacon);
    }

    /**
     * @param uuidSet uuid set (created from the app uid)
     * @param ownDevice the own device
     * @throws ClassNotFoundException if the blaubot-jsr82 jar is not in the classpath
     */
    public static final IBlaubotAdapter createJsr82Adapter(BlaubotUUIDSet uuidSet, IBlaubotDevice ownDevice) throws ClassNotFoundException {
        // create adapter
//        IBlaubotAdapter jsr82BluetoothAdapter = new BlaubotJsr82BluetoothAdapter(uuidSet, ownDevice);
        final Class<?> adapterClass = Class.forName("eu.hgross.blaubot.bluetooth.BlaubotJsr82BluetoothAdapter");
        IBlaubotAdapter adapter = null;
        try {
            final Constructor<?> constructor = adapterClass.getConstructor(BlaubotUUIDSet.class, IBlaubotDevice.class);
            adapter = (IBlaubotAdapter) constructor.newInstance(uuidSet, ownDevice);
            return adapter;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

	/**
	 * Creates a blaubot server using the websocket acceptor
	 * @param ownDevice the own device
	 * @param host the interfaces to bind the websockets to.
	 * @param port the port to open the websocket server on
	 * @return the server instance
     * @throws ClassNotFoundException if the websocket jar is not in the classpath
	 */
	public static BlaubotServer createBlaubotWebsocketServer(IBlaubotDevice ownDevice, String host, int port) throws ClassNotFoundException {
		// we need an acceptor, so we create an adapter and use it's acceptor
		IBlaubotAdapter adapter = createBlaubotWebsocketAdapter(ownDevice, host, port);
		// create and start the Blaubot server
		BlaubotServer server = new BlaubotServer(ownDevice, adapter.getConnectionAcceptor());
		return server;
	}


    /**
     * Create a websocket adapter via reflection
     * @param ownDevice the own device (and uniqueDeviceId)
     * @param host the own host address (interfaces to bind to)
     * @param port the port for the acceptor to accept connections on
     * @return adapter instance
     * @throws ClassNotFoundException if the websocket jar is not in the classpath
     */
    public static final IBlaubotAdapter createBlaubotWebsocketAdapter(IBlaubotDevice ownDevice, String host, int port) throws ClassNotFoundException {
//        IBlaubotAdapter adapter = new BlaubotWebsocketAdapter(ownDevice, host, port);
        IBlaubotAdapter adapter = null;
        final Class<?> adapterClass = Class.forName("eu.hgross.blaubot.websocket.BlaubotWebsocketAdapter");
        try {
            final Constructor<?> constructor = adapterClass.getConstructor(IBlaubotDevice.class, String.class, Integer.TYPE);
            adapter = (IBlaubotAdapter) constructor.newInstance(ownDevice, host, port);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return adapter;
    }

    /**
     * @param host the hostname to connect to
     * @param path the path to connect the websocket with (with leading slash)
     * @param port the port
     * @throws ClassNotFoundException if the websocket jar is not in the classpath
     */
    public static final ConnectionMetaDataDTO createWebSocketMetaDataDTO(String host, String path, int port) throws ClassNotFoundException {
        ConnectionMetaDataDTO data = null;
        final Class<?> adapterClass = Class.forName("eu.hgross.blaubot.websocket.WebsocketConnectionMetaDataDTO");
        try {
            final Constructor<?> constructor = adapterClass.getConstructor(String.class, String.class, Integer.TYPE);
            data = (ConnectionMetaDataDTO) constructor.newInstance(host, path, port);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return data;
    }


	/**
	 * Creates a blaubot server using websockets, the default port 8080 on all interfaces (0.0.0.0).
	 *
	 * @param ownDevice the own device
	 * @return the blaubot server
     * @throws ClassNotFoundException if the websocket jar is not in the classpath
	 */
	public static BlaubotServer createBlaubotWebsocketServer(IBlaubotDevice ownDevice) throws ClassNotFoundException {
		return createBlaubotWebsocketServer(ownDevice, "0.0.0.0", 8080);
	}

	/**
	 * Creates a blaubot server using websockets on all interfaces (0.0.0.0)
	 * using the given httpPort.
	 *
	 * @param ownDevice the own device
	 * @param httpPort the httpPort for the http server to server requests
	 * @return the blaubot server
     * @throws ClassNotFoundException if the websocket jar is not in the classpath
	 */
	public static BlaubotServer createBlaubotWebsocketServer(IBlaubotDevice ownDevice, int httpPort) throws ClassNotFoundException {
		return createBlaubotWebsocketServer(ownDevice, "0.0.0.0", httpPort);
	}

	/**
	 * Creates a WebSocket ServerConnector.
	 *
	 * @param host the server's hostname or IPv4-Address
	 * @param port the server's port
	 * @param websocketPath the uri path used by the websocket acceptor
	 * @param ownDevice the OWN device of the connecting blaubot instance
	 * @param serverDeviceUniqueDeviceId the server's unique device id.
	 * @return the server connector ready to be attached to the blaubot instance
     * @throws ClassNotFoundException if the websocket jar is not in the classpath
	 */
	public static BlaubotServerConnector createWebSocketServerConnector(String host, int port, String websocketPath, IBlaubotDevice ownDevice, String serverDeviceUniqueDeviceId) throws ClassNotFoundException {
		ConnectionMetaDataDTO connectionMetaData = createWebSocketMetaDataDTO(host, websocketPath, port);

		// supply connectors by creating adapters
        final IBlaubotAdapter websocketAdapter = createBlaubotWebsocketAdapter(ownDevice, "0.0.0.0", port);
		ArrayList<IBlaubotConnector> connectors = new ArrayList<>();
		connectors.add(websocketAdapter.getConnector());

		// provide a beacon store with the server's connect meta data inside
		BlaubotBeaconStore beaconStore = new BlaubotBeaconStore();
		List<ConnectionMetaDataDTO> connectionMetaDataList = new ArrayList<>();
		connectionMetaDataList.add(connectionMetaData);
		beaconStore.putConnectionMetaData(serverDeviceUniqueDeviceId, connectionMetaDataList);

		// create the server connector and attach it to blaubot
		BlaubotServerConnector bsc = new BlaubotServerConnector(serverDeviceUniqueDeviceId, beaconStore, connectors);
		return bsc;
	}



}
