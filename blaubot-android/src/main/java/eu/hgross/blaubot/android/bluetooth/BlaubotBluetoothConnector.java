package eu.hgross.blaubot.android.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.UniqueDeviceIdHelper;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.connector.IncompatibleBlaubotDeviceException;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.util.Log;

/**
 * Bluetooth connector implementation for android.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class BlaubotBluetoothConnector implements IBlaubotConnector {
	private static final String LOG_TAG = "BlaubotBluetoothConnector";
    private static final List<String> SUPPORTED_ACCEPTOR_TYPES = Arrays.asList(BlaubotConstants.ACCEPTOR_TYPE_RFCOMM_ANDROID_BLUETOOTH, BlaubotConstants.ACCEPTOR_TYPE_RFCOMM_JSR82_BLUETOOTH);
    private final IBlaubotDevice ownDevice;
    private IBlaubotIncomingConnectionListener incomingConnectionListener;
    private BlaubotBluetoothAdapter blaubotBluetoothAdapter;
    private IBlaubotBeaconStore beaconStore;

    public BlaubotBluetoothConnector(BlaubotBluetoothAdapter blaubotBluetoothAdapter, IBlaubotDevice ownDevice) {
		this.ownDevice = ownDevice;
        this.blaubotBluetoothAdapter = blaubotBluetoothAdapter;
	}

    @Override
    public List<String> getSupportedAcceptorTypes() {
        return SUPPORTED_ACCEPTOR_TYPES;
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
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener incomingConnectionListener) {
		this.incomingConnectionListener = incomingConnectionListener;
	}

	@Override 
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) {
        final String uniqueDeviceID = blaubotDevice.getUniqueDeviceID();

        // check if we have meta data
        final List<ConnectionMetaDataDTO> lastKnownConnectionMetaData = beaconStore.getLastKnownConnectionMetaData(uniqueDeviceID);
        if(lastKnownConnectionMetaData == null) {
            if(Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not get connection meta data for unique device id " + uniqueDeviceID);
            }
            return null;
        }

        // take the first supported acceptor, if any
        final List<ConnectionMetaDataDTO> supportedAcceptors = BlaubotAdapterHelper.filterBySupportedAcceptorTypes(lastKnownConnectionMetaData, getSupportedAcceptorTypes());
        if(supportedAcceptors.isEmpty()) {
            throw new IncompatibleBlaubotDeviceException(blaubotDevice + " could not get acceptor meta data for this device.");
        }
        BluetoothConnectionMetaDataDTO bluetoothConnectionMetaData = new BluetoothConnectionMetaDataDTO(supportedAcceptors.get(0));
        final String bluetoothMacAddress = bluetoothConnectionMetaData.getMacAddress();

        // TODO: we want to decouple the android device form our IBlaubotDevice and create a bluetooth device here based on the mac
        // we want to do smthg like this here:
        final BluetoothAdapter androidBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        final BluetoothDevice remoteDevice = androidBluetoothAdapter.getRemoteDevice(bluetoothMacAddress);


		BluetoothSocket socket = null;
        try {
            BlaubotConstants.BLUETOOTH_ADAPTER_LOCK.acquire();
            try {
                socket = remoteDevice.createRfcommSocketToServiceRecord(blaubotBluetoothAdapter.getUUIDSet().getAppUUID());
                socket.connect();
                if(Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Got a new connection from " + socket.getRemoteDevice());
                }

                // send our unique device id
                UniqueDeviceIdHelper.sendUniqueDeviceIdThroughOutputStream(ownDevice, socket.getOutputStream());

                // create the connection object
                BlaubotBluetoothDevice device = new BlaubotBluetoothDevice(blaubotDevice.getUniqueDeviceID(), socket.getRemoteDevice());
                IBlaubotConnection connection = new BlaubotBluetoothConnection(device, socket);

                // send our acceptor data
                final BeaconMessage currentBeaconMessage = getAdapter().getBlaubot().getConnectionStateMachine().getBeaconService().getCurrentBeaconMessage();
                connection.write(currentBeaconMessage.toBytes());

                this.incomingConnectionListener.onConnectionEstablished(connection);
                return connection;
            } catch (IOException e) {
                if(Log.logWarningMessages()) {
                    Log.w(LOG_TAG, "Bluetooth connect failed! Adding " + blaubotDevice + " (" + remoteDevice+ ") to dead devices. Error: " + e.getMessage(), e);
                }
                // TODO: maybe retry 1-2 times ??
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e1) {
                    }
                }
            } catch (Exception e) {
                if(Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Reflection failure or something worse.", e);
                }
            } finally {
                BlaubotConstants.BLUETOOTH_ADAPTER_LOCK.release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

		return null;
	}

}
