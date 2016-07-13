package eu.hgross.blaubot.android;

import android.bluetooth.BluetoothAdapter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import eu.hgross.blaubot.android.bluetooth.BlaubotBluetoothAdapter;
import eu.hgross.blaubot.android.bluetooth.BlaubotBluetoothBeacon;
import eu.hgross.blaubot.android.geo.GeoLocationBeaconAndroid;
import eu.hgross.blaubot.android.nfc.BlaubotNFCBeacon;
import eu.hgross.blaubot.android.wifi.BlaubotWifiAdapter;
import eu.hgross.blaubot.android.wifip2p.BlaubotWifiP2PBeacon;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.ethernet.BlaubotBonjourBeacon;
import eu.hgross.blaubot.ethernet.BlaubotEthernetAdapter;
import eu.hgross.blaubot.ethernet.BlaubotEthernetMulticastBeacon;
import eu.hgross.blaubot.geobeacon.GeoBeaconConstants;
import eu.hgross.blaubot.geobeacon.GeoLocationBeacon;

/**
 * Factory to create {@link Blaubot} instances for Android.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class BlaubotAndroidFactory extends eu.hgross.blaubot.core.BlaubotFactory {
    /**
     * Creates an android specific GeoLocationBeacon using WebSockets.
     * This will utilize Android's geolocation service for geo data.
     * If you want to provide the geo data yourself, see {@link BlaubotAndroidFactory#createWebSocketGeoLocationBeacon(IBlaubotDevice, String, int)}.
     * In order to use this beacon, you have to host a {@link eu.hgross.blaubot.geobeacon.GeoBeaconServer}.
     * Note that you have to put blaubot-websockets as a dependency to your project.
     *
     * @param ownDevice        the ownDevice (Blaubot-Instance device)
     * @param beaconServerHost the hostname or ip of the beacon server
     * @param beaconServerPort the port on which the beacon server listens
     * @return the geolocation beacon for android
     * @throws ClassNotFoundException if blaubot-websockets is not in the classpath
     */
    public static GeoLocationBeaconAndroid createWebSocketGeoLocationBeaconForAndroid(IBlaubotDevice ownDevice, String beaconServerHost, int beaconServerPort) throws ClassNotFoundException {
        IBlaubotConnector connector = createBlaubotWebsocketAdapter(ownDevice, beaconServerHost, beaconServerPort).getConnector(); // hacky ...
        ConnectionMetaDataDTO webSocketMetaDataDTO = createWebSocketMetaDataDTO(beaconServerHost, "/blaubot", beaconServerPort);
        BlaubotBeaconStore beaconStore = new BlaubotBeaconStore();
        beaconStore.putConnectionMetaData(GeoBeaconConstants.GEO_BEACON_SERVER_UNIQUE_DEVICE_ID, webSocketMetaDataDTO);

        GeoLocationBeaconAndroid geoLocationBeacon = new GeoLocationBeaconAndroid(beaconStore, connector);
        return geoLocationBeacon;
    }

    /**
     * Creates a blaubot instance using ethernet adapter and the NFC beacon.
     * 
     * Note that you have to call some lifecycle methods onResume and onPause.
     * See BlaubotAndroid.
     *
     * @param appUUID        the app's uuid
     * @param acceptorPort   the port of the connector's accepting socket
     * @param ownInetAddress the own {@link InetAddress} of the network to act on
     * @return the blaubot instance
     */
    public static BlaubotAndroid createEthernetBlaubotWithNFCBeacon(UUID appUUID, int acceptorPort, InetAddress ownInetAddress) {
        if (ownInetAddress == null || appUUID == null)
            throw new NullPointerException();
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotEthernetAdapter ethernetAdapter = new BlaubotEthernetAdapter(ownDevice, acceptorPort, ownInetAddress);
        BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();

        return (BlaubotAndroid) createBlaubot(appUUID, ownDevice, ethernetAdapter, nfcBeacon);
    }

    /**
     * Creates a blaubot instance using the bluetooth beacon and sockets to communicate.
     * Note: requires broadcast capable network
     *
     * @param appUUID        the app's uuid
     * @param acceptorPort   the port of the connector's accepting socket
     * @param ownInetAddress the own {@link InetAddress} of the network to act on
     * @return the blaubot instance
     */
    public static BlaubotAndroid createEthernetBlaubotWithBluetoothBeacon(UUID appUUID, int acceptorPort, InetAddress ownInetAddress) {
        if (ownInetAddress == null || appUUID == null) {
            throw new NullPointerException("InetAddress or appUUID was null.");
        }
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotEthernetAdapter ethernetAdapter = new BlaubotEthernetAdapter(ownDevice, acceptorPort, ownInetAddress);
        BlaubotBluetoothBeacon bluetoothBeacon = new BlaubotBluetoothBeacon();
        return createBlaubot(appUUID, ownDevice, ethernetAdapter, bluetoothBeacon);
    }

    /**
     * Creates a Blaubot instance from a given adapter and multiple beacons.
     *
     * @param appUuid   the app's unique uuid
     * @param ownDevice the own device containing this device's unique identifier
     * @param adapter   the adapter to be used (Sockets, WebSockets, Bluetooth, ...)
     * @param beacons   the becaons to be used (Bluetooth, NFC, Multicast, Bonjour, ...)
     * @return the blaubot instance
     */
    public static BlaubotAndroid createBlaubot(UUID appUuid, IBlaubotDevice ownDevice, IBlaubotAdapter adapter, IBlaubotBeacon... beacons) {
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUuid);
        List<IBlaubotAdapter> adapters = new ArrayList<>();
        adapters.add(adapter);
        BlaubotAndroid blaubotInstance = new BlaubotAndroid(ownDevice, uuidSet, adapters, Arrays.asList(beacons));
        return blaubotInstance;
    }

    /**
     * Sets up a default {@link Blaubot} instance using only the bluetooth adapter.
     *
     * @param appUUID the app's unique uuid
     * @param beaconPort the port on which the beacon accepts connections via tcp 
     * @param beaconBroadcastPort the port on which the beacons broadcasts and receives udp multicasts
     * @return blaubot instance
     */
    public static BlaubotAndroid createBluetoothBlaubotWithMulticastBeacon(UUID appUUID, int beaconPort, int beaconBroadcastPort) {
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        final InetAddress ownInetAddress = getLocalIpAddress();
        if (ownInetAddress == null || appUUID == null)
            throw new NullPointerException();

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new RuntimeException("Bluetooth is not supported by this device: No bluetooth default adapter found.");
        }
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
        BlaubotEthernetMulticastBeacon multicastBeacon = new BlaubotEthernetMulticastBeacon(beaconPort, beaconBroadcastPort);

        return (BlaubotAndroid) createBlaubot(appUUID, ownDevice, bluetoothAdapter, multicastBeacon);
    }

    /**
     * Sets up a default {@link Blaubot} instance using only bluetooth for discovery and network connections.
     *
     * @param appUUID the app's unique uuid
     * @return blaubot instance
     */
    public static BlaubotAndroid createBluetoothBlaubot(UUID appUUID) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new RuntimeException("Bluetooth is not supported by this device: No bluetooth default adapter found. ");
        }
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
        BlaubotBluetoothBeacon blaubotBluetoothBeacon = new BlaubotBluetoothBeacon();

        return (BlaubotAndroid) createBlaubot(appUUID, ownDevice, bluetoothAdapter, blaubotBluetoothBeacon);
    }

    /**
     * Creates a bluetooth driven blaubot intance using NFC as beacon.
     * Please ensure, that you delegate the android lifecycle events to the blaubot instance
     * as described in {@link BlaubotAndroid}.
     *
     * @param appUuid the app's uuid
     * @return the android blaubot instance
     */
    public static BlaubotAndroid createBluetoothBlaubotWithNFCBeacon(UUID appUuid) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new RuntimeException("Bluetooth is not supported by this device: No bluetooth default adapter found.");
        }

        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUuid);
        BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
        BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();

        return (BlaubotAndroid) createBlaubot(appUuid, ownDevice, bluetoothAdapter, nfcBeacon);
    }

    /**
     * Creates a bluetooth driven blaubot intance using NFC and Bluetooth as beacons.
     * Please ensure, that you delegate the android lifecycle events to the blaubot instance
     * as described in {@link BlaubotAndroid}.
     *
     * @param appUuid the app's uuid
     * @return the android blaubot instance
     */
    public static BlaubotAndroid createBluetoothBlaubotWithBluetoothAndNFCBeacon(UUID appUuid) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new RuntimeException("Bluetooth is not supported by this device: No bluetooth default adapter found.");
        }

        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUuid);
        BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
        BlaubotBluetoothBeacon bluetoothBeacon = new BlaubotBluetoothBeacon();
        BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();

        return (BlaubotAndroid) createBlaubot(appUuid, ownDevice, bluetoothAdapter, bluetoothBeacon, nfcBeacon);
    }


    /**
     * Sets up a default {@link Blaubot} instance using only the WIFIDirect adapter.
     * WARNING: This experimental and not recommended in production environments.
     *
     * @param appUUID         the app's unique uuid
     * @param p2pWifiManager  android's p2p wifi manager service 
     * @param wifiManager     the android wifi manager service
     * @param beaconChannel   the beaconChannel (wifip2p) to be used for beacon transmissions
     * @param acceptorChannel the acceptorChannel (wifip2p) on which connection are going to be accepted
     * @return blaubot instance
     */
    public static BlaubotAndroid createWifiP2PBlaubot(UUID appUUID, WifiP2pManager p2pWifiManager, WifiManager wifiManager, WifiP2pManager.Channel beaconChannel, WifiP2pManager.Channel acceptorChannel) {
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        //BlaubotWifiP2PAdapter adapter = new BlaubotWifiP2PAdapter(uuidSet, p2pWifiManager, wifiManager, acceptorChannel);
        BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
        BlaubotWifiP2PBeacon blaubotWifiP2PBeacon = new BlaubotWifiP2PBeacon(p2pWifiManager, beaconChannel, 1987);

        List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
        List<IBlaubotBeacon> beacons = new ArrayList<>();
        adapters.add(bluetoothAdapter);
        beacons.add(blaubotWifiP2PBeacon);

        BlaubotAndroid blaubotInstance = new BlaubotAndroid(ownDevice, uuidSet, adapters, beacons);
        return blaubotInstance;
    }


    /**
     * Sets up a default {@link Blaubot} instance using Wifi by creating wifi access points and socket
     * connections. Discovery is done by the NFC beacon.
     * WARNING: This experimental and not recommended in production environments.
     *
     * @param appUUID             the app's unique uuid
     * @param connectivityManager the connectivity system service
     * @param wifiManager         the android wifi manager
     * @param acceptorPort        the port to accept connections on
     * @return blaubot instance
     */
    public static BlaubotAndroid createWifiApWithNfcBeaconBlaubot(UUID appUUID, ConnectivityManager connectivityManager, WifiManager wifiManager, int acceptorPort) {
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());

        BlaubotWifiAdapter blaubotWifiAdapter = new BlaubotWifiAdapter(ownDevice, uuidSet, acceptorPort, wifiManager, connectivityManager);
        BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();

        return (BlaubotAndroid) createBlaubot(appUUID, ownDevice, blaubotWifiAdapter, nfcBeacon);
    }

    /**
     * Sets up a default {@link Blaubot} instance using Wifi by creating wifi access points and socket
     * connections. Discovery is done by the Bluetooth beacon.
     * WARNING: This experimental and not recommended in production environments.
     *
     * @param appUUID             the app's unique uuid
     * @param connectivityManager the connectivity system service
     * @param wifiManager         the android wifi manager
     * @param acceptorPort        the port to accept connections on
     * @return blaubot instance
     */
    public static BlaubotAndroid createWifiApWithBluetoothBeaconBlaubot(UUID appUUID, ConnectivityManager connectivityManager, WifiManager wifiManager, int acceptorPort) {
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());

        BlaubotWifiAdapter blaubotWifiAdapter = new BlaubotWifiAdapter(ownDevice, uuidSet, acceptorPort, wifiManager, connectivityManager);
        BlaubotBluetoothBeacon bluetoothBeacon = new BlaubotBluetoothBeacon();

        return (BlaubotAndroid) createBlaubot(appUUID, ownDevice, blaubotWifiAdapter, bluetoothBeacon);
    }
}
