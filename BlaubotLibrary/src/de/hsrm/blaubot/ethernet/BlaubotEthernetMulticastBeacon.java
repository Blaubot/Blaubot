package de.hsrm.blaubot.ethernet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotListeningStateListener;
import de.hsrm.blaubot.core.acceptor.discovery.BeaconMessage;
import de.hsrm.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import de.hsrm.blaubot.core.acceptor.discovery.ExchangeStatesTask;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import de.hsrm.blaubot.core.acceptor.discovery.TimeoutList;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.util.Log;

/**
 * Beacon for ethernet using broadcasts. It consists of two broadcast specific threads (Broadcaster and
 * BroadcastDiscoverer) and a beacon accept thread. The broadcasts simply shout the current beaconUUID out in the world.
 * If a BroadcastReceiver gets to recongnize the beaconUUID (which must be the same as its own) the device is added
 * to a {@link TimeoutList}. 
 * 
 * While the discovery is activated, a BeaconScanner goes through all devices known as alive and tries to connect to
 * their beacon. On a successful connection the resulting {@link IBlaubotConnection} is handed to the registered 
 * {@link IBlaubotIncomingConnectionListener}. From here the {@link BlaubotBeaconService} will handle the beacon conversation
 * via the {@link ExchangeStatesTask} (exchanging {@link BeaconMessage}s). 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotEthernetMulticastBeacon implements IBlaubotBeaconInterface, IEthernetBeacon {
	private static final String LOG_TAG = "BlaubotEthernetBeacon";
	private static final int BROADCASTER_INTERVAL = 6500;
	private static final long BEACON_PROBE_INTERVAL = 200;
	private static final int ALIVE_TIMEOUT = BROADCASTER_INTERVAL*5;
	private static final int BROADCAST_MESSAGE_METAINFO_LENGTH = 2 * 4; // == 2 integer -> AcceptorPort, BeaconPort
	private final BlaubotEthernetAdapter blaubotEthernetAdapter;
	private final int beaconPort;
	private final int beaconBroadcastPort;
	private final UUID beaconUUID;

	private volatile IBlaubotState currentState;
	private volatile IBlaubotDiscoveryEventListener discoveryEventListener;
	private volatile IBlaubotIncomingConnectionListener incomingConnectionListener;
	private volatile IBlaubotListeningStateListener listeningStateListener;
	private volatile boolean discoveryActive = true;
	/**
	 * Contains all devices which sent us a broadcast in between the last x seconds.
	 */
	private final TimeoutList<IBlaubotDevice> knownActiveDevices;

	private EthernetBeaconAcceptThread acceptThread;
	private BroadcasterThread broadcaster;
	private BroadcastDiscovererThread broadcastDiscoverer;
	private EthernetBeaconScanner beaconScanner;
	private Object startStopMonitor;

	public BlaubotEthernetMulticastBeacon(BlaubotEthernetAdapter blaubotEthernetAdapter, int beaconPort, int beaconBroadcastPort) {
		this.startStopMonitor = new Object();
		this.beaconPort = beaconPort;
		this.beaconBroadcastPort = beaconBroadcastPort;
		this.blaubotEthernetAdapter = blaubotEthernetAdapter;
		this.beaconUUID = blaubotEthernetAdapter.getUuidSet().getBeaconUUID();
		this.knownActiveDevices = new TimeoutList<IBlaubotDevice>((long) ALIVE_TIMEOUT);
		this.broadcastMessage = createBroadcastMessage();
	}

	@Override
	public void startListening() {
		synchronized (startStopMonitor) {
			if (isStarted()) {
				return;
			}
			acceptThread = new EthernetBeaconAcceptThread(incomingConnectionListener, this);
			broadcaster = new BroadcasterThread();
			broadcastDiscoverer = new BroadcastDiscovererThread();
			beaconScanner = new EthernetBeaconScanner();
			
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Beacon is starting to listen for incoming connections on port " + beaconPort);
			}
			acceptThread.start();
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "BroadcastDiscoverer is starting");
			}
			broadcastDiscoverer.start();
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Broadcaster is starting");
			}
			broadcaster.start();
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "EthernetBeaconScanner is starting");
			}
			beaconScanner.start();
			
			if (listeningStateListener != null)
				listeningStateListener.onListeningStarted(this);
		}
	}

	@Override
	public void stopListening() {
		synchronized (startStopMonitor) {
			if (!isStarted()) {
				return;
			}
			if (beaconScanner != null && beaconScanner.isAlive()) {
				beaconScanner.interrupt();
	//			try {
	//				beaconScanner.join();
	//			} catch (InterruptedException e) {
	//				throw new RuntimeException(e);
	//			}
			}
			beaconScanner = null;
			
			if (broadcaster != null && broadcaster.isAlive()) {
				broadcaster.interrupt();
				try {
					broadcaster.join();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			broadcaster = null;
			
			if (broadcastDiscoverer != null && broadcastDiscoverer.isAlive()) {
				broadcastDiscoverer.interrupt();
				try {
					broadcastDiscoverer.join();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			broadcastDiscoverer = null;
			
			if (acceptThread != null && acceptThread.isAlive()) {
				try {
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Waiting for beacon accept thread to finish ...");
					}
					acceptThread.interrupt();
					acceptThread.join();
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Beacon accept thread to finished ...");
					}
				} catch (InterruptedException e) {
					if (Log.logWarningMessages()) {
						Log.w(LOG_TAG, e);
					}
				}
				acceptThread = null;
			}
			
			if (listeningStateListener != null)
				listeningStateListener.onListeningStopped(this);
		}
	}

	@Override
	public boolean isStarted() {
		return acceptThread != null && acceptThread.isAlive();
	}

	@Override
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
		this.listeningStateListener = stateListener;
	}

	@Override
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
		this.incomingConnectionListener = acceptorListener;
	}

	@Override
	public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
		this.discoveryEventListener = discoveryEventListener;
	}

	@Override
	public void onConnectionStateMachineStateChanged(IBlaubotState state) {
		this.currentState = state;
	}

	@Override
	public void setDiscoveryActivated(boolean active) {
		this.discoveryActive = active;
	}

	/**
	 * Broadcasts the beacon's existence (BeaconUUID) periodically to the network.
	 * The message has the format BEACON_UUID;acceptorPort;beaconPort
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	private final byte[] broadcastMessage;

	private byte[] createBroadcastMessage() {
		byte[] uuidBytes = beaconUUID.toString().getBytes(BlaubotConstants.STRING_CHARSET);
		// -- UUIDs have fixed lengths
		ByteBuffer bb = ByteBuffer.allocate(uuidBytes.length + BROADCAST_MESSAGE_METAINFO_LENGTH);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.put(uuidBytes);
		bb.putInt(blaubotEthernetAdapter.getAcceptorPort());
		bb.putInt(blaubotEthernetAdapter.getBeaconPort());
		bb.flip();
		return bb.array();
	}

	class BroadcasterThread extends Thread {
		private static final String LOG_TAG = "MulticastBroadcaster";
		private static final int SEND_INTERVAL = BROADCASTER_INTERVAL;

		@Override
		public void run() {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Broadcaster started ...");
			}
			DatagramSocket serverSocket = null;
			try {
				serverSocket = new DatagramSocket();
				serverSocket.setBroadcast(true);
			} catch (SocketException e1) {
				return;
			}

			while (!isInterrupted() && Thread.currentThread() == broadcaster) {
				// try to send to 255.255.255.255 first
				try {
					DatagramPacket packetToSend = new DatagramPacket(broadcastMessage, broadcastMessage.length, InetAddress.getByName("255.255.255.255"), beaconBroadcastPort);
					serverSocket.send(packetToSend);
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Broadcast message sent to: 255.255.255.255");
					}
				} catch (IOException e) {
					// Log.e(LOG_TAG, "Failed to broadcast to 255.255.255.255", e);
				}

				Enumeration<NetworkInterface> interfaces;
				try {
					interfaces = NetworkInterface.getNetworkInterfaces();
				} catch (SocketException e1) {
					if (Log.logErrorMessages()) {
						Log.e(LOG_TAG, "Failed to get network interfaces", e1);
					}
					break;
				}
				while (interfaces.hasMoreElements()) {
					NetworkInterface networkInterface = interfaces.nextElement();
					try {
						if (networkInterface.isLoopback() || !networkInterface.isUp()) {
							continue;
						}
					} catch (SocketException e1) {
						if (Log.logWarningMessages()) {
							Log.w(LOG_TAG, "Failed to get network informations", e1);
						}
					}
					for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
						InetAddress broadcast = interfaceAddress.getBroadcast();
						if (broadcast == null) {
							continue;
						}
						try {
							DatagramPacket sendPacket = new DatagramPacket(broadcastMessage, broadcastMessage.length, broadcast, beaconPort);
							serverSocket.send(sendPacket);
						} catch (Exception e) {
							if (Log.logWarningMessages()) {
								Log.w(LOG_TAG, "Failed to send broadcast message to " + broadcast.getHostAddress() + "; Interface: " + networkInterface.getDisplayName());
							}
						}

						if (Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Broadcast message sent to: " + broadcast.getHostAddress() + " over interface: " + networkInterface.getDisplayName());
						}
					}
				}

				try {
					Thread.sleep(SEND_INTERVAL);
				} catch (InterruptedException e) {
					break;
				}

			}
			serverSocket.close();
		}
	}

	/**
	 * Listens to Broadcasts on the network (if the discovery is active, see setDiscoveryActivated(). If a broadcast is
	 * discovered, a connection to the beacon will be established to exchange states.
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	class BroadcastDiscovererThread extends Thread {
		private static final int BROADCAST_DISCOVERER_SOCKET_TIMEOUT = 150;

		@Override
		public void run() {
			DatagramSocket receivingSocket = null;
			try {
				receivingSocket = new DatagramSocket(beaconBroadcastPort);
				receivingSocket.setSoTimeout(BROADCAST_DISCOVERER_SOCKET_TIMEOUT);
			} catch (SocketException e) {
				return;
			}

			byte[] buffer = new byte[broadcastMessage.length];
			while (!isInterrupted() && Thread.currentThread() == broadcastDiscoverer) {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				try {
					receivingSocket.receive(packet);
					if (isOwnInetAddr(packet.getAddress())) {
						// ignore inet addresses that belong to one of our own interfaces!
						continue;
					}
				} catch (SocketTimeoutException e) {
					// no connection for BROADCAST_DISCOVERER_SOCKET_TIMEOUT ms
					continue;
				} catch (IOException e) {
					if (Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Receive failed.", e);
					}
					continue;
				}

				byte[] uuidStrBytes = new byte[broadcastMessage.length - BROADCAST_MESSAGE_METAINFO_LENGTH];
				ByteBuffer bb = ByteBuffer.wrap(packet.getData());
				bb.order(ByteOrder.BIG_ENDIAN);
				bb.get(uuidStrBytes, 0, uuidStrBytes.length); // the uuid
				// now the meta infos (AcceptorPort, BeaconPort)
				int remoteAcceptorPort = bb.getInt();
				int remoteBeaconPort = bb.getInt();
				String uuidStr = new String(uuidStrBytes, BlaubotConstants.STRING_CHARSET);
				try {
					UUID receivedUUID = UUID.fromString(uuidStr);
					if (receivedUUID.equals(beaconUUID)) {
						InetAddress remoteDeviceAddr = packet.getAddress();
						BlaubotEthernetDevice device = new BlaubotEthernetDevice(remoteDeviceAddr, remoteAcceptorPort, remoteBeaconPort, blaubotEthernetAdapter);
						if (Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Received a relevant beaconUUID via broadcast from " + device + ". Reporting it as active device.");
						}
						knownActiveDevices.report(device);
					}
				} catch (IllegalArgumentException e) {
					if (Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Received String is not an valid UUID: " + uuidStr, e);
					}
				}
			}
			receivingSocket.close();
		}

	}

	/**
	 * Periodically checks all devies known as alive (added to the {@link TimeoutList} 
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 *
	 */
	class EthernetBeaconScanner extends Thread {
		
		private List<IBlaubotDevice> getAliveDevices() {
			ArrayList<IBlaubotDevice> devices = new ArrayList<IBlaubotDevice>(knownActiveDevices.getItems());
			// do not check the devices connected to the blaubot network
			devices.removeAll(blaubotEthernetAdapter.getBlaubot().getConnectionManager().getConnectedDevices());
			// note: we do not sort the collection since ethernet connection creation is beaming fast.
			return devices;
		}
		
		@Override
		public void run() {
			boolean exit = false;
			while(!isInterrupted() && Thread.currentThread() == beaconScanner && !exit) {
				if (isDiscoveryDisabled()) {
//						Log.d(LOG_TAG, "Discovery is not active - not connecting to discovered beacon.");
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						exit = true;
					}
					// we don't want to connect if discovery is deactivated.
					continue;
				}
				for(IBlaubotDevice d : getAliveDevices()) {
					if(isDiscoveryDisabled())
						break;
					BlaubotEthernetDevice device = (BlaubotEthernetDevice) d;
					InetAddress remoteDeviceAddr = device.getInetAddress();
					// -- we know that remoteDeviceAddr had a running beacon in the recent past as it is in the knownActiveDevices TimeoutList
					// try to connect, then exchange states via tcp/ip
					Socket clientSocket;
					try {
						clientSocket = new Socket(remoteDeviceAddr, beaconPort);
						BlaubotEthernetConnection connection = new BlaubotEthernetConnection(device, clientSocket);
						ExchangeStatesTask exchangeStatesTask = new EthernetExchangeTask(connection, currentState, blaubotEthernetAdapter.getAcceptorPort(), beaconPort, discoveryEventListener);
						exchangeStatesTask.run();
					} catch (IOException e) {
						if (Log.logWarningMessages()) {
							Log.w(LOG_TAG, "Connection to " + device + "'s beacon failed.");
						}
					}
					try {
						Thread.sleep(BEACON_PROBE_INTERVAL);
					} catch (InterruptedException e) {
						exit = true;
						break;
					}
				}
			}
		}

		/**
		 * @return true if the discovery is disabled by config or explicitly by {@link #setDiscoveryActivated}
		 */
		private boolean isDiscoveryDisabled() {
			boolean mergeKingdomsActivated = blaubotEthernetAdapter.getBlaubotAdapterConfig().isMergeKingdomsActivated();
			return !mergeKingdomsActivated || !discoveryActive;
		}
	}
	
	
	@Override
	public void onDeviceDiscovered(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
		if (discoveryEventListener != null) {
			discoveryEventListener.onDeviceDiscoveryEvent(discoveryEvent);
		}
	}

	/**
	 * 
	 * @param inetAddress
	 * @return true, if the given inetAddress belongs to on of our network interfaces - false otherwise
	 */
	private boolean isOwnInetAddr(InetAddress inetAddress) {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress curAddr = enumIpAddr.nextElement();
					if (curAddr.equals(inetAddress)) {
						return true;
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(LOG_TAG, ex.toString());
		}

		return false;
	}

	@Override
	public Thread getAcceptThread() {
		return acceptThread;
	}

	@Override
	public BlaubotEthernetAdapter getEthernetAdapter() {
		return blaubotEthernetAdapter;
	}

}
