package eu.hgross.blaubot.android.bluetooth;

import android.bluetooth.BluetoothSocket;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import eu.hgross.blaubot.core.AbstractBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.util.Log;

/**
 * An android specific {@link IBlaubotConnection} implementation for bluetooth.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlaubotBluetoothConnection extends AbstractBlaubotConnection {
	private static final String LOG_TAG = "BlaubotBluetoothConnection";
	private BluetoothSocket socket;
	private IBlaubotDevice bluetoothDevice;
	private DataInputStream dataInputStream;
    private UUID uuid = UUID.randomUUID(); // for instance based hashcode/equals
	
	public BlaubotBluetoothConnection(IBlaubotDevice device, BluetoothSocket socket) {
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
	protected void notifyDisconnected() {
		if(notifiedDisconnect)
			return;
		super.notifyDisconnected();
		notifiedDisconnect = true;
	}

	private Object closeMonitor = new Object();
	private boolean disconnected = false;
	@Override
	public void disconnect() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Disconnecting BluetoothBlaubotConnection " + this + " ...");
		}
		if(!disconnected) {
			try {
				synchronized (closeMonitor) {
					socket.close();
					disconnected = true;
				}
			} catch (IOException e) {
				if (Log.logErrorMessages()) {
					Log.e(LOG_TAG, "Failed to close socket", e);
				}
			}
		}
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
		checkClosed();
		try {
			return this.socket.getInputStream().read();
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}
	}

	/**
	 * checks if the socket was disconnected and throws an IOException if so
	 * @throws IOException
	 */
	private void checkClosed() throws IOException {
        // some device have problems when using a bluetooth socket after it was closed: http://stackoverflow.com/questions/18147925/fatal-signal-11-sigsegv-when-closing-bluetoothsocket-on-android-4-2-2-and-4-3/29529974#2952997
        // fix: after disconnect() store a flag and if set, always throw IOException when trying to read/write
		synchronized (closeMonitor) {
			if (disconnected) {
				throw new IOException("Connection closed");
			}
		}
	}

	@Override
	public int read(byte[] b) throws IOException {
		checkClosed();
		try {
			return this.socket.getInputStream().read(b);
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
		checkClosed();
		try {
			return this.socket.getInputStream().read(buffer, byteOffset, byteCount);
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}		
	}

	@Override
	public void write(int b) throws IOException {
		checkClosed();
		try {
			this.socket.getOutputStream().write(b);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void write(byte[] bytes) throws IOException {
		checkClosed();
		try {
			this.socket.getOutputStream().write(bytes);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		checkClosed();
		try {
			this.socket.getOutputStream().write(b,off,len);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void readFully(byte[] buffer) throws IOException {
		checkClosed();
		try {
			dataInputStream.readFully(buffer);
		} catch (IOException e) {
			handleSocketException(e);
		}
	}
	
	@Override
	public void readFully(byte[] buffer, int offset, int byteCount) throws IOException {
		checkClosed();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BlaubotBluetoothConnection that = (BlaubotBluetoothConnection) o;

        if (bluetoothDevice != null ? !bluetoothDevice.equals(that.bluetoothDevice) : that.bluetoothDevice != null)
            return false;
        if (socket != null ? !socket.equals(that.socket) : that.socket != null) return false;
        if (uuid != null ? !uuid.equals(that.uuid) : that.uuid != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (socket != null ? socket.hashCode() : 0);
        result = 31 * result + (bluetoothDevice != null ? bluetoothDevice.hashCode() : 0);
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        return result;
    }
}
