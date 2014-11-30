package de.hsrm.blaubot.core;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import de.hsrm.blaubot.ethernet.BlaubotEthernetAdapter;
import de.hsrm.blaubot.util.Log;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotFactory {
	private static final String LOG_TAG = "BlaubotFactory";
	
	abstract class CreateRunnable implements Runnable {
		public Blaubot mBlaubot;
	}
	
	/**
	 * Creates a ethernet blaubot instance with the default ports 17171, 17172, 17173
	 * and does some dirty stuff to get around android's pesky no network on main thread
	 * policy :p
	 * 
	 * @param appUUID the app's uuid
	 * @return the blaubot instance
	 */
	public static Blaubot createEthernetBlaubot(final UUID appUUID) {
		final CountDownLatch cdl = new CountDownLatch(1);
		CreateRunnable cr = new BlaubotFactory().new CreateRunnable() {
			@Override
			public void run() {
				InetAddress inetAddr= getLocalIpAddress();
				if(inetAddr == null)
					throw new RuntimeException("Failed to get local ip address.");
				mBlaubot = createEthernetBlaubot(appUUID, 17171, 17172, 17173, inetAddr);
				cdl.countDown();
			}
		};
		new Thread(cr).start();
		try {
			cdl.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return cr.mBlaubot;
	}
	
	/**
	 * Creates a blaubot instance using the multicast ethernet adapter.
	 * Note: requires broadcast capable network
	 * 
	 * @param appUUID the app's uuid
	 * @param acceptorPort the port of the connector's accepting socket
	 * @param beaconPort the port of the beacon's accepting socket
	 * @param beaconBroadcastPort the broadcast port 
	 * @param ownInetAddress the own {@link InetAddress} of the network to act on
	 * @return
	 */
	public static Blaubot createEthernetBlaubot (UUID appUUID, int acceptorPort, int beaconPort, int beaconBroadcastPort, InetAddress ownInetAddress) {
		if(ownInetAddress == null || appUUID == null)
			throw new NullPointerException();
		BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
		IBlaubotAdapter ethernetAdapter = new BlaubotEthernetAdapter(uuidSet, beaconPort, beaconBroadcastPort, acceptorPort, ownInetAddress);
		List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
		adapters.add(ethernetAdapter);
		Blaubot blaubotInstance = new Blaubot(adapters);
		System.out.println("Created Ethernet-Blaubot instance (multicast beacon): " + blaubotInstance);
		return blaubotInstance;
	}
	
	/**
	 * Creates a blaubot instance using the ethernet adapter.
	 * This instance will only operate on the given fixedDevicesSet and does not do any discovery.
	 *
	 * @param appUUID the app's uuid
	 * @param acceptorPort the port of the connector's accepting socket
	 * @param beaconPort the port of the beacon's accepting socket
	 * @param ownInetAddress the own {@link InetAddress} of the network to act on
	 * @param fixedDevicesSet a set of fixed devices to connect to (represented as uniqueIdStrings)
	 * @return
	 */
	public static Blaubot createEthernetBlaubotWithFixedDevices (UUID appUUID, int acceptorPort, int beaconPort, InetAddress ownInetAddress, Set<String> fixedDevicesSet) {
		if(ownInetAddress == null || appUUID == null)
			throw new NullPointerException();
		BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(appUUID);
		IBlaubotAdapter ethernetAdapter = new BlaubotEthernetAdapter(uuidSet, beaconPort, acceptorPort, ownInetAddress, fixedDevicesSet);
		List<IBlaubotAdapter> adapters = new ArrayList<IBlaubotAdapter>();
		adapters.add(ethernetAdapter);
		Blaubot blaubotInstance = new Blaubot(adapters);
		System.out.println("Created Ethernet-Blaubot instance (fixed device set beacon): " + blaubotInstance);
		return blaubotInstance;
	}
	
	
	/**
	 * TODO: move to another location
	 * @return the most likely SiteLocal inetAddress or null, if everything went wrong
	 */
	public static InetAddress getLocalIpAddress() {
		try {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Searching interface to use.");
			}
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				if(intf.isLoopback() || !intf.isUp()) {
					continue;
				}
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress() && !inetAddress.isAnyLocalAddress() && inetAddress.isSiteLocalAddress()) {
						if(Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Using interface " + intf + " (IP: " + inetAddress + ")");
						}
						return inetAddress;
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(LOG_TAG, ex.toString());
		}

		return null;
	}
}
