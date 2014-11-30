package de.hsrm.blaubot.android.bluetooth;

import android.bluetooth.BluetoothAdapter;
import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotDevice;

/**
 * Singleton Device to represent the BluetoothAdapter's device
 * TODO: The singleton approach might be bad if there is more than one bt adapter present.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class OwnAndroidBluetoothDevice implements IBlaubotDevice {
	private static OwnAndroidBluetoothDevice DEVICE;
	public static OwnAndroidBluetoothDevice getInstance() {
		if(DEVICE == null)
			DEVICE = new OwnAndroidBluetoothDevice();
		return DEVICE;
	}
	private OwnAndroidBluetoothDevice() {}

	@Override
	public int compareTo(IBlaubotDevice another) {
		return getUniqueDeviceID().compareTo(another.getUniqueDeviceID());
	}

	@Override
	public String getUniqueDeviceID() {
		return BluetoothAdapter.getDefaultAdapter().getAddress();
	}

	@Override
	public String getReadableName() {
		return BluetoothAdapter.getDefaultAdapter().getName();
	}

	@Override
	public boolean isGreaterThanOurDevice() {
		return false;
	}
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof IBlaubotDevice)) {
			return false;
		}
		return ((IBlaubotDevice)o).getUniqueDeviceID().equals(getUniqueDeviceID());
	}
	
	@Override
	public int hashCode() {
		return BluetoothAdapter.getDefaultAdapter().hashCode();
	}
	@Override
	public IBlaubotAdapter getAdapter() {
		return null;
	}

}
