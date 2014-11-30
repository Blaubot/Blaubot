package de.hsrm.blaubot.ethernet;

import java.net.InetAddress;
import java.net.UnknownHostException;

import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.util.Log;

/**
 * Blaubot device for ethernet.
 * The device is unique by it's uniqueID built in the format:
 * 	IpAddress###acceptorPort###beaconPort
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotEthernetDevice implements IBlaubotDevice {
	public static final String UNIQUE_ID_SEPARATOR = "###";
	private static final String LOG_TAG = "BlaubotEthernetDevice";
	private InetAddress inetAddress;
	private BlaubotEthernetAdapter adapter;
	private int beaconPort;
	private int acceptorPort;
	private String uniqueDeviceId;
	

	public BlaubotEthernetDevice(InetAddress inetAddress, int acceptorPort, int beaconPort, BlaubotEthernetAdapter blaubotEthernetAdapter) {
		if(inetAddress == null)
			throw new NullPointerException();
		if(blaubotEthernetAdapter == null)
			throw new NullPointerException();
		this.beaconPort = beaconPort;
		this.acceptorPort = acceptorPort;
		this.inetAddress = inetAddress;
		this.adapter = blaubotEthernetAdapter;
		createUniqueDeviceId();
	}
	
	/**
	 * Creates the device from a uniqueDeviceId
	 * @param uniqueId id in the form of IP-Address###acceptorPort###beaconPort
	 * @throws UnknownHostException 
	 */
	public BlaubotEthernetDevice(String uniqueId, BlaubotEthernetAdapter blaubotEthernetAdapter) throws UnknownHostException {
		String splitted[] = uniqueId.split(UNIQUE_ID_SEPARATOR);
		this.inetAddress = InetAddress.getByName(splitted[0]);
		this.acceptorPort = Integer.parseInt(splitted[1]);
		this.beaconPort = Integer.parseInt(splitted[2]);
		this.adapter = blaubotEthernetAdapter;
		createUniqueDeviceId();
	}

	@Override
	public int compareTo(IBlaubotDevice another) {
		return this.getUniqueDeviceID().compareTo(another.getUniqueDeviceID());
	}

	private void createUniqueDeviceId() {
		this.uniqueDeviceId = BlaubotEthernetUtils.createUniqueDeviceId(inetAddress, acceptorPort, beaconPort);
	}
	
	@Override
	public String getUniqueDeviceID() {
		return uniqueDeviceId;
	}

	@Override
	public String getReadableName() {
		return this.uniqueDeviceId;
	}

	@Override
	public boolean isGreaterThanOurDevice() {
		IBlaubotDevice ownDevice = adapter.getOwnDevice();
		if(ownDevice == this) {
			Log.w(LOG_TAG, "Comparing with myself");
			return true;
		}
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
	
	/**
	 * The {@link InetAddress} of this device
	 * @return The {@link InetAddress} of this device
	 */
	public InetAddress getInetAddress() {
		return inetAddress;
	}

	@Override
	public String toString() {
		return "BlaubotEthernetDevice [getUniqueDeviceID()=" + getUniqueDeviceID() + ", getReadableName()=" + getReadableName() + "]";
	}
	
	public int getBeaconPort() {
		return beaconPort;
	}

	public void setBeaconPort(int beaconPort) {
		this.beaconPort = beaconPort;
	}

	public int getAcceptorPort() {
		return acceptorPort;
	}

	public void setAcceptorPort(int acceptorPort) {
		this.acceptorPort = acceptorPort;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + acceptorPort;
		result = prime * result + ((adapter == null) ? 0 : adapter.hashCode());
		result = prime * result + beaconPort;
		result = prime * result + ((inetAddress == null) ? 0 : inetAddress.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BlaubotEthernetDevice other = (BlaubotEthernetDevice) obj;
		if (acceptorPort != other.acceptorPort)
			return false;
		if (adapter == null) {
			if (other.adapter != null)
				return false;
		} else if (!adapter.equals(other.adapter))
			return false;
		if (beaconPort != other.beaconPort)
			return false;
		if (inetAddress == null) {
			if (other.inetAddress != null)
				return false;
		} else if (!inetAddress.equals(other.inetAddress))
			return false;
		return true;
	}


	

}
