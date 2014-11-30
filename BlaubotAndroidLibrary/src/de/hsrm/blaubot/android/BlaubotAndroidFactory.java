package de.hsrm.blaubot.android;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import de.hsrm.blaubot.android.bluetooth.BlaubotBluetoothAdapter;
import de.hsrm.blaubot.android.wifip2p.BlaubotWifiP2PAdapter;
import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.BlaubotUUIDSet;
import de.hsrm.blaubot.core.IBlaubotAdapter;

/**
 * Factory to create {@link Blaubot} instances for Android.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotAndroidFactory extends de.hsrm.blaubot.core.BlaubotFactory {
	/**
	 * Sets up a default {@link Blaubot} instance using only the bluetooth adapter.
	 * @param appUUID the app's unique uuid
	 * @return blaubot instance
	 */
	public static BlaubotAndroid createBluetoothBlaubot(UUID appUUID) {
		BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
		IBlaubotAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet);
		List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
		adapters.add(bluetoothAdapter);
		BlaubotAndroid blaubotInstance = new BlaubotAndroid(adapters);
		return blaubotInstance;
	}
	
	/**
	 * Sets up a default {@link Blaubot} instance using only the WIFIDirect adapter.
	 * @param appUUID the app's unique uuid
	 * @param manager
	 * @param beaconChannel 
	 * @param acceptorChannel 
	 * @return blaubot instance
	 */
	public static BlaubotAndroid createWifiP2PBlaubot(UUID appUUID, WifiP2pManager p2pWifiManager, WifiManager wifiManager, Channel beaconChannel, Channel acceptorChannel) {
		BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
		IBlaubotAdapter adapter = new BlaubotWifiP2PAdapter(uuidSet, p2pWifiManager, wifiManager, beaconChannel, acceptorChannel);
		List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
		adapters.add(adapter);
		BlaubotAndroid blaubotInstance = new BlaubotAndroid(adapters);
		return blaubotInstance;
	}
}
