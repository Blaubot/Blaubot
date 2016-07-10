package eu.hgross.blaubot.android.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.BlaubotConnectionManager;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.UniqueDeviceIdHelper;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.util.Log;

/**
 * An Acceptor handling incoming bluetooth connections for android devices.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class BlaubotBluetoothConnectionAcceptor implements IBlaubotConnectionAcceptor {
	private static final String LOG_TAG = BlaubotBluetoothConnectionAcceptor.class.toString();
	private IBlaubotListeningStateListener listeningStateListener;
	private IBlaubotIncomingConnectionListener acceptorListener;
	private BluetoothAcceptThread acceptThread = null;
	private boolean started = false;
	private BlaubotBluetoothAdapter blaubotBluetoothAdapter;
    private IBlaubotBeaconStore beaconStore;

    public BlaubotBluetoothConnectionAcceptor(BlaubotBluetoothAdapter blaubotBluetoothAdapter) {
		this.blaubotBluetoothAdapter = blaubotBluetoothAdapter;
	}

    @Override
    public IBlaubotAdapter getAdapter() {
        return blaubotBluetoothAdapter;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
	public void startListening() {
		if (acceptThread != null) {
			stopListening();
		}
		acceptThread = new BluetoothAcceptThread(blaubotBluetoothAdapter.getUUIDSet().getAppUUID());
		acceptThread.start();
	}

	@Override
	public void stopListening() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Stop listening for bluetooth clients ...");
		}
		if (acceptThread != null) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Interrupting and joining acceptThread ...");
			}
			acceptThread.interrupt();
			try {
				acceptThread.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "AcceptThread stopped ...");
			}
		}
		acceptThread = null;
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
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothConnectionMetaDataDTO connectionMetaDataDTO = new BluetoothConnectionMetaDataDTO(bluetoothAdapter.getAddress());
        return connectionMetaDataDTO;
    }

    @Override
	public boolean isStarted() {
		return started;
	}
	
	/**
	 * Handles initial BlauBot instance communication. Once a client connects, the connected socket is handed over to the {@link BlaubotConnectionManager} clientConnections
	 * 
	 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
	 */
	public class BluetoothAcceptThread extends Thread {
		private final String LOG_TAG = "BluetoothAcceptor.BluetoothAcceptThread";
		private BluetoothServerSocket serverSocket;
		private UUID uuid;

		/**
		 * @param uuid The uuid to register with bluetooth SDP
		 */
		public BluetoothAcceptThread(UUID uuid) {
			this.uuid = uuid;
			setName("bluetooth-acceptor-accept-thread");
		}

		@Override
		public void interrupt() {
			// TODO move serversocket close to another method (semantics)
			super.interrupt();
			if(this.serverSocket == null) {
				return;
			}
			Log.d(LOG_TAG, "Closing ServerSocket ...");
			// TODO: first interrupt and close after a timeout if not interrupted until then
			try {
				this.serverSocket.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "Closing ServerSocket caused exception", e);
			}
		}

		@Override
		public void run() {
			started = true;
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Accept Thread starting ...");
			}
			BluetoothServerSocket s = null;
			try {
				s = BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord(BlaubotConstants.BLUETOOTH_ACCEPTORS_RFCOMM_SDP_SERVICE_NAME, this.uuid);
				serverSocket = s;
			} catch (IOException e) {
				if(Log.logErrorMessages()) {
					Log.e(LOG_TAG, "Could not listen to RFCOMM", e);
				}
				started = false;
				throw new RuntimeException("TODO: handle listen() failure");
			}

			boolean notifiedListening = false;
			while (!this.isInterrupted()) {
				BluetoothSocket socket = null;
				try {
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Creating bluetooth ServerSocket for incoming BlueBot slaves ...");
					}
					if(!notifiedListening && listeningStateListener!= null) {
						notifiedListening = true;
						listeningStateListener.onListeningStarted(BlaubotBluetoothConnectionAcceptor.this);
					}
					socket = serverSocket.accept();
					// TODO: check out if interrupt() causes an InterruptedException
				} catch (IOException e) {
					if(Log.logWarningMessages()) {
						Log.w(LOG_TAG, "ServerSocket accept failed.", e);
					}
				}

				if (socket != null) {
					// we got a connection gather unique id
                    String uniqueDeviceId;
                    try {
                        // read the accepting device's uniqueDeviceId
                        DataInputStream dis = new DataInputStream(socket.getInputStream());
                        uniqueDeviceId = UniqueDeviceIdHelper.readUniqueDeviceId(dis);
                    } catch (IOException e) {
                        if(Log.logErrorMessages()) {
                            Log.e(LOG_TAG, "Something went wrong gathering the unique device id");
                        }
                        try {
                            socket.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        continue;
                    }


					BlaubotBluetoothDevice device = new BlaubotBluetoothDevice(uniqueDeviceId, socket.getRemoteDevice());
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Got connection from blaubot slave device " + socket.getRemoteDevice().getAddress() + "(" + device.getName() + ")");
					}
					BlaubotBluetoothConnection connection = new BlaubotBluetoothConnection(device, socket);

                    // retrieve their beacon message with their state and most importantly their acceptor meta data
                    final BeaconMessage theirBeaconMessage = BeaconMessage.fromBlaubotConnection(connection);
                    beaconStore.putDiscoveryEvent(theirBeaconMessage, device);


                    if(acceptorListener != null) {
						acceptorListener.onConnectionEstablished(connection);
					} else {
						if(Log.logWarningMessages()) {
							Log.w(LOG_TAG, "No AcceptorListener registered to " + this + " - connection established but unknown to everyone!");
						}
					}
				} else {
					// TODO: we also end here, if we hit the limit of the max bluetooth connections on a device!!
					if(Log.logWarningMessages()) {
						Log.w(LOG_TAG, "Socket null - no client connected. This can happen if you hit the maximum connections supported by a device's bluetooth hardware, on timeout or aborted calls.");
					}
				}
			}
			// loop finished, check wether we need to notified observers, that we started listening
			// if so, notify that we are now not listening anymore
			if(notifiedListening && listeningStateListener != null) {
				listeningStateListener.onListeningStopped(BlaubotBluetoothConnectionAcceptor.this);
			}
			started = false;
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Accept Thread finished ...");
			}
		}
	}
}
