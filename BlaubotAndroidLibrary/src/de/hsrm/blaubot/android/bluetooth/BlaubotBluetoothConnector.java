package de.hsrm.blaubot.android.bluetooth;

import java.io.IOException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;
import de.hsrm.blaubot.core.connector.IncompatibleBlaubotDeviceException;
import de.hsrm.blaubot.util.Log;

/**
 * Bluetooth connector implementation for android.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotBluetoothConnector implements IBlaubotConnector {
	private static final String LOG_TAG = "BlaubotBluetoothConnector";
	private IBlaubotIncomingConnectionListener incomingConnectionListener;
	private BlaubotBluetoothAdapter blaubotBluetoothAdapter;

	public BlaubotBluetoothConnector(BlaubotBluetoothAdapter blaubotBluetoothAdapter) {
		this.blaubotBluetoothAdapter = blaubotBluetoothAdapter;
	}


	@Override
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener incomingConnectionListener) {
		this.incomingConnectionListener = incomingConnectionListener;
	}

	@Override 
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) throws IncompatibleBlaubotDeviceException {
		if (!(blaubotDevice instanceof BlaubotBluetoothDevice))
			throw new IncompatibleBlaubotDeviceException("Only BlaubotBluetoothDevice instances are supported.");

		BlaubotBluetoothDevice bbd = (BlaubotBluetoothDevice) blaubotDevice;
		BluetoothSocket socket = null;
		try {
			blaubotBluetoothAdapter.bluetoothAdapterSempaphore.acquire();
			try {
				socket = bbd.createRfcommSocketToServiceRecord(blaubotBluetoothAdapter.getUUIDSet().getAppUUID());
				socket.connect();
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got a new connection from " + socket.getRemoteDevice());
				}
				BlaubotBluetoothDevice device = new BlaubotBluetoothDevice(socket.getRemoteDevice(), blaubotBluetoothAdapter);
				IBlaubotConnection connection = new BlaubotBluetoothConnection(device, socket);
				this.incomingConnectionListener.onConnectionEstablished(connection);
				return connection;
			} catch (IOException e) {
				if(Log.logWarningMessages()) {
					Log.w(LOG_TAG, "Bluetooth connect failed! Adding " + bbd + " to dead devices", e);
				}
				// TODO: maybe retry 1-2 times ??
				blaubotBluetoothAdapter.onConnectionToDeviceFailed(bbd);
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
				blaubotBluetoothAdapter.bluetoothAdapterSempaphore.release();
			}
		} catch (InterruptedException e2) {
			if(Log.logWarningMessages()) {
				Log.w(LOG_TAG, "What to do here?!");
			}
		} 
		return null;
	}

	@Override
	public IBlaubotDevice createRemoteDevice(String uniqueId) {
		for(BluetoothDevice d : BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
			if(d.getAddress().equals(uniqueId)){
				BlaubotBluetoothDevice bbd = new BlaubotBluetoothDevice(d, blaubotBluetoothAdapter);
				return bbd;
			}
		}
		return null;
	}

}
