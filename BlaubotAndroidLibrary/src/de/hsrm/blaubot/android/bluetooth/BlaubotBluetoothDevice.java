package de.hsrm.blaubot.android.bluetooth;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Parcel;
import android.os.ParcelUuid;
import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.connector.IncompatibleBlaubotDeviceException;

/**
 * Wrapper Object for android's {@link BluetoothDevice} objects.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotBluetoothDevice implements IBlaubotDevice {
	private BluetoothDevice device;
	private IBlaubotAdapter adapter;

	public BlaubotBluetoothDevice(BluetoothDevice androidBluetoothDevice, IBlaubotAdapter adapter) {
		this.device = androidBluetoothDevice;
		this.adapter = adapter;
	}

	/*
	 * Delegating android Methods from here ...
	 */

	public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) throws IOException {
		return device.createInsecureRfcommSocketToServiceRecord(uuid);
	}

	public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
		return device.createRfcommSocketToServiceRecord(uuid);
	}

	public int describeContents() {
		return device.describeContents();
	}

	public boolean equals(Object o) {
		if(o instanceof BlaubotBluetoothDevice) {
			BlaubotBluetoothDevice blaubotBluetoothDevice = (BlaubotBluetoothDevice) o;
			return device.equals(blaubotBluetoothDevice.device) && this.adapter == blaubotBluetoothDevice.adapter;
		}
		return device.equals(o);
	}

	public boolean fetchUuidsWithSdp() {
		return device.fetchUuidsWithSdp();
	}

	public String getAddress() {
		return device.getAddress();
	}

	public BluetoothClass getBluetoothClass() {
		return device.getBluetoothClass();
	}

	public int getBondState() {
		return device.getBondState();
	}

	public String getName() {
		return device.getName();
	}

	public ParcelUuid[] getUuids() {
		return device.getUuids();
	}

	public int hashCode() {
		return device.hashCode();
	}

	public String toString() {
		return "BlaubotBluetoothDevice[" + getAddress() + ", " + getReadableName() + "]";
	}

	public void writeToParcel(Parcel out, int flags) {
		device.writeToParcel(out, flags);
	}

	@Override
	public String getUniqueDeviceID() {
		return getAddress();
	}

	@Override
	public String getReadableName() {
		return getName();
	}

	@Override
	public int compareTo(IBlaubotDevice another) {
		if (!(another instanceof BlaubotBluetoothDevice))
			throw new IncompatibleBlaubotDeviceException("Tried to compare non comparable blaubot devices.");
		BlaubotBluetoothDevice other = (BlaubotBluetoothDevice) another;
		return getAddress().compareTo(other.getAddress());
	}

	@Override
	public boolean isGreaterThanOurDevice() {
		String ourAddress = BluetoothAdapter.getDefaultAdapter().getAddress();
		return ourAddress.compareTo(getAddress()) < 0;
	}

	@Override
	public IBlaubotAdapter getAdapter() {
		return adapter;
	}

	// This methods below are API LEVEL 19!
	// public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback) {
	// return device.connectGatt(context, autoConnect, callback);
	// }
	//
	// public boolean createBond() {
	// return device.createBond();
	// }

	// public boolean setPairingConfirmation(boolean confirm) {
	// return device.setPairingConfirmation(confirm);
	// }
	//
	// public boolean setPin(byte[] pin) {
	// return device.setPin(pin);
	// }
	//
	// public int getType() {
	// return device.getType();
	// }
}
