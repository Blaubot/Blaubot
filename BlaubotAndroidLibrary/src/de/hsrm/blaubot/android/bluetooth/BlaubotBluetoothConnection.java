package de.hsrm.blaubot.android.bluetooth;

import java.io.DataInputStream;
import java.io.IOException;

import android.bluetooth.BluetoothSocket;
import de.hsrm.blaubot.core.AbstractBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.util.Log;

/**
 * An android specific {@link IBlaubotConnection} implementation for bluetooth.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotBluetoothConnection extends AbstractBlaubotConnection {
	private static final String LOG_TAG = "BlaubotBluetoothConnection";
	private BluetoothSocket socket;
	private BlaubotBluetoothDevice bluetoothDevice;
	private DataInputStream dataInputStream;
	
	public BlaubotBluetoothConnection(BlaubotBluetoothDevice device, BluetoothSocket socket) {
		this.bluetoothDevice = device;
		this.socket = socket;
		try {
			this.dataInputStream = new DataInputStream(socket.getInputStream());
		} catch (IOException e) {
			if(Log.logErrorMessages()) {
				Log.e(LOG_TAG, "Failed to get InputStream", e);
			}
			throw new RuntimeException("Failed to get InputStream");
		}
	}
	
	private boolean notifiedDisconnect = false;
	@Override
	protected synchronized void notifyDisconnected() {
		if(notifiedDisconnect)
			return;
		super.notifyDisconnected();
		notifiedDisconnect = true;
	}

	@Override
	public void disconnect() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Disconnecting BluetoothBlaubotConnection " + this + " ...");
		}
//		if(socket.isConnected()) {
			try {
				socket.close();
			} catch (IOException e) {
				if(Log.logErrorMessages()) {
					Log.e(LOG_TAG, "Failed to close socket", e);
				}
			}
//		} else {
//			Log.d(LOG_TAG, "BluetoothBlaubotConnection was already closed " + this + " ...");
//		}
		this.notifyDisconnected();
	}

	private void handleSocketException(IOException e) throws IOException {
		if(Log.logWarningMessages()) {
			Log.w(LOG_TAG, "Got socket exception", e);
		}
		this.disconnect();
		throw e;
	}

	@Override
	public int read() throws IOException {
		try {
			return this.socket.getInputStream().read();
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}
	}

	@Override
	public int read(byte[] b) throws IOException {
		try {
			return this.socket.getInputStream().read(b);
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
		try {
			return this.socket.getInputStream().read(buffer, byteOffset, byteCount);
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}		
	}

	@Override
	public void write(int b) throws IOException {
		try {
			this.socket.getOutputStream().write(b);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void write(byte[] bytes) throws IOException {
		try {
			this.socket.getOutputStream().write(bytes);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		try {
			this.socket.getOutputStream().write(b,off,len);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void readFully(byte[] buffer) throws IOException {
		try {
			dataInputStream.readFully(buffer);
		} catch (IOException e) {
			handleSocketException(e);
		}
	}
	
	@Override
	public void readFully(byte[] buffer, int offset, int byteCount) throws IOException {
		try {
			dataInputStream.readFully(buffer, offset, byteCount);
		} catch (IOException e) {
			handleSocketException(e);
		}
	}
	
	@Override
	public IBlaubotDevice getRemoteDevice() {
		return this.bluetoothDevice;
	}

	@Override
	public boolean isConnected() {
		return this.socket.isConnected();
	}

	@Override
	public String toString() {
		return "BlaubotBluetoothConnection["+getRemoteDevice()+"]";
	}

	
}
