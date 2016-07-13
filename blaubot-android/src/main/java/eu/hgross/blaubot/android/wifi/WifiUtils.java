package eu.hgross.blaubot.android.wifi;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import eu.hgross.blaubot.util.Log;


/**
 * Helper methods for the WIFI implementations.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class WifiUtils {
	private static final String LOG_TAG = "WifiP2PUtils";

	
	/**
	 * logs the /proc/net/arp file to the log
	 */
	public static void printARP() {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("/proc/net/arp"));
			String line;
			while ((line = br.readLine()) != null) {
				Log.d(LOG_TAG, line);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * Tries to resolve the ip address for a given mac address from the ARP cache.
     * 
	 * @param mac the mac address to get the ip for
	 * @return null or ip
	 */
	public static String getIpByMACFromARP(String mac) {
		/*
		 * The ARP format:
		 * 
		 * IP address HW type Flags HW address Mask Device 192.168.3.29 0x1 0x2 c4:43:8f:ca:74:d7 * wlan0
		 */
		BufferedReader br = null;
		String result = null;
		try {
			br = new BufferedReader(new FileReader("/proc/net/arp"));
			String line;
			int i = 0;
			while ((line = br.readLine()) != null) {
				if (i++ == 0)
					continue;
				String[] splitted = line.split("\\s+");
				if(splitted.length >= 4 && splitted[3].equalsIgnoreCase(mac)) {
					result = splitted[0];
			        break;
			      }
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
			}
		}
		return result;
	}


	/**
	 * Given an ipv4 <strong>ip</strong> address, tries to retrieve the MAC of the device to which
     * this ip is assigned.
	 * 
	 * @param ip the ipv4 address in the form of x.x.x.x
	 * @return null or mac
	 */
	public static String getMACbyIpFromARP(String ip) {
		/*
		 * The ARP format:
		 * 
		 * IP address HW type Flags HW address Mask Device 192.168.3.29 0x1 0x2 c4:43:8f:ca:74:d7 * wlan0
		 */
		BufferedReader br = null;
		String result = null;
		try {
			br = new BufferedReader(new FileReader("/proc/net/arp"));
			String line;
			int i = 0;
			while ((line = br.readLine()) != null) {
				if (i++ == 0)
					continue;
				String[] splitted = line.split("\\s+");
				if(splitted.length >= 1 && splitted[0].equalsIgnoreCase(ip)) {
					result = splitted[0];
			        break;
			      }
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
			}
		}
		return result;
	}
	
	
	/**
	 * log local ip addresses to log
	 */
	public static void logInterfacesAndIpAddresses() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
//				if(intf.isLoopback() || !intf.isUp()) {
//					continue;
//				}
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					Log.d(LOG_TAG, "Interface " + intf + " (IP: " + inetAddress + ")");
//					if (!inetAddress.isLoopbackAddress() && !inetAddress.isAnyLocalAddress() && inetAddress.isSiteLocalAddress()) {
//					}
				}
			}
		} catch (SocketException ex) {
			Log.e(LOG_TAG, ex.toString());
		}

	}
}
