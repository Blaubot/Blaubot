package eu.hgross.blaubot.android.bluetooth;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import eu.hgross.blaubot.core.IBlaubotDevice;

import android.bluetooth.BluetoothDevice;

/**
 * TODO: introduce an abstraction layer to remove {@link BluetoothDevice} and to use {@link IBlaubotDevice} instead
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public interface IBluetoothDiscoveryListener {
	/**
	 * Called whenever a discovery was started
	 */
	public void onDiscoveryStarted();
	
	/**
	 * Called whenever something new or a change was discovered.
	 * @param discoveredDevices all discovered devices during the last discovery
	 * @param discoveredServices a device to service mapping - including discovered devices and their sdp lookup results
	 */
	public void onDiscovery(List<BluetoothDevice> discoveredDevices, HashMap<BluetoothDevice, List<UUID>> discoveredServices);
	
	/**
	 * Called after the discovery finished.
	 * @param discoveredDevices all discovered devices during the last discovery
	 * @param discoveredServices a device to service mapping - including discovered devices and their sdp lookup results
	 */
	public void onDiscoveryFinished(List<BluetoothDevice> discoveredDevices, HashMap<BluetoothDevice, List<UUID>> discoveredServices);
}
