package de.hsrm.blaubot.android.wifip2p;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import de.hsrm.blaubot.android.wifip2p.BlaubotWifiP2PBroadcastReceiver.IBlaubotWifiDirectEventListener;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotListeningStateListener;
import de.hsrm.blaubot.core.acceptor.discovery.BeaconMessage;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import de.hsrm.blaubot.core.acceptor.discovery.TimeoutList;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.message.admin.CensusMessage;
import de.hsrm.blaubot.util.Log;

/**
 * We are able to signal some defined strings ({@link http://upnp.org/specs/dm/UPnP-dm-BasicManagement-v1-Service.pdf}) 
 * over the upnp framework, so we pick one of them to indicate that there is a blaubot instance running.
 * 
 * The beacons then search for this upnp string/service and connect to them to probe if this devices
 * are really blaubot instances. If we find a blaubot instance, they exchange the beaconUUID, and if their beaconIds match
 * the usual {@link BeaconMessage} to exchange their states.
 * 
 * Sadly we can not communicate additional attributes via upnp service requests (which we should be according to the upnp standard) 
 * on android so we have to actively ask for this attributes (beaconId, state) by connecting to the device.
 * see: https://code.google.com/p/android/issues/detail?id=40003
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotWifiP2PBeacon implements IBlaubotBeaconInterface {
	private static final String LOG_TAG = "BlaubotWifiP2PBeacon";
	private static final String BONJOUR_INSTANCE_NAME = "Blaubot";
	private static final String BONJOUR_SERVICE_TYPE = "_tcp";
	private final BlaubotWifiP2PAdapter adapter;
	private IBlaubotListeningStateListener listeningStateListener;
	private IBlaubotIncomingConnectionListener incomingConnectionListener;
	private IBlaubotDiscoveryEventListener discoveryEventListener;
	private State currentState;
	private volatile boolean isStarted;
	private volatile boolean discoveryActivated;
	private UUID ourDeviceUUID; // random uuid to indetify this beacon TODO: this uuid should be persistent for one device!
	private TimeoutList<BlaubotWifiP2PDevice> knownActiveDevices;
	private WifiP2PBeaconScanner beaconScanner;
	private BonjourListener bonjourListener;
	/**
	 * The string that we exploit to pre-filter upnp devices before we probe them if they are really a 
	 * blaubot beacon.
	 */
	
	protected BlaubotWifiP2PBeacon(BlaubotWifiP2PAdapter blaubotWifiP2PAdapter) {
		this.ourDeviceUUID = UUID.randomUUID();
		this.knownActiveDevices = new TimeoutList<BlaubotWifiP2PDevice>(60000);
		this.adapter = blaubotWifiP2PAdapter;
		this.adapter.getBlaubotWifiP2PBroadcastReceiver().addEventListener(wifiDirectEventListener);
		this.bonjourListener = new BonjourListener();
		this.adapter.getWifiP2pManager().setDnsSdResponseListeners(this.adapter.getBeaconWifiChannel(), bonjourListener, bonjourListener);
	}
	
	class BonjourListener implements DnsSdServiceResponseListener, DnsSdTxtRecordListener {
		private static final String TXT_RECORD_KEY_STATE = "state";
		private static final String TXT_RECORD_KEY_BEACON_PORT = "beaconPort";
		private static final String TXT_RECORD_KEY_ACCEPTOR_PORT = "acceptorPort";
		
		
		@Override
		public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
			Log.d(LOG_TAG, "onDnsSdTxtRecordAvailable(" + fullDomainName + ", " + txtRecordMap + ", " + srcDevice +")");
			State state = null;
			for(Entry<String, String> txtRecord : txtRecordMap.entrySet()) {
				if(txtRecord.getKey().equals(TXT_RECORD_KEY_STATE)) {
					state = State.valueOf(txtRecord.getValue());
				}
			}
			if(state == null) {
				return;
			}
			IBlaubotDevice device = new BlaubotWifiP2PDevice(adapter, srcDevice);
			AbstractBlaubotDeviceDiscoveryEvent discoveryEvent = state.createDiscoveryEventForDevice(device);

			if (discoveryEventListener != null)
				discoveryEventListener.onDeviceDiscoveryEvent(discoveryEvent);
		}

		@Override
		public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {
			Log.d(LOG_TAG, "onDnsSdServiceAvailable(" + instanceName + ", " + registrationType + ", " + srcDevice +")");			
		}
		
	}
	
	
	private IBlaubotWifiDirectEventListener wifiDirectEventListener = new IBlaubotWifiDirectEventListener() {
		@Override
		public void onP2PWifiEnabled() {
			
		}
		
		@Override
		public void onP2PWifiDisabled() {
			
		}
		
		@Override
		public void onListOfPeersChanged(WifiP2pDeviceList deviceList) {
			
		}
		
		@Override
		public void onDiscoveryStopped() {
			
		}
		
		@Override
		public void onDiscoveryStarted() {
			System.out.println("Stopppped");
			
		}
		
		@Override
		public void onConnectivityChanged(WifiP2pInfo p2pInfo, NetworkInfo networkInfo) {
			
		}
	};

	@Override
	public synchronized void startListening() {
		if(isStarted) {
			stopListening();
		}
		// TODO: start discovery and listening
		Log.d(LOG_TAG, "Starting WifiP2PBeaconScanner ...");
		beaconScanner = new WifiP2PBeaconScanner();
		beaconScanner.start();
		
		Log.d(LOG_TAG, "Setting up search for beacon services ...");
		createServiceRequestsAndStartDiscovery();

		isStarted = true;
		if(listeningStateListener != null) {
			listeningStateListener.onListeningStarted(this);
		}
	}

	@Override
	public synchronized void stopListening() {
		if(!isStarted()) {
			return;
		}
		// TODO: stop discovery and listening
		if (beaconScanner != null && beaconScanner.isAlive()) {
			Log.d(LOG_TAG, "Stopping WifiP2PBeaconScanner ...");
			beaconScanner.interrupt();
		}
		
		adapter.getWifiP2pManager().clearLocalServices(adapter.getBeaconWifiChannel(), null); 
		
		isStarted = false;
		if(listeningStateListener != null) {
			listeningStateListener.onListeningStopped(this);
		}
	}

	@Override
	public boolean isStarted() {
		return this.isStarted;
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

	private void createServiceRequestsAndStartDiscovery() {
		final WifiP2pManager mManager = adapter.getWifiP2pManager();
		final Channel mBeaconChannel = adapter.getBeaconWifiChannel();
		mManager.clearServiceRequests(mBeaconChannel, new ActionListener() {
			@Override
			public void onSuccess() {
				// bonjour
				final WifiP2pDnsSdServiceRequest bonjourSearchRequest = WifiP2pDnsSdServiceRequest.newInstance(BONJOUR_INSTANCE_NAME, BONJOUR_SERVICE_TYPE);
				mManager.addServiceRequest(mBeaconChannel, bonjourSearchRequest, new ActionListener() {
					
					@Override
					public void onSuccess() {
						Log.d(LOG_TAG, "Bonjour service search request added: " + bonjourSearchRequest);
						try {
							// android hardware .... 
							Thread.sleep(50);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						mManager.discoverServices(mBeaconChannel, new ActionListener() {
							@Override
							public void onSuccess() {
								Log.d(LOG_TAG, "Bonjour service discovery started");
							}
							
							@Override
							public void onFailure(int reason) {
								Log.d(LOG_TAG, "Failed to start Bonjour service discovery, reason: " + reason);
							}
						});
					}
					
					@Override
					public void onFailure(int reason) {
						Log.d(LOG_TAG, "Failed to add bonjour service search request: " + bonjourSearchRequest);
						Log.d(LOG_TAG, "Reason: " + reason);
					}
				});
				
			}
			
			@Override
			public void onFailure(int reason) {
				Log.w(LOG_TAG, "Failed to clear service requests");
			}
		});
	}
	
	@Override
	public void onConnectionStateMachineStateChanged(IBlaubotState state) {
		this.currentState = State.getStateByStatemachineClass(state.getClass());
		final Channel channel = adapter.getBeaconWifiChannel();
		final String stateName = currentState.name();
		adapter.getWifiP2pManager().clearLocalServices(channel, new ActionListener() {
			
			@Override
			public void onSuccess() {
				Log.d(LOG_TAG, "Success");
				// Add the bonjour local service
				Map<String, String> txtRecordsMap = new HashMap<String, String>();
//				txtRecordsMap.put("beaconUUID", adapter.getUuidSet().getBeaconUUID().toString());
				txtRecordsMap.put("acceptorPort", "17171");
				txtRecordsMap.put("beaconPort", "17172");
				txtRecordsMap.put("state", stateName);
				WifiP2pServiceInfo bonjourServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(BONJOUR_INSTANCE_NAME, BONJOUR_SERVICE_TYPE, txtRecordsMap );
				adapter.getWifiP2pManager().addLocalService(channel, bonjourServiceInfo, new ActionListener() {

					@Override
					public void onSuccess() {
						Log.d(LOG_TAG, "Successfully added service " + stateName);
					}

					@Override
					public void onFailure(int reason) {
						Log.d(LOG_TAG, "Adding Service failure:" + reason);
					}
					
				});
			}
			
			@Override
			public void onFailure(int reason) {
				Log.d(LOG_TAG, "failure:" + reason);
			}
		});
	}

	@Override
	public void setDiscoveryActivated(boolean active) {
		this.discoveryActivated = active;
	}

	@Override
	public void onDeviceDiscovered(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
		if(discoveryEventListener != null) {
			discoveryEventListener.onDeviceDiscoveryEvent(discoveryEvent);
		}
	}
	
	class WifiP2PBeaconScanner extends Thread {
		public static final long PER_DEVICE_TIMEOUT = 1500; 
		
		/**
		 * Creates the list of devices to be scanned.
		 * 
		 * @return 
		 */
		private ArrayList<BlaubotWifiP2PDevice> getDevicesToScan() {
			// We only check devices that are bonded and not already connected to our network to minimize
			// the expensive bluetooth sdp lookup and connectivity traffic.
			Set<String> blaubotNetworkDevices = new HashSet<String>();
			CensusMessage lastCensusMessage = adapter.getBlaubot().getConnectionStateMachine().getStateMachineSession().getLastCensusMessage();
			if(lastCensusMessage != null) {
				blaubotNetworkDevices.addAll(lastCensusMessage.getDeviceStates().keySet());
			}
			ArrayList<BlaubotWifiP2PDevice> devicesToScan = new ArrayList<BlaubotWifiP2PDevice>();
			for (BlaubotWifiP2PDevice d : knownActiveDevices.getItems()) {
				// filter connected devices
				if(!blaubotNetworkDevices.contains(d.getUniqueDeviceID())) {
					devicesToScan.add(d);
				}
			}
			// After filtering we sort the devices by descending by their unique id
			Collections.sort(devicesToScan);
			Collections.reverse(devicesToScan); 
			return devicesToScan;
		}
		
		@Override
		public void run() {
			boolean interrupted = false;
			while(!isInterrupted() && beaconScanner == Thread.currentThread() && !interrupted) {
				System.out.println("scanning " + getDevicesToScan());
				for(BlaubotWifiP2PDevice device : getDevicesToScan()) {
					System.out.println("todo: connect to " + device);
					WifiP2PUtils.printARP();
					// connect to device
					// TODO: connect to socket -> which ip? from where?
//					Socket socket = 
//					IBlaubotConnection conn = BlaubotWifiP2PConnection.fromSocket(device, clientSocket, adapter.getUuidSet());
					
					try {
						Thread.sleep(PER_DEVICE_TIMEOUT);
					} catch (InterruptedException e) {
						interrupted = true;
						break;
					}
				}
				try {
					Thread.sleep(PER_DEVICE_TIMEOUT);
				} catch (InterruptedException e) {
					interrupted = true;
					break;
				}
			}
			Log.d(LOG_TAG, "BeaconScanner finished.");
		}
	}
	
	
}
