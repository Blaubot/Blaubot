package eu.hgross.blaubot.ethernet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
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
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.util.Log;

/**
 * TODO: not using the uuid set
 *
 * Beacon for ethernet using a fixed set of {@link IBlaubotDevice}s. It mainly consists of the accept thread and a
 * thread iterating through the given set of devices and trying to connect to them using the {@link ExchangeStatesTask}.
 * 
 * On a successful connection the resulting {@link IBlaubotConnection} is handed to the registered 
 * {@link IBlaubotIncomingConnectionListener}. From here the {@link BlaubotBeaconService} will handle the beacon conversation
 * via the {@link ExchangeStatesTask} (exchanging {@link BeaconMessage}s). 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotEthernetFixedDeviceSetBeacon implements IBlaubotBeacon, IEthernetBeacon {
	private static final String LOG_TAG = "BlaubotEthernetFixedDeviceSetBeacon";
	private static final long BEACON_PROBE_INTERVAL = 200;

	private final int beaconPort;
    private IBlaubotDevice ownDevice;

    private volatile IBlaubotState currentState;
	private volatile IBlaubotDiscoveryEventListener discoveryEventListener;
	private volatile IBlaubotIncomingConnectionListener incomingConnectionListener;
	private volatile IBlaubotListeningStateListener listeningStateListener;
	private volatile boolean discoveryActive = true;
	/**
	 * Contains all devices which sent us a broadcast in between the last x seconds.
	 */
	private final Set<FixedDeviceSetBlaubotDevice> fixedDeviceSet;

	private volatile EthernetBeaconAcceptThread acceptThread;
	private volatile EthernetBeaconScanner beaconScanner;
	private Object startStopMonitor;
    private IBlaubotBeaconStore beaconStore;
    private Blaubot blaubot;
    private BlaubotUUIDSet uuidSet;

    public BlaubotEthernetFixedDeviceSetBeacon(Set<FixedDeviceSetBlaubotDevice> fixedDeviceSet, int beaconPort) {
		this.beaconPort = beaconPort;
		this.fixedDeviceSet = fixedDeviceSet;
		this.startStopMonitor = new Object();
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
			beaconScanner = new EthernetBeaconScanner();
			
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Beacon is starting to listen for incoming connections on port " + beaconPort);
			}
			acceptThread.start();
			if(Log.logDebugMessages()) {
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
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Interrupting and waiting for beaconScanner to stop ...");
				}
				beaconScanner.interrupt();
				// We don't join the beacon scanner anymore: The scanner has a worst case blocking time of the underlying socket's timeout
				// and he will kill himself anyways so we let him hang himself in the background.
				//			try {
//				System.out.println("");
//				beaconScanner.join();
				
//			} catch (InterruptedException e) {
//				throw new RuntimeException(e);
//			}
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "BeaconScanner stopped ...");
				}
			}
			beaconScanner = null;
			
			
			if (acceptThread != null && acceptThread.isAlive()) {
				try {
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Waiting for beacon accept thread to finish ...");
					}
					acceptThread.interrupt();
					acceptThread.join();
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Beacon accept thread finished.");
					}
				} catch (InterruptedException e) {
					if(Log.logWarningMessages()) {
						Log.w(LOG_TAG, e);
					}
				}
			}
			acceptThread = null;
			
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
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
        this.ownDevice = blaubot.getOwnDevice();
        this.uuidSet = blaubot.getUuidSet();
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
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
	 * Periodically checks all devices known as alive (added to the {@link TimeoutList}
	 * TODO: generalize
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 *
	 */
	class EthernetBeaconScanner extends Thread {
		
		private List<IBlaubotDevice> getAliveDevices() {
			ArrayList<IBlaubotDevice> devices = new ArrayList<IBlaubotDevice>(fixedDeviceSet);
			// do not check the devices connected to the blaubot network
			devices.removeAll(blaubot.getConnectionManager().getConnectedDevices());
            devices.remove(ownDevice);
			Collections.sort(devices);
			Collections.reverse(devices);
            return devices;
		}
		
		@Override
		public void run() {
            boolean exit = false;
			while(!isInterrupted() && Thread.currentThread() == beaconScanner && !exit) {
				if (isDiscoveryDisabled()) {
//					Log.d(LOG_TAG, "Discovery is not active - not connecting to discovered beacon.");
					try {
						Thread.sleep(300);
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

					FixedDeviceSetBlaubotDevice device = (FixedDeviceSetBlaubotDevice) d;
					InetAddress remoteDeviceAddr = device.getInetAddress();
					final int remoteBeaconPort = device.getBeaconPort();
					// -- we know that remoteDeviceAddr had a running beacon in the recent past as it is in the knownActiveDevices TimeoutList
					// try to connect, then exchange states via tcp/ip
					Socket clientSocket;

					try {
						clientSocket = new Socket(remoteDeviceAddr, remoteBeaconPort);
						BlaubotEthernetUtils.sendOwnUniqueIdThroughSocket(ownDevice, clientSocket);
						BlaubotEthernetConnection connection = new BlaubotEthernetConnection(device, clientSocket);
						final List<ConnectionMetaDataDTO> ownAcceptorsMetaDataList = BlaubotAdapterHelper.getConnectionMetaDataList(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
						ExchangeStatesTask exchangeStatesTask = new ExchangeStatesTask(ownDevice, connection, currentState, ownAcceptorsMetaDataList, beaconStore, discoveryEventListener);
						exchangeStatesTask.run();
					} catch (IOException e) {
						if (Log.logWarningMessages()) {
							Log.w(LOG_TAG, "Connection to " + device + "'s beacon failed ( " + e.getMessage() + ").");
						}
					}

					// sleep
					try {
						Thread.sleep(BEACON_PROBE_INTERVAL);
					} catch (InterruptedException e) {
						exit = true;
						break;
					}
				}

            }
			
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "BeaconScanner finished.");
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
     * A dedicated BlaubotDevice class to be used with the fixed device set beacon implementation.
     * It needs to know the beacon ports and ip of the other devices to receive data (acceptor ports and stuff) from there.
     */
    public static class FixedDeviceSetBlaubotDevice extends BlaubotDevice {
        private final int beaconPort;
        private final InetAddress inetAddress;

        public FixedDeviceSetBlaubotDevice(String uniqueDeviceId, InetAddress inetAddress, int beaconPort) {
            super(uniqueDeviceId);
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
	
	
	@Override
	public Thread getAcceptThread() {
		return acceptThread;
	}

    @Override
    public int getBeaconPort() {
        return beaconPort;
    }

}