package eu.hgross.blaubot.ethernet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import eu.hgross.blaubot.core.acceptor.discovery.ExchangeStatesTask;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.acceptor.discovery.TimeoutList;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.util.KingdomCensusLifecycleListener;
import eu.hgross.blaubot.util.Log;

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
public class BlaubotEthernetMulticastBeacon implements IBlaubotBeacon, IEthernetBeacon {
	private static final String LOG_TAG = "BlaubotEthernetMulticastBeacon";
	private static final int BROADCASTER_INTERVAL = 6500;
	/**
	 * Probe interval if in FreeState
	 */
	private static final long BEACON_PROBE_INTERVAL_AGGRESSIVE = 1000;
	/**
	 * PROBE-INTERVAL if not in FreeState
	 */
	private static final long BEACON_PROBE_INTERVAL_DECENT = 5000;
	private static final int ALIVE_TIMEOUT = BROADCASTER_INTERVAL * 5;
	private final int beaconPort;
	private final int beaconBroadcastPort;
	private UUID beaconUUID;
    private IBlaubotDevice ownDevice;

    private volatile IBlaubotState currentState;
	private volatile IBlaubotDiscoveryEventListener discoveryEventListener;
	private volatile IBlaubotIncomingConnectionListener incomingConnectionListener;
	private volatile IBlaubotListeningStateListener listeningStateListener;
	private volatile boolean discoveryActive = true;
	/**
	 * Contains all devices which sent us a broadcast in between the last x seconds.
	 */
	private final TimeoutList<IBlaubotDevice> knownActiveDevices;

	private volatile EthernetBeaconAcceptThread acceptThread;
	private volatile BroadcasterThread broadcaster;
	private volatile BroadcastDiscovererThread broadcastDiscoverer;
	private volatile EthernetBeaconScanner beaconScanner;
	private Object startStopMonitor;
    private IBlaubotBeaconStore beaconStore;
    private Blaubot blaubot;
    private KingdomCensusLifecycleListener kingdomCensusLifecycleListener;

	/**
	 * @param beaconPort the port to accept beacon connections
	 * @param beaconBroadcastPort the broadcast port for announcements
	 */
    public BlaubotEthernetMulticastBeacon(int beaconPort, int beaconBroadcastPort) {
		this.startStopMonitor = new Object();
		this.beaconPort = beaconPort;
		this.beaconBroadcastPort = beaconBroadcastPort;
		this.knownActiveDevices = new TimeoutList<>((long) ALIVE_TIMEOUT);
	}

    @Override
    public IBlaubotAdapter getAdapter() {
        return null;
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
    public ConnectionMetaDataDTO getConnectionMetaData() {
        // TODO: maybe beacons should not derive from acceptors anymore
        return null;
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
	private byte[] broadcastMessage;

	private byte[] createBroadcastMessage() {
		byte[] uuidBytes = beaconUUID.toString().getBytes(BlaubotConstants.STRING_CHARSET);
		// -- UUIDs have fixed lengths
        final String ownDeviceUniqueDeviceID = ownDevice.getUniqueDeviceID();
        ByteBuffer bb = ByteBuffer.allocate(uuidBytes.length + 2 * 4 + ownDeviceUniqueDeviceID.length()); // app uuid, integer for beacon, integer for uniqueDeviceId length, and uniqueDeviceId
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.put(uuidBytes);
		bb.putInt(beaconPort);
        bb.putInt(ownDeviceUniqueDeviceID.length());
        bb.put(ownDeviceUniqueDeviceID.getBytes(BlaubotConstants.STRING_CHARSET));
		bb.flip();
		return bb.array();
	}

    @Override
    public Thread getAcceptThread() {
        return acceptThread;
    }

    @Override
    public int getBeaconPort() {
        return beaconPort;
    }

    /**
     * Used to store some beacon related data for the discoverer and scanner.
     */
    private class MulticastBeaconBlaubotDevice extends BlaubotDevice {
        private final InetAddress inetAddress;
        private final int beaconPort;

        private MulticastBeaconBlaubotDevice(String uniqueId, InetAddress inetAddress, int beaconPort) {
            super(uniqueId);
            this.inetAddress = inetAddress;
            this.beaconPort = beaconPort;
        }

        private int getBeaconPort() {
            return beaconPort;
        }

        private InetAddress getInetAddress() {
            return inetAddress;
        }
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
							Log.w(LOG_TAG, "Failed to get network information", e1);
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
//					if (isOwnInetAddr(packet.getAddress())) {
						// ignore inet addresses that belong to one of our own interfaces!
//						continue;
//					}
				} catch (SocketTimeoutException e) {
					// no connection for BROADCAST_DISCOVERER_SOCKET_TIMEOUT ms
					continue;
				} catch (IOException e) {
					if (Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Receive failed.", e);
					}
					continue;
				}

                // wrap the data
                final ByteBuffer bb = ByteBuffer.wrap(packet.getData());
                bb.order(BlaubotConstants.BYTE_ORDER);

                // read uuid
                byte[] uuidBytes = new byte[beaconUUID.toString().length()];
                bb.get(uuidBytes, 0, uuidBytes.length);
                String uuidStr = new String(uuidBytes, BlaubotConstants.STRING_CHARSET);
                UUID receivedUUID = UUID.fromString(uuidStr);

                // get the beacon port
                int beaconPort = bb.getInt();
                // get the uniqueDeviceId length
                int uniqueDeviceIdLength = bb.getInt();
                // get the uniqueDeviceId bytes
                byte[] uniqueDeviceIdBytes = new byte[uniqueDeviceIdLength];
                bb.get(uniqueDeviceIdBytes);
                String uniqueDeviceId = new String(uniqueDeviceIdBytes, BlaubotConstants.STRING_CHARSET);

				final boolean isFromOwnUniqueDeviceId = uniqueDeviceId.equals(ownDevice.getUniqueDeviceID());

				try {
					final boolean relevantBeaconUUID = receivedUUID.equals(beaconUUID);
					if (!isFromOwnUniqueDeviceId && relevantBeaconUUID) {
						InetAddress remoteDeviceAddr = packet.getAddress(); // only used for the beacon internally
						IBlaubotDevice device = new MulticastBeaconBlaubotDevice(uniqueDeviceId, remoteDeviceAddr, beaconPort);
						if (Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Received a relevant beaconUUID (" + receivedUUID.toString() + " == " + beaconUUID.toString() + ") via broadcast from " + device + ". Reporting it as active device.");
						}
						knownActiveDevices.report(device);
					} else if(!relevantBeaconUUID) {
						if (Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Received an irrelevant beaconUUID (" + receivedUUID.toString() + " != " + beaconUUID.toString() + ") via broadcast. Ignoring device.");
						}
					}
				} catch (IllegalArgumentException e) {
					if (Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Received String is not a valid UUID: " + uuidStr, e);
					}
				}
			}
			receivingSocket.close();
		}

	}

	/**
	 * Periodically checks the beacon of all devices known as alive (except devices connected to our network) (added to the {@link TimeoutList})
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 *
	 */
	class EthernetBeaconScanner extends Thread {
		private static final int BEACON_SCANNER_CONNECT_TIMEOUT = 10000;
		private String LOG_TAG = "EthernetBeaconScanner";

		private List<IBlaubotDevice> getAliveDevices() {
			ArrayList<IBlaubotDevice> devices = new ArrayList<IBlaubotDevice>(knownActiveDevices.getItems());
			// do not check the devices connected to the blaubot network
            devices.removeAll(kingdomCensusLifecycleListener.getDevices());
			// note: we do not sort the collection since ethernet connection creation is beaming fast.
            return devices;
		}
		
		@Override
		public void run() {
			boolean exit = false;
			while(!isInterrupted() && Thread.currentThread() == beaconScanner && !exit) {
				if (isDiscoveryDisabled()) {
                    // Discovery is not active - not connecting to discovered beacon.
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						exit = true;
					}
					// we don't want to connect if discovery is deactivated.
					continue;
				}
				for(IBlaubotDevice d : getAliveDevices()) {
                    if (isDiscoveryDisabled()) {
                        break;
                    }
                    MulticastBeaconBlaubotDevice device = (MulticastBeaconBlaubotDevice) d;
                    InetAddress remoteDeviceAddr = device.getInetAddress();
                    int remoteBeaconPort = device.getBeaconPort();
                    // -- we know that remoteDeviceAddr had a running beacon in the recent past as it is in the knownActiveDevices TimeoutList
                    // try to connect, then exchange states via tcp/ip
                    Socket clientSocket;
                    try {
                        clientSocket = new Socket();
                        clientSocket.connect(new InetSocketAddress(remoteDeviceAddr, remoteBeaconPort), BEACON_SCANNER_CONNECT_TIMEOUT);
                        BlaubotEthernetUtils.sendOwnUniqueIdThroughSocket(ownDevice, clientSocket);
                        BlaubotEthernetConnection connection = new BlaubotEthernetConnection(device, clientSocket);
                        final List<ConnectionMetaDataDTO> ownAcceptorsMetaDataList = BlaubotAdapterHelper.getConnectionMetaDataList(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
                        ExchangeStatesTask exchangeStatesTask = new ExchangeStatesTask(ownDevice, connection, currentState, ownAcceptorsMetaDataList, beaconStore, discoveryEventListener);
                        exchangeStatesTask.run();
                    } catch (IOException e) {
                        if (Log.logWarningMessages()) {
                            Log.w(LOG_TAG, "Connection to " + device + "'s beacon (" + remoteDeviceAddr + ":" + remoteBeaconPort + ") failed: " + e.getMessage());
                        }
                    }
                    try {
                        // if we are in free state, be a little more decent with the interval
                        final long sleepTime = currentState != null && !(currentState instanceof FreeState) ? BEACON_PROBE_INTERVAL_DECENT : BEACON_PROBE_INTERVAL_AGGRESSIVE;
                        Thread.sleep(sleepTime);
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
			return !discoveryActive;
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
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
        this.ownDevice = blaubot.getOwnDevice();
        this.beaconUUID = blaubot.getUuidSet().getBeaconUUID();
        this.broadcastMessage = createBroadcastMessage();
        this.kingdomCensusLifecycleListener = new KingdomCensusLifecycleListener(ownDevice);
        this.blaubot.addLifecycleListener(this.kingdomCensusLifecycleListener);
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }
}
