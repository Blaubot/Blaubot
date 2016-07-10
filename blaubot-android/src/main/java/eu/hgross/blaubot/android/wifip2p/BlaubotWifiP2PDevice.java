package eu.hgross.blaubot.android.wifip2p;

import android.net.wifi.p2p.WifiP2pDevice;

import eu.hgross.blaubot.core.BlaubotDevice;

/**
 * 
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlaubotWifiP2PDevice extends BlaubotDevice {
	private WifiP2pDevice device;

	public BlaubotWifiP2PDevice(String uniqueDeviceId, WifiP2pDevice device) {
        super(uniqueDeviceId);
		this.device = device;
	}

    /**
     * The WifiP2pDevice
     * @return the native device
     */
    public WifiP2pDevice getWifiP2pDevice() {
        return device;
    }

    @Override
	public String getReadableName() {
		return device.deviceName;
	}
}
