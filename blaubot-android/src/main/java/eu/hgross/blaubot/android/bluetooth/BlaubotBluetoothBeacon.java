package eu.hgross.blaubot.android.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.UniqueDeviceIdHelper;
import eu.hgross.blaubot.core.acceptor.discovery.ExchangeStatesTask;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.acceptor.discovery.TimeoutList;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.util.KingdomCensusLifecycleListener;
import eu.hgross.blaubot.util.Log;

/**
 * A beacon implementation for bluetooth on android.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlaubotBluetoothBeacon implements IBlaubotBeacon {
	private static final String LOG_TAG = "BlaubotBluetoothBeacon";
    private static final String BEACON_SERVICE_NAME = "BeaconService";
    private IBlaubotDevice ownDevice;

    private IBlaubotListeningStateListener listeningStateListener;
    private IBlaubotIncomingConnectionListener acceptorListener;
    private BluetoothServerSocket serverSocket;

	private BeaconScanner beaconScanner;
	private BeaconAcceptThread acceptThread;
	private IBlaubotDiscoveryEventListener discoveryEventListener;
	private boolean discoveryActivated;
	private IBlaubotState currentState = null;

	private UUID currentBeaconUUID;
    private IBlaubotBeaconStore beaconStore;
    private Blaubot blaubot;
    private KingdomCensusLifecycleListener kingdomCensusLifecycleListener;

    public BlaubotBluetoothBeacon() {
        this.discoveryActivated = true;
	}

    @Override
    public IBlaubotAdapter getAdapter() {
        return null;
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
			final BluetoothServerSocket bss = BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord(BEACON_SERVICE_NAME, currentBeaconUUID);
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
            throw new RuntimeException("Could not create the bluetooth ServerSocket!");
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
    public ConnectionMetaDataDTO getConnectionMetaData() {
        // TODO: maybe beacons should not derive from acceptors anymore
        return null;
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
	 *
	 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
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
			final BluetoothServerSocket serverSocket = BlaubotBluetoothBeacon.this.serverSocket;
			while (!isInterrupted() && serverSocket != null) { // this is busy wait (for 3 to 5 iterations) - I can live
																// with that.
				BluetoothSocket bluetoothSocket;
				try {
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Waiting for incoming beacon connections ...");
					}
					bluetoothSocket = serverSocket.accept();
				} catch (IOException e) {
					if(Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Beacon communication failed with I/O Exception ", e);
					}
					// TODO: in cases where the bluetooth adapter fails (not unlikely on android) we will end up in an endless loop
					continue;
				}
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got a new beacon connection from " + bluetoothSocket.getRemoteDevice());
				}

                String uniqueDeviceId;
                try {
                    // we read the unique device id
                    DataInputStream dis = new DataInputStream(bluetoothSocket.getInputStream());
                    uniqueDeviceId = UniqueDeviceIdHelper.readUniqueDeviceId(dis);

                    // we send our unique device id
                    UniqueDeviceIdHelper.sendUniqueDeviceIdThroughOutputStream(ownDevice, bluetoothSocket.getOutputStream());
                } catch (IOException e) {
                    if(Log.logErrorMessages()) {
                        Log.e(LOG_TAG, "Something went wrong exchanging unique device Ids");
                    }
                    try {
                        bluetoothSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }



				BlaubotBluetoothDevice bluetoothDevice = new BlaubotBluetoothDevice(uniqueDeviceId, bluetoothSocket.getRemoteDevice());
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
	 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
	 *
	 */
	class BeaconScanner extends Thread {
		private static final String LOG_TAG = "BeaconScanner";
		private static final int MIN_WAIT_BETWEEN_BEACON_PROBES = 2000; // ms
		private static final int MAX_WAIT_BETWEEN_BEACON_PROBES = 5000; // ms

        /**
         * Maps UniqueDeviceIds -> BluetoothMacAdresses
         */
        private ConcurrentHashMap<String, String> uniqueIdToMacAddressCache = new ConcurrentHashMap<>();


		@Override
		public void interrupt() {
			super.interrupt();
		}

		/**
		 * Creates the list of devices to be scanned.
		 *
		 * @return list of devices to scan
		 */
		private ArrayList<BluetoothDevice> getDevicesToScan() {
			// We only check devices that are bonded and not already connected to our network to minimize
			// the expensive bluetooth sdp lookup and connectivity traffic.
			Set<BluetoothDevice> bondedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
			ArrayList<BluetoothDevice> devicesToScan = new ArrayList<>();
            final Set<String> excludedUniqueIds = new HashSet<>(kingdomCensusLifecycleListener.getConnectedUniqueIds());
            final Set<String> excludedMacAddresses = new HashSet<>();
            for(Map.Entry<String, String> entry : uniqueIdToMacAddressCache.entrySet()) {
                if(excludedUniqueIds.contains(entry.getKey())) {
                    continue;
                }
                excludedMacAddresses.add(entry.getValue());
            }
            for (BluetoothDevice d : bondedDevices) {
                // try to filter connected devices
                if(excludedMacAddresses.contains(d.getAddress())) {
                    // -- address of d is in the network, so skip
                    continue;
                }
                devicesToScan.add(d);

			}
			// After filtering we sort the devices by descending by their addresses
			Collections.sort(devicesToScan, new Comparator<BluetoothDevice>() {
                @Override
                public int compare(BluetoothDevice lhs, BluetoothDevice rhs) {
                    return lhs.getAddress().compareTo(rhs.getAddress());
                }
            });
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

				ArrayList<BluetoothDevice> devicesToScan = getDevicesToScan();
				if(!(this.isInterrupted() || interrupted || isDiscoveryDisabled())) {
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Probing " + (devicesToScan.size()) + " bonded devices on their beacon interfaces");
					}
				}
				for (BluetoothDevice device : devicesToScan) {
					if (this.isInterrupted() || interrupted || isDiscoveryDisabled())
						break;
					if (notAvailableDevices.contains(device)) {
						if(Log.logDebugMessages()) {
							Log.d(LOG_TAG, "Skipping known dead device " + device + " (" + device.getName() + ")");
						}
						continue; // skip dead devices
					}

					BluetoothSocket socket;
                    try {
                        BlaubotConstants.BLUETOOTH_ADAPTER_LOCK.acquire();
                        try {
                            if(Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "Connecting to beacon of " + device + " (" + device.getName() + ")" + " ... ");
                            }
                            socket = device.createRfcommSocketToServiceRecord(currentBeaconUUID);
                            if(!notAvailableDevices.contains(device)) {
                                socket.connect();
                            }
                        } catch (IOException e) {
                            socket = null;
                            if(Log.logWarningMessages()) {
                                Log.w(LOG_TAG, "Could not connect to bluetooth beacon of device " + device + " (" + device.getName() + ") -> " + e.getMessage());
                            }
                        } finally {
                            BlaubotConstants.BLUETOOTH_ADAPTER_LOCK.release();
                        }
                    } catch (InterruptedException e) {
                        break;
                    }

					if(socket != null) {
                        String uniqueDeviceId;
                        try {
                            // send our uniqueDeviceId
                            UniqueDeviceIdHelper.sendUniqueDeviceIdThroughOutputStream(ownDevice, socket.getOutputStream());

                            // read the accepting device's uniqueDeviceId
                            DataInputStream dis = new DataInputStream(socket.getInputStream());
                            uniqueDeviceId = UniqueDeviceIdHelper.readUniqueDeviceId(dis);
                            uniqueIdToMacAddressCache.put(uniqueDeviceId, socket.getRemoteDevice().getAddress());

                        } catch (IOException e) {
                            if(Log.logErrorMessages()) {
                                Log.e(LOG_TAG, "Something went wrong exchanging unique device Ids");
                            }
                            try {
                                socket.close();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            continue;
                        }

                        BlaubotBluetoothDevice bbd = new BlaubotBluetoothDevice(uniqueDeviceId, device);
						IBlaubotConnection connection = new BlaubotBluetoothConnection(bbd, socket);
                        final List<ConnectionMetaDataDTO> connectionMetaDataList = BlaubotAdapterHelper.getConnectionMetaDataList(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
						ExchangeStatesTask exchangeStatesTask = new ExchangeStatesTask(ownDevice, connection, currentState, connectionMetaDataList, beaconStore, discoveryEventListener);
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
			return !discoveryActivated;
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
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
        this.currentBeaconUUID = blaubot.getUuidSet().getBeaconUUID();
        this.ownDevice = blaubot.getOwnDevice();
        this.kingdomCensusLifecycleListener = new KingdomCensusLifecycleListener(ownDevice);
        this.blaubot.addLifecycleListener(this.kingdomCensusLifecycleListener);
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    private static final long NEGATIVE_LIST_TIMEOUT = 1300;
    protected TimeoutList<BluetoothDevice> notAvailableDevices = new TimeoutList<>(NEGATIVE_LIST_TIMEOUT);

}
