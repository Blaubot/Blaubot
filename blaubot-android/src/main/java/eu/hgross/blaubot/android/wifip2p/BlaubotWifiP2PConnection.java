package eu.hgross.blaubot.android.wifip2p;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import eu.hgross.blaubot.core.AbstractBlaubotConnection;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.util.Log;

/**
 * WIFI P2P connection implementation
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class BlaubotWifiP2PConnection extends AbstractBlaubotConnection {
	protected static int UUID_BYTE_LENGTH = UUID.randomUUID().toString().getBytes(BlaubotConstants.STRING_CHARSET).length;
	private static final String LOG_TAG = "BlaubotWifiP2PConnection";
	private final BlaubotWifiP2PDevice device;
	private final Socket socket;
	private final DataInputStream dataInputStream;
	private volatile boolean notifiedDisconnect = false;
	private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
	
	/**
	 * Creates the blaubot connection from a connected clientSocket.
	 * This methods connects to the socket an checks wether the remote endpoint
	 * is a valid blaubot endpoint (wifip2p {@link IBlaubotConnectionAcceptor}) by
	 * directly reading a {@link UUID} from the connection.
	 * 
	 * If this {@link UUID} matches the app OR beacon UUID, then this connection
	 * will return a valid {@link IBlaubotConnection}.
	 * Otherwise, if the UUID does not match or nothing is received for too long,
	 * null will be the result.
	 * 
	 * @param device the remote device
	 * @param clientSocket the client socket
	 * @param uuidSet the {@link BlaubotUUIDSet} retrievable from a {@link IBlaubotAdapter}
	 * @return the connection object iff the remote endpoint is a blaubot instance which responded fast enougth with a valid beacon or app UUID. 
	 */
	public static BlaubotWifiP2PConnection fromSocket(BlaubotWifiP2PDevice device, Socket clientSocket, final BlaubotUUIDSet uuidSet) {
		final BlaubotWifiP2PConnection conn = new BlaubotWifiP2PConnection(device, clientSocket);
		// using a future to be able to time out
		Future<Boolean> future = EXECUTOR_SERVICE.submit(new Callable<Boolean>() {
			@Override
			public Boolean call()  {
				// read uuid and validate
				byte[] buffer = new byte[UUID_BYTE_LENGTH];
				byte[] buffer_ordered = new byte[UUID_BYTE_LENGTH];
				try {
					// read bytes to buffer
					conn.readFully(buffer);
					// read in order
					ByteBuffer.wrap(buffer).order(BlaubotConstants.BYTE_ORDER).get(buffer_ordered);
					String uuidString = new String(buffer_ordered, BlaubotConstants.STRING_CHARSET);
					
					// validate uuid
					if(uuidString.equals(uuidSet.getAppUUID().toString()) || uuidString.equals(uuidSet.getBeaconUUID().toString())) {
						return true;
					}
				} catch (IOException e) {
				}
				return false;
			}
		});
		try {
			boolean isValid = future.get(4000, TimeUnit.MILLISECONDS);
			if(isValid) {
				return conn;
			}
		} catch (InterruptedException e) {
		} catch (ExecutionException e) {
		} catch (TimeoutException e) {
			try {
				clientSocket.close();
			} catch (IOException e1) {}
		}
		return null;
		// now read
	}
	
	private BlaubotWifiP2PConnection(BlaubotWifiP2PDevice device, Socket clientSocket) {
		this.socket = clientSocket;
		this.device = device;
		try {
			this.dataInputStream = new DataInputStream(clientSocket.getInputStream());
		} catch (IOException e) {
			throw new RuntimeException("Could not get InputStream from clientSocket. A socket handed to the constructor has to be connected!");
		}
	}

	@Override
	protected synchronized void notifyDisconnected() {
		if (notifiedDisconnect)
			return;
		super.notifyDisconnected();
		notifiedDisconnect = true;
	}

	@Override
	public void disconnect() {
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Disconnecting BlaubotWifiP2PConnection " + this + " ...");
		}
		try {
			socket.close();
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to close socket", e);
		}
		this.notifyDisconnected();
	}

	@Override
	public boolean isConnected() {
		return socket.isConnected() && !socket.isClosed();
	}

	@Override
	public IBlaubotDevice getRemoteDevice() {
		return device;
	}

	private void handleSocketException(IOException e) throws SocketTimeoutException, IOException {
		if(Log.logWarningMessages()) {
			Log.w(LOG_TAG, "Got socket exception", e);
		}
		if(!(e instanceof SocketTimeoutException)) {
			this.disconnect();
		}
		throw e;
	}

	@Override
	public int read() throws SocketTimeoutException, IOException {
		try {
			return this.socket.getInputStream().read();
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}
	}

	@Override
	public int read(byte[] b) throws SocketTimeoutException, IOException {
		try {
			return this.socket.getInputStream().read(b);
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws SocketTimeoutException, IOException {
		try {
			return this.socket.getInputStream().read(buffer, byteOffset, byteCount);
		} catch (IOException e) {
			this.handleSocketException(e);
			return -1; // will never get here
		}		
	}

	@Override
	public void write(int b) throws SocketTimeoutException, IOException {
		try {
			this.socket.getOutputStream().write(b);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void write(byte[] bytes) throws SocketTimeoutException, IOException {
		try {
			this.socket.getOutputStream().write(bytes);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws SocketTimeoutException, IOException {
		try {
			this.socket.getOutputStream().write(b,off,len);
		} catch (IOException e) {
			this.handleSocketException(e);
		}
	}

	@Override
	public void readFully(byte[] buffer) throws SocketTimeoutException, IOException {
		try {
			dataInputStream.readFully(buffer);
		} catch (IOException e) {
			handleSocketException(e);
		}
	}
	
	@Override
	public void readFully(byte[] buffer, int offset, int byteCount) throws SocketTimeoutException, IOException {
		try {
			dataInputStream.readFully(buffer, offset, byteCount);
		} catch (IOException e) {
			handleSocketException(e);
		}
	}
}
