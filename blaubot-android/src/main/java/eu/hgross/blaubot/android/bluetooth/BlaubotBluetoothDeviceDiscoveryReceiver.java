package eu.hgross.blaubot.android.bluetooth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.Parcelable;
import eu.hgross.blaubot.util.Log;

/**
 * Decoupled receiver object to find devices via the bluetooth adapter's discovery method.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class BlaubotBluetoothDeviceDiscoveryReceiver extends BroadcastReceiver {
	private List<BluetoothDevice> discoveredBluetoothDevices;
	private HashMap<BluetoothDevice, List<UUID>> deviceServicesMapping;

	private static final String DEBUG_TAG = "DeviceDiscoveryReceiver";
	private static final long KEEP_PERIOD = 12000; // ms
	private final Object monitor = new Object();
	private List<IBluetoothDiscoveryListener> bluetoothDiscoveryListeners;
	private long lastDiscoveryTimestamp = 0;
	private boolean fetchUUIDs;
	
	public BlaubotBluetoothDeviceDiscoveryReceiver(boolean fetchUUIDsAutomatically) {
		this.fetchUUIDs = fetchUUIDsAutomatically;
		this.discoveredBluetoothDevices = Collections.synchronizedList(new ArrayList<BluetoothDevice>());
		this.bluetoothDiscoveryListeners = Collections.synchronizedList(new ArrayList<IBluetoothDiscoveryListener>());
		this.deviceServicesMapping = new HashMap<BluetoothDevice, List<UUID>>();
	}

	private int start_finished_counter = 0;

	public boolean isDone() {
		return start_finished_counter == 0;
	}

	public void addBluetoothDiscoveryListener(IBluetoothDiscoveryListener bluetoothDiscoveryListener) {
		this.bluetoothDiscoveryListeners.add(bluetoothDiscoveryListener);
	}

	public void removeBluetoothDiscoveryListener(IBluetoothDiscoveryListener bluetoothDiscoveryListener) {
		this.bluetoothDiscoveryListeners.remove(bluetoothDiscoveryListener);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (BluetoothDevice.ACTION_FOUND.equals(action)) {
			BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			Log.d(DEBUG_TAG, "Discovered device: " + device.getName() + ", " + device);
			synchronized (monitor) {
				if(!discoveredBluetoothDevices.contains(device)) {
					discoveredBluetoothDevices.add(device);
				}
			}
			notifyDiscovery();
		} else if (BluetoothDevice.ACTION_UUID.equals(action)) {
			BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
			List<UUID> services = null;
			synchronized (monitor) {
				services = deviceServicesMapping.get(device);
				if (services == null) {
					services = new ArrayList<UUID>();
					deviceServicesMapping.put(device, services);
				}
			}
			if (uuidExtra != null) {
				for (int i = 0; i < uuidExtra.length; i++) {
					UUID service = ((ParcelUuid)uuidExtra[i]).getUuid();
//					Log.d(DEBUG_TAG, "Discovered service of: " + device.getName() + ", " + device + ", Service: " + service.toString());
					synchronized (monitor) {
						if (!services.contains(service)) {
							services.add(service);
						}
					}
				}
				notifyDiscovery();
			} 
		} else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
			Log.d(DEBUG_TAG, "\nDiscovery Started...");
			synchronized (monitor) {
				start_finished_counter += 1;
			}
			if (start_finished_counter == 1) {
				// only clear if the last discovery is a while ago (to overcome the retrigger behaviour)
				if (System.currentTimeMillis() - lastDiscoveryTimestamp > KEEP_PERIOD) {
					discoveredBluetoothDevices.clear();
					deviceServicesMapping.clear();
				}
				lastDiscoveryTimestamp = System.currentTimeMillis();
				notifyStarted();
			}
		} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
			Log.d(DEBUG_TAG, "\nDiscovery Finished");
			Iterator<BluetoothDevice> itr = discoveredBluetoothDevices.iterator();
			while (fetchUUIDs && itr.hasNext()) {
				// Get Services for paired devices
				BluetoothDevice device = itr.next();
				Log.d(DEBUG_TAG, "\nGetting Services for " + device.getName() + ", " + device);
				// device.getUuids();
				if (!device.fetchUuidsWithSdp()) {
					Log.d("BlueBot", "\nSDP Failed for " + device.getName());
				}
			}
			synchronized (monitor) {
				// Some android devices trigger DISCOVERY_STARTED more often than DISCOVERY_FINISHED so we
				// fix this with this condition.
				if(!BluetoothAdapter.getDefaultAdapter().isDiscovering()) {
					start_finished_counter = 0;
				} else {
					start_finished_counter -= 1;
				}
			}
			if (isDone()) {
				notifyFinished();
			}
		}
	}

	private void notifyStarted() {
//		Log.d(DEBUG_TAG, "Notifying: Discovery started");
		for (IBluetoothDiscoveryListener listener : bluetoothDiscoveryListeners) {
			listener.onDiscoveryStarted();
		}
	}

	private void notifyDiscovery() {
//		Log.d(DEBUG_TAG, "Notifying: List of discovered devices/services changed.");
		// copy data structures
		ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>(this.discoveredBluetoothDevices);
		HashMap<BluetoothDevice, List<UUID>> serviceMapping = new HashMap<BluetoothDevice, List<UUID>>(this.deviceServicesMapping);
		for (BluetoothDevice device : serviceMapping.keySet()) {
			serviceMapping.put(device, new ArrayList<UUID>(serviceMapping.get(device)));
		}
		for (IBluetoothDiscoveryListener listener : bluetoothDiscoveryListeners) {
			listener.onDiscovery(devices, serviceMapping);
		}
	}

	private void notifyFinished() {
//		Log.d(DEBUG_TAG, "Notifying: Discovery finished");
		// copy data structures
		ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>(this.discoveredBluetoothDevices);
		HashMap<BluetoothDevice, List<UUID>> serviceMapping = new HashMap<BluetoothDevice, List<UUID>>(this.deviceServicesMapping);
		for (BluetoothDevice device : serviceMapping.keySet()) {
			serviceMapping.put(device, new ArrayList<UUID>(serviceMapping.get(device)));
		}
		for (IBluetoothDiscoveryListener listener : bluetoothDiscoveryListeners) {
			listener.onDiscoveryFinished(devices, serviceMapping);
		}
	}

	public List<BluetoothDevice> getDiscoveredBluetoothDevices() {
		return discoveredBluetoothDevices;
	}
	
	/**
	 * The device object for the given macAddress, if the device was already discovered.
	 * 
	 * @param macAddress the macAddress to search for
	 * @return The device object for the given macAddress, if the device was already discovered - null otherwise 
	 */
	public BluetoothDevice getBluetoothDeviceByAddress(String macAddress) {
		for(BluetoothDevice b : this.discoveredBluetoothDevices) {
			if (b.getAddress().equals(macAddress))
				return b;
		}
		return null;
	}
	
	/**
	 * Creates an {@link IntentFilter} for the bluetooth intents needed by this {@link BroadcastReceiver}.
	 * @return the set up {@link IntentFilter}
	 */
	public static IntentFilter createBluetoothIntentFilter() {
		// register bluetooth related intents
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_UUID);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		return filter;
	}
	
}
