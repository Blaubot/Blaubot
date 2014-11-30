package de.hsrm.blaubot.ethernet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
public class BlaubotEthernetFixedDeviceSetBeacon implements IBlaubotBeaconInterface, IEthernetBeacon {
	private static final String LOG_TAG = "BlaubotEthernetFixedDeviceSetBeacon";
	private static final long BEACON_PROBE_INTERVAL = 200;

	private final BlaubotEthernetAdapter blaubotEthernetAdapter;
	private final int beaconPort;

	private volatile IBlaubotState currentState;
	private volatile IBlaubotDiscoveryEventListener discoveryEventListener;
	private volatile IBlaubotIncomingConnectionListener incomingConnectionListener;
	private volatile IBlaubotListeningStateListener listeningStateListener;
	private volatile boolean discoveryActive = true;
	/**
	 * Contains all devices which sent us a broadcast in between the last x seconds.
	 */
	private final Set<IBlaubotDevice> fixedDeviceSet;

	private EthernetBeaconAcceptThread acceptThread;
	private EthernetBeaconScanner beaconScanner;
	private Object startStopMonitor;

	public BlaubotEthernetFixedDeviceSetBeacon(BlaubotEthernetAdapter blaubotEthernetAdapter, Set<IBlaubotDevice> fixedDeviceSet, int beaconPort) {
		this.beaconPort = beaconPort;
		this.blaubotEthernetAdapter = blaubotEthernetAdapter;
		this.fixedDeviceSet = fixedDeviceSet;
		this.startStopMonitor = new Object();
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
	 * Periodically checks all devies known as alive (added to the {@link TimeoutList} 
	 * TODO: generalize
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 *
	 */
	class EthernetBeaconScanner extends Thread {
		
		private List<IBlaubotDevice> getAliveDevices() {
			ArrayList<IBlaubotDevice> devices = new ArrayList<IBlaubotDevice>(fixedDeviceSet);
			// do not check the devices connected to the blaubot network
			devices.removeAll(blaubotEthernetAdapter.getBlaubot().getConnectionManager().getConnectedDevices());
			Collections.sort(devices);
			Collections.reverse(devices);
			return devices;
		}
		
		@Override
		public void run() {
			while(!isInterrupted() && Thread.currentThread() == beaconScanner) {
				if (isDiscoveryDisabled()) {
//					Log.d(LOG_TAG, "Discovery is not active - not connecting to discovered beacon.");
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						break;
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
						ExchangeStatesTask exchangeStatesTask = new EthernetExchangeTask(connection, currentState, blaubotEthernetAdapter.getAcceptorPort(), blaubotEthernetAdapter.getBeaconPort(), discoveryEventListener);
						exchangeStatesTask.run();
					} catch (IOException e) {
						if(Log.logWarningMessages()) {
							Log.w(LOG_TAG, "Connection to " + device + "'s beacon failed.");
						}
					}
					
					// sleep 
					try {
						Thread.sleep(BEACON_PROBE_INTERVAL);
					} catch (InterruptedException e) {
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

	@Override
	public Thread getAcceptThread() {
		return acceptThread;
	}


	@Override
	public BlaubotEthernetAdapter getEthernetAdapter() {
		return blaubotEthernetAdapter;
	}
	
}