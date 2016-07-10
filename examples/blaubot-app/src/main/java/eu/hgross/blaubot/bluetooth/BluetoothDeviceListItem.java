package eu.hgross.blaubot.bluetooth;

import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothDevice;

/**
 * ListView ListItem wrapper for BluetoothDevice
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BluetoothDeviceListItem {
	public BluetoothDevice device;

	public BluetoothDeviceListItem(BluetoothDevice device) {
		this.device = device;
	}

	public static List<BluetoothDeviceListItem> convertList(List<BluetoothDevice> list) {
		ArrayList<BluetoothDeviceListItem> items = new ArrayList<BluetoothDeviceListItem>();
		for(BluetoothDevice d : list)
			items.add(new BluetoothDeviceListItem(d));
		return items;
	}
	
	@Override
	public String toString() {
		return "BluetoothDeviceListItem [device=" + device + "]";
	}
}