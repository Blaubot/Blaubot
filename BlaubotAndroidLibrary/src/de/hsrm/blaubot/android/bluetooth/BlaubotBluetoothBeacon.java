package de.hsrm.blaubot.android.bluetooth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import de.hsrm.blaubot.core.BlaubotUUIDSet;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotListeningStateListener;
import de.hsrm.blaubot.core.acceptor.discovery.ExchangeStatesTask;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.message.admin.CensusMessage;
import de.hsrm.blaubot.util.Log;

/**
 * A beacon implementation for bluetooth on android.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotBluetoothBeacon implements IBlaubotBeaconInterface {
	private static final String LOG_TAG = "BlaubotBluetoothBeacon";
	private IBlaubotListeningStateListener listeningStateListener;
	private IBlaubotIncomingConnectionListener acceptorListener;
	private BluetoothServerSocket serverSocket;
	private String beaconString = "BeaconService";

	private BeaconScanner beaconScanner;
	private BeaconAcceptThread acceptThread;
	private IBlaubotDiscoveryEventListener discoveryEventListener;
	private boolean discoveryActivated;
	private IBlaubotState currentState = null;

	private UUID currentBeaconUUID;
	private BlaubotUUIDSet uuidSet;
	private BlaubotBluetoothAdapter blaubotBluetoothAdapter;

	public BlaubotBluetoothBeacon(BlaubotBluetoothAdapter blaubotBluetoothAdapter) {
		this.discoveryActivated = true;
		this.blaubotBluetoothAdapter = blaubotBluetoothAdapter;
		this.uuidSet = blaubotBluetoothAdapter.getUUIDSet();
		this.currentBeaconUUID = uuidSet.getBeaconUUID();
	}

	@Override
	public synchronized void startListening() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting to listen for beacon connections ...");
		}
		if (isStarted()) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Beacon already listening - stopping first ...");
			}
			stopListening();
		}
		try {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Beacon is starting to listen on RFCOMM with UUID " + currentBeaconUUID);
			}
			BluetoothServerSocket bss = BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord(beaconString, currentBeaconUUID);
			serverSocket = bss;
			acceptThread = new BeaconAcceptThread();
			acceptThread.start();
			if (beaconScanner == null) {
				beaconScanner = new BeaconScanner();
				beaconScanner.start();
			}
		} catch (IOException e) {
			// TODO: what to do if we fail here?!
			serverSocket = null;
			if(Log.logErrorMessages()) {
				Log.e(LOG_TAG, "Got IOException on BluetoothServerSocket creation!", e);
			}
		}
		if (listeningStateListener != null)
			listeningStateListener.onListeningStarted(this);
	}

	@Override
	public synchronized void stopListening() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Stopping to listen for beacon connections ...");
		}
		if (beaconScanner != null && beaconScanner.isAlive()) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Waiting for beacon BeaconScanner to finish ...");
			}
			beaconScanner.interrupt();
			// should kill himself
//				beaconScanner.join(); 
			beaconScanner = null;
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "BeaconScanner finished ...");
			}
		}
		if (serverSocket != null) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Closing BluetoothServerSocket ...");
			}
			try {
				serverSocket.close();
				serverSocket = null;
			} catch (IOException e) {
				if(Log.logWarningMessages()) {
					Log.w(LOG_TAG, "Got IOException during close!");
				}
			}
		}
		if (acceptThread != null && acceptThread.isAlive()) {
			try {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Waiting for beacon accept thread to finish ...");
				}
				acceptThread.interrupt();
				acceptThread.join();
				acceptThread = null;
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Beacon accept thread to finished ...");
				}
			} catch (InterruptedException e) {
				if(Log.logWarningMessages()) {
					Log.w(LOG_TAG, e);
				}
			}
		}
		if (listeningStateListener != null)
			listeningStateListener.onListeningStopped(this);

	}

	@Override
	public boolean isStarted() {
		return serverSocket != null || beaconScanner != null || acceptThread != null;
	}

	@Override
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
		this.listeningStateListener = stateListener;
	}

	@Override
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
		this.acceptorListener = acceptorListener;
	}

	@Override
	public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
		this.discoveryEventListener = discoveryEventListener;
	}

	/**
	 * Fulfills the {@link IBlaubotConnectionAcceptor} specification for this beacon. Simply accepts the incoming beacon
	 * connections, wraps them into the generalized {@link IBlaubotConnection} interface and hand them to the
	 * {@link IBlaubotIncomingConnectionListener} for further handling of the state information exchange.
	 * 
	 * TODO: close serversocket!
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	class BeaconAcceptThread extends Thread {
		private final String LOG_TAG = "BeaconAcceptThread";

		@Override
		public void interrupt() {
			super.interrupt();
		}

		@Override
		public void run() {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "BeaconAcceptThread started ...");
			}
			BluetoothServerSocket serverSocket = BlaubotBluetoothBeacon.this.serverSocket;
			while (!isInterrupted() && serverSocket != null) { // this is busy wait (for 3 to 5 iterations) - I can live
																// with that.
				BluetoothSocket bluetoothSocket = null;
				try {
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Waiting for incoming beacon connections ...");
					}
					bluetoothSocket = serverSocket.accept();
				} catch (IOException e) {
					if(Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Beacon communication failed with I/O Exception", e);
					}
					continue;
				}
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got a new beacon connection from " + bluetoothSocket.getRemoteDevice());
				}
				BlaubotBluetoothDevice bluetoothDevice = new BlaubotBluetoothDevice(bluetoothSocket.getRemoteDevice(), blaubotBluetoothAdapter);
				IBlaubotConnection connection = new BlaubotBluetoothConnection(bluetoothDevice, bluetoothSocket);
				if (acceptorListener != null) {
					acceptorListener.onConnectionEstablished(connection);
				} else {
					if(Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Got a beacon connection but no acceptor listener was there to handle it!");
					}
					connection.disconnect();
				}
			}
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "BeaconAcceptThread finished ...");
			}
		}
	}

	/**
	 * Iterates continuously over the paired devices and exchanges state informations over the beacon interface by
	 * trying to establish a connection to the device. If successful, the discovered state is propagated via the
	 * beacon's handleDiscoveredBlaubotDevice(..) method.
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	class BeaconScanner extends Thread {
		private static final String LOG_TAG = "BeaconScanner";
		private static final int MIN_WAIT_BETWEEN_BEACON_PROBES = 2000; // ms
		private static final int MAX_WAIT_BETWEEN_BEACON_PROBES = 5000; // ms

		@Override
		public void interrupt() {
			super.interrupt();
		}

		/**
		 * Creates the list of devices to be scanned.
		 * 
		 * @return 
		 */
		private ArrayList<BlaubotBluetoothDevice> getDevicesToScan() {
			// We only check devices that are bonded and not already connected to our network to minimize
			// the expensive bluetooth sdp lookup and connectivity traffic.
			Set<BluetoothDevice> bondedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
			Set<String> blaubotNetworkDevices = new HashSet<String>();
			CensusMessage lastCensusMessage = blaubotBluetoothAdapter.getBlaubot().getConnectionStateMachine().getStateMachineSession().getLastCensusMessage();
			if(lastCensusMessage != null) {
				blaubotNetworkDevices.addAll(lastCensusMessage.getDeviceStates().keySet());
			}
			ArrayList<BlaubotBluetoothDevice> devicesToScan = new ArrayList<BlaubotBluetoothDevice>();
			for (BluetoothDevice d : bondedDevices) {
				BlaubotBluetoothDevice bbd = new BlaubotBluetoothDevice(d, blaubotBluetoothAdapter);
				// filter connected devices
				if(!blaubotNetworkDevices.contains(bbd.getUniqueDeviceID())) {
					devicesToScan.add(bbd);
				}
			}
			// After filtering we sort the devices by descending by their unique id
			Collections.sort(devicesToScan);
			Collections.reverse(devicesToScan); 
			return devicesToScan;
		}
		
		@Override
		public void run() {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "BeaconScanner started ...");
			}
			boolean interrupted = false; // attention: we use sleep inside the loop so isInterrupted() will fail if we
											// end up in the catch-block (flag cleared)
			Random random = new Random(System.currentTimeMillis());
			while (!this.isInterrupted() && !interrupted && beaconScanner == Thread.currentThread()) {
				if (isDiscoveryDisabled()) {
					try {
						Thread.sleep(500); // TODO: busy wait, not good
					} catch (InterruptedException e) {
						break;
					}
				}
				
				ArrayList<BlaubotBluetoothDevice> devicesToScan = getDevicesToScan();
				if(!(this.isInterrupted() || interrupted || isDiscoveryDisabled())) {
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Probing " + (devicesToScan.size()) + " bonded devices on their beacon interfaces");
					}
				} 
				for (BlaubotBluetoothDevice device : devicesToScan) {
					if (this.isInterrupted() || interrupted || isDiscoveryDisabled())
						break;
					if (blaubotBluetoothAdapter.notAvailableDevices.contains(device)) {
						if(Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Skipping known dead device " + device);
						}
						continue; // skip dead devices
					}
					BluetoothSocket socket = null;
					
					try {
						if(Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Connecting to beacon of " + device + " ... ");
						}
						socket = device.createRfcommSocketToServiceRecord(currentBeaconUUID);
						if(!blaubotBluetoothAdapter.notAvailableDevices.contains(device)) {
							socket.connect();
						};
					} catch (IOException e) {
						socket = null;
						if(Log.logWarningMessages()) {
							Log.w(LOG_TAG, "Could not connect to bluetooth beacon of device " + device);
						}
					}
					if(socket != null) {
						IBlaubotConnection connection = new BlaubotBluetoothConnection(device, socket);
						ExchangeStatesTask exchangeStatesTask = new ExchangeStatesTask(connection, currentState, discoveryEventListener);
						exchangeStatesTask.run();
					}

					try {
						long time = random.nextInt(MAX_WAIT_BETWEEN_BEACON_PROBES - MIN_WAIT_BETWEEN_BEACON_PROBES + 1) + MIN_WAIT_BETWEEN_BEACON_PROBES;
						if(Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Letting the bluetooth adapter breathe for " + time + " ms");
						}
						Thread.sleep(time);
					} catch (InterruptedException e) {
						if(Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Interrupted while breathing.");
						}
						interrupted = true;
						break;
					}
				}
			}
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "BeaconScanner finished ...");
			}
		}
		
		/**
		 * @return true if the discovery is disabled by config or explicitly
		 */
		private boolean isDiscoveryDisabled() {
			boolean mergeKingdomsActivated = blaubotBluetoothAdapter.getBlaubotAdapterConfig().isMergeKingdomsActivated();
			return !mergeKingdomsActivated || !discoveryActivated;
		}

	}

	@Override
	public void onConnectionStateMachineStateChanged(IBlaubotState state) {
		currentState = state;
	}

	@Override
	public void setDiscoveryActivated(boolean active) {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Discovery was set to " + (active ? "active" : "inactive"));
		}
		this.discoveryActivated = active;
	}

	@Override
	public void onDeviceDiscovered(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
		if(discoveryEventListener != null) {
			discoveryEventListener.onDeviceDiscoveryEvent(discoveryEvent);
		}
	}

}
