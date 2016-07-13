package eu.hgross.blaubot.bluetooth;

import java.io.IOException;
import java.util.UUID;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import eu.hgross.blaubot.core.BlaubotConnectionManager;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IUnidentifiedBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.UniqueDeviceIdHelper;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.util.Log;

/**
 * An Acceptor handling incoming bluetooth connections.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class BlaubotJsr82BluetoothAcceptor implements IBlaubotConnectionAcceptor {
    private static final String LOG_TAG = BlaubotJsr82BluetoothAcceptor.class.toString();
    private final String localDeviceBluetoothMacAddress;
    private final LocalDevice localDevice;
    private IBlaubotListeningStateListener listeningStateListener;
    private IBlaubotIncomingConnectionListener acceptorListener;
    private Jsr82BluetoothAcceptThread acceptThread = null;
    private boolean started = false;
    private BlaubotJsr82BluetoothAdapter blaubotBluetoothAdapter;
    private IBlaubotBeaconStore beaconStore;

    /**
     * @param blaubotBluetoothAdapter the blaubot adapter
     * @throws BluetoothStateException if we cannot acces the hardware bluetooth adapter
     */
    public BlaubotJsr82BluetoothAcceptor(BlaubotJsr82BluetoothAdapter blaubotBluetoothAdapter) throws BluetoothStateException {
        this.blaubotBluetoothAdapter = blaubotBluetoothAdapter;
        this.localDevice = LocalDevice.getLocalDevice();
        this.localDeviceBluetoothMacAddress = localDevice.getBluetoothAddress();
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
        acceptThread = new Jsr82BluetoothAcceptThread(blaubotBluetoothAdapter.getUUIDSet().getAppUUID());
        acceptThread.start();
    }

    @Override
    public void stopListening() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Stop listening for bluetooth clients ...");
        }
        if (acceptThread != null) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Interrupting and joining acceptThread ...");
            }
            acceptThread.interrupt();
            try {
                acceptThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (Log.logDebugMessages()) {
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
        StringBuilder sb = new StringBuilder();
        int i=0;
        int l = localDeviceBluetoothMacAddress.length();
        for (char c : localDeviceBluetoothMacAddress.toCharArray()) {
            sb.append(c);
            if ((i%2) != 0 && i!=l-1 ) {
                sb.append(":");
            }
            i++;
        }

        String formattedMac = sb.toString();
        Jsr82BluetoothConnectionMetaDataDTO connectionMetaDataDTO = new Jsr82BluetoothConnectionMetaDataDTO(formattedMac);
        return connectionMetaDataDTO;
    }

    @Override
    public boolean isStarted() {
        return started;
    }


    /**
     * A blaubot device that enables us to delay the uniqueDeviceId set() -> used after we received it from the connection
     */
    private class UnidentifiedBlaubotDevice extends BlaubotDevice implements IUnidentifiedBlaubotDevice {
        public UnidentifiedBlaubotDevice() {
            super("UnidentifiedBlaubotDeviceFrom" + BlaubotJsr82BluetoothAcceptor.this);
        }

        @Override
        public void setUniqueDeviceId(String uniqueDeviceId) {
            this.uniqueDeviceId = uniqueDeviceId;
        }
    }

    /**
     * Handles initial BlauBot instance communication. Once a client connects, the connected socket is handed over to the {@link BlaubotConnectionManager} clientConnections
     *
     * @author Henning Gross {@literal (mail.to@henning-gross.de)}
     */
    public class Jsr82BluetoothAcceptThread extends Thread {
        private final String LOG_TAG = "Jsr82BluetoothAcceptor.Jsr82BluetoothAcceptThread";
        private UUID uuid;
        private StreamConnectionNotifier streamConnectionNotifier;



        /**
         * @param uuid The uuid to register with bluetooth SDP
         */
        public Jsr82BluetoothAcceptThread(UUID uuid) {
            this.uuid = uuid;
            setName("jsr82-bluetooth-acceptor-accept-thread");
        }

        @Override
        public void interrupt() {
            super.interrupt();
            if (this.streamConnectionNotifier == null) {
                return;
            }
            Log.d(LOG_TAG, "Closing streamConnection ...");
            try {
                this.streamConnectionNotifier.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Closing streamConnection caused exception", e);
            }
        }

        @Override
        public void run() {
            String reformattedUuid = uuid.toString().replace("-", "");
            final String url = "btspp://localhost:" + reformattedUuid +  ";name=" + BlaubotConstants.BLUETOOTH_ACCEPTORS_RFCOMM_SDP_SERVICE_NAME;
            started = true;
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "BluetoothJsr82 Accept Thread starting ...");
            }
            try {
                // we will be visible for an unlimited time: http://www.ampedrftech.com/guides/cod_definition.pdf
                localDevice.setDiscoverable(DiscoveryAgent.GIAC);
            } catch (BluetoothStateException e) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Failed to start GIAC discoverable state: " + e.getMessage(), e);
                }
            }
            StreamConnectionNotifier service = null;
            try {
                service = (StreamConnectionNotifier) Connector.open(url, Connector.READ_WRITE);
                streamConnectionNotifier = service;
            } catch (IOException e) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Could not listen to RFCOMM", e);
                }
                started = false;
                throw new RuntimeException("TODO: handle listen() failure");
            }

            boolean notifiedListening = false;
            while (!this.isInterrupted()) {
                StreamConnection socket = null;
                RemoteDevice dev = null;
                try {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Creating bluetooth StreamConnection for incoming blaubot connections ...");
                    }
                    if (!notifiedListening && listeningStateListener != null) {
                        notifiedListening = true;
                        listeningStateListener.onListeningStarted(BlaubotJsr82BluetoothAcceptor.this);
                    }
                    socket = service.acceptAndOpen();
                    dev = RemoteDevice.getRemoteDevice(socket);
                } catch (IOException e) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "ServerSocket accept failed.", e);
                    }
                }

                if (socket != null) {
                    // we got a connection gather unique id
                    UnidentifiedBlaubotDevice blaubotDevice;
                    BlaubotJsr82BluetoothConnection connection;
                    String readableName = null;
                    try {
                        blaubotDevice = new UnidentifiedBlaubotDevice();
                        connection = new BlaubotJsr82BluetoothConnection(blaubotDevice, socket);

                        // read the accepting device's uniqueDeviceId
                        String uniqueDeviceId = UniqueDeviceIdHelper.readUniqueDeviceId(connection.getDataInputStream());
                        blaubotDevice.setUniqueDeviceId(uniqueDeviceId);
                    } catch (IOException e) {
                        if (Log.logErrorMessages()) {
                            Log.e(LOG_TAG, "Something went wrong gathering the unique device id");
                        }
                        try {
                            socket.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        continue;
                    }

                    try {
                        readableName = dev.getFriendlyName(false);
                        if (readableName != null && !readableName.isEmpty()) {
                            blaubotDevice.setReadableName(readableName);
                        } else {
                            blaubotDevice.setReadableName(dev.getBluetoothAddress());
                        }
                    } catch (IOException e) {
                    }

                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Got connection from blaubot slave device " + dev.getBluetoothAddress() + "(" + blaubotDevice.getReadableName() + ")");
                    }

                    // retrieve their beacon message with their state and most importantly their acceptor meta data
                    final BeaconMessage theirBeaconMessage = BeaconMessage.fromBlaubotConnection(connection);
                    beaconStore.putDiscoveryEvent(theirBeaconMessage, blaubotDevice);


                    if (acceptorListener != null) {
                        acceptorListener.onConnectionEstablished(connection);
                    } else {
                        if (Log.logWarningMessages()) {
                            Log.w(LOG_TAG, "No AcceptorListener registered to " + this + " - connection established but unknown to everyone!");
                        }
                    }
                } else {
                    // TODO: we also end here, if we hit the limit of the max bluetooth connections on a device!!
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "Socket null - no client connected. This can happen if you hit the maximum connections supported by a device's bluetooth hardware, on timeout or aborted calls.");
                    }
                }
            }
            // loop finished, check wether we need to notified observers, that we started listening
            // if so, notify that we are now not listening anymore
            if (notifiedListening && listeningStateListener != null) {
                listeningStateListener.onListeningStopped(BlaubotJsr82BluetoothAcceptor.this);
            }
            started = false;
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Accept Thread finished ...");
            }
        }
    }
}
