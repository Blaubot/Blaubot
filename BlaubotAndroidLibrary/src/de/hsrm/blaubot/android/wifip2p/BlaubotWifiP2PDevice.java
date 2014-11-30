package de.hsrm.blaubot.android.wifip2p;

import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Parcel;
import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotDevice;

/**
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotWifiP2PDevice implements IBlaubotDevice {
	private WifiP2pDevice device;
	private IBlaubotAdapter adapter;
	
	public BlaubotWifiP2PDevice(IBlaubotAdapter adapter, WifiP2pDevice device) {
		this.adapter = adapter;
		this.device = device;
	}
	
	@Override
	public int compareTo(IBlaubotDevice another) {
		return this.getUniqueDeviceID().compareTo(another.getUniqueDeviceID());
	}

	@Override
	public String getUniqueDeviceID() {
		return device.deviceAddress;
	}

	@Override
	public String getReadableName() {
		return device.deviceName;
	}

	@Override
	public boolean isGreaterThanOurDevice() {
		IBlaubotDevice ownDevice = adapter.getOwnDevice();
		if(ownDevice == this)
			return true;
		// a.compareTo(b)
		// a<b  -> <0
		// a==b ->  0
		// a>b  -> >0
		return ownDevice.compareTo(this) < 0;
	}

	@Override
	public IBlaubotAdapter getAdapter() {
		return adapter;
	}
	
	/*
	 * From here delegation of WifiP2PDevice methods
	 */
	
	public boolean wpsPbcSupported() {
		return device.wpsPbcSupported();
	}

	public boolean wpsKeypadSupported() {
		return device.wpsKeypadSupported();
	}

	public boolean wpsDisplaySupported() {
		return device.wpsDisplaySupported();
	}

	public boolean isServiceDiscoveryCapable() {
		return device.isServiceDiscoveryCapable();
	}

	public boolean isGroupOwner() {
		return device.isGroupOwner();
	}

	public boolean equals(Object obj) {
		return device.equals(obj);
	}

	public String toString() {
		return device.toString();
	}

	public int describeContents() {
		return device.describeContents();
	}

	public void writeToParcel(Parcel dest, int flags) {
		device.writeToParcel(dest, flags);
	}

	public int hashCode() {
		return device.hashCode();
	}
	
}
