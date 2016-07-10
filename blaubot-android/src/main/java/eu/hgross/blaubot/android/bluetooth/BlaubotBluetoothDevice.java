package eu.hgross.blaubot.android.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Parcel;
import android.os.ParcelUuid;

import java.io.IOException;
import java.util.UUID;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.connector.IncompatibleBlaubotDeviceException;

/**
 * Wrapper Object for android's {@link BluetoothDevice} objects only for the bluetooth beacon.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class BlaubotBluetoothDevice extends BlaubotDevice {
	private BluetoothDevice device;

	public BlaubotBluetoothDevice(String uniqueDeviceId, BluetoothDevice androidBluetoothDevice) {
		super(uniqueDeviceId);
        this.device = androidBluetoothDevice;
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
		if(this == o) return true;
        if (o == null || !(o instanceof IBlaubotDevice)) return false;
        return this.getUniqueDeviceID().equals(((IBlaubotDevice) o).getUniqueDeviceID());
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
		return getUniqueDeviceID().hashCode();
	}

	public String toString() {
		return "BlaubotBluetoothDevice[" + getAddress() + ", " + getReadableName() + ", " + getUniqueDeviceID() + "]";
	}

	public void writeToParcel(Parcel out, int flags) {
		device.writeToParcel(out, flags);
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
