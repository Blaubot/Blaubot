package eu.hgross.blaubot.android;

import android.bluetooth.BluetoothAdapter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import eu.hgross.blaubot.android.bluetooth.BlaubotBluetoothAdapter;
import eu.hgross.blaubot.android.bluetooth.BlaubotBluetoothBeacon;
import eu.hgross.blaubot.android.nfc.BlaubotNFCBeacon;
import eu.hgross.blaubot.android.wifi.BlaubotWifiAdapter;
import eu.hgross.blaubot.android.wifip2p.BlaubotWifiP2PBeacon;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.ethernet.BlaubotEthernetAdapter;
import eu.hgross.blaubot.ethernet.BlaubotEthernetMulticastBeacon;

/**
 * Factory to create {@link Blaubot} instances for Android.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotAndroidFactory extends eu.hgross.blaubot.core.BlaubotFactory {

    /**
     * Creates a blaubot instance using ethernet adapter and the NFC beacon.
     *
     * Note that you have to call some lifecycle methods onResume and onPause.
     * See BlaubotAndroid.
     *
     * @param appUUID the app's uuid
     * @param acceptorPort the port of the connector's accepting socket
     * @param ownInetAddress the own {@link InetAddress} of the network to act on
     * @return the blaubot instance
     */
    public static BlaubotAndroid createEthernetBlaubotWithNFCBeacon (UUID appUUID, int acceptorPort, InetAddress ownInetAddress) {
        if(ownInetAddress == null || appUUID == null)
            throw new NullPointerException();
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        BlaubotEthernetAdapter ethernetAdapter = new BlaubotEthernetAdapter(ownDevice, uuidSet, acceptorPort, ownInetAddress);
        BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();

        List<IBlaubotAdapter> adapters = new ArrayList<>();
        List<IBlaubotBeacon> beacons = new ArrayList<>();
        adapters.add(ethernetAdapter);
        beacons.add(nfcBeacon);

        BlaubotAndroid blaubotInstance = new BlaubotAndroid(ownDevice, uuidSet, adapters, beacons);
        System.out.println("Created Ethernet-Blaubot instance (NFCBeacon): " + blaubotInstance);
        return blaubotInstance;
    }

    /**
	 * Sets up a default {@link Blaubot} instance using only the bluetooth adapter.
	 * @param appUUID the app's unique uuid
	 * @return blaubot instance
	 */
	public static BlaubotAndroid createBluetoothBlaubotWithMulticastBeacon(UUID appUUID, int acceptorPort, int beaconPort, int beaconBroadcastPort) {
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        final InetAddress ownInetAddress = getLocalIpAddress();
        if(ownInetAddress == null || appUUID == null)
            throw new NullPointerException();

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter == null) {
            throw new RuntimeException("Bluetooth is not supported by this device: No bluetooth default adapter found.");
        }
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
		BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
//        BlaubotBluetoothBeacon blaubotBluetoothBeacon = new BlaubotBluetoothBeacon(uuidSet, ownDevice);
        BlaubotEthernetMulticastBeacon multicastBeacon = new BlaubotEthernetMulticastBeacon(beaconPort, beaconBroadcastPort);

        List<IBlaubotAdapter> adapters = new ArrayList<>();
		List<IBlaubotBeacon> beacons = new ArrayList<>();
        adapters.add(bluetoothAdapter);
//        beacons.add(blaubotBluetoothBeacon);
        beacons.add(multicastBeacon);

        BlaubotAndroid blaubotInstance = new BlaubotAndroid(ownDevice, uuidSet, adapters, beacons);
        System.out.println("Created Bluetooth Blaubot instance (multicast beacon): " + blaubotInstance);
        return blaubotInstance;
	}

    /**
     * Sets up a default {@link Blaubot} instance using only bluetooth for discovery and network connections.
     * @param appUUID the app's unique uuid
     * @return blaubot instance
     */
    public static BlaubotAndroid createBluetoothBlaubot(UUID appUUID) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter == null) {
            throw new RuntimeException("Bluetooth is not supported by this device: No bluetooth default adapter found.");
        }
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
        BlaubotBluetoothBeacon blaubotBluetoothBeacon = new BlaubotBluetoothBeacon();

        List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
        List<IBlaubotBeacon> beacons = new ArrayList<>();
        adapters.add(bluetoothAdapter);
        beacons.add(blaubotBluetoothBeacon);

        BlaubotAndroid blaubotInstance = new BlaubotAndroid(ownDevice, uuidSet, adapters, beacons);
        return blaubotInstance;
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
        if(adapter == null) {
            throw new RuntimeException("Bluetooth is not supported by this device: No bluetooth default adapter found.");
        }

        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUuid);
        BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
        BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();

        List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
        List<IBlaubotBeacon> beacons = new ArrayList<>();
        adapters.add(bluetoothAdapter);
        beacons.add(nfcBeacon);

        BlaubotAndroid blaubotInstance = new BlaubotAndroid(ownDevice, uuidSet, adapters, beacons);
        return blaubotInstance;
    }

	/**
	 * Sets up a default {@link Blaubot} instance using only the WIFIDirect adapter.
     *
	 * @param appUUID the app's unique uuid
	 * @param wifiManager
	 * @param beaconChannel 
	 * @param acceptorChannel 
	 * @return blaubot instance
	 */
	public static BlaubotAndroid createWifiP2PBlaubot(UUID appUUID, WifiP2pManager p2pWifiManager, WifiManager wifiManager, Channel beaconChannel, Channel acceptorChannel) {
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
     * connections.
     *
     * @param appUUID the app's unique uuid
     * @param connectivityManager the connectivity system service
     * @param wifiManager the android wifi manager
     * @param acceptorPort the port to accept connections on
     * @return blaubot instance
     */
    public static BlaubotAndroid createWifiApBlaubot(UUID appUUID, ConnectivityManager connectivityManager, WifiManager wifiManager, int acceptorPort) {
        BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
        IBlaubotDevice ownDevice = new BlaubotDevice(UUID.randomUUID().toString());

        BlaubotWifiAdapter blaubotWifiAdapter= new BlaubotWifiAdapter(ownDevice, uuidSet, acceptorPort, wifiManager, connectivityManager);
        BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();

        List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
        List<IBlaubotBeacon> beacons = new ArrayList<>();
        adapters.add(blaubotWifiAdapter);
        beacons.add(nfcBeacon);

        BlaubotAndroid blaubotInstance = new BlaubotAndroid(ownDevice, uuidSet, adapters, beacons);
        return blaubotInstance;
    }

}
