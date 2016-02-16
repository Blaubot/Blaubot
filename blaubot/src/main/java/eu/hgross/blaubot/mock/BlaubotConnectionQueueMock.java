package eu.hgross.blaubot.mock;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import eu.hgross.blaubot.core.AbstractBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;

/**
 * 
 * Mock object utilizing an input and an output queue to emulate connection operations. To
 * emulate data sent from a client (this connection is the client connection) use the
 * {@link #writeMockDataToInputStream(byte[])} method. This data can then be retrieved via
 * the read*() methods like {@link #readFully(byte[])}.
 * 
 * If you want to read the data written to this connection via its write*() methods like
 * {@link #write(byte[])}, use the {@link #getInputStreamForWrittenConnectionData()}
 * {@link InputStream} to do so.
 * 
 * If you need a pair of connections where one connection writes to the other connections
 * input stream, use {@link BlaubotConnectionQueueMock#getOtherEndpointConnection(eu.hgross.blaubot.core.IBlaubotDevice)}.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotConnectionQueueMock extends AbstractBlaubotConnection {

	protected LinkedBlockingQueue<Byte> inputQueue;
	protected LinkedBlockingQueue<Byte> outputQueue;
	private DataInputStream dataInputStream;
	private DataOutputStream dataOutputStream;
	protected volatile boolean connected = true;
	private IBlaubotDevice device;
    private UUID uuid = UUID.randomUUID();

	/**
	 * @param remoteDevice
	 *            the remote device
	 */
	public BlaubotConnectionQueueMock(IBlaubotDevice remoteDevice) {
		this.device = remoteDevice;
		this.inputQueue = new LinkedBlockingQueue<Byte>();
		this.outputQueue = new LinkedBlockingQueue<Byte>();
		setupDataStreams();
	}

	private void setupDataStreams() {
		this.dataInputStream = new DataInputStream(inputStream);
		this.dataOutputStream = new DataOutputStream(outputStream);
	}

	/**
	 * Retrieve a {@link BlaubotConnectionQueueMock} object which reads from THIS object
	 * for its read() methods and writes to THIS object.
	 * 
	 * @param otherSidesDevice
	 *            the other endpoint's {@link IBlaubotDevice}
	 * @return
	 */
	public BlaubotConnectionQueueMock getOtherEndpointConnection(IBlaubotDevice otherSidesDevice) {
		BlaubotConnectionQueueMock otherSide = new BlaubotConnectionQueueMock(otherSidesDevice);
		otherSide.outputQueue = inputQueue;
		otherSide.inputQueue = outputQueue;
		otherSide.setupDataStreams();
		return otherSide;

	}

	/**
	 * Write data to the stream that can be retrieved via the {@link IBlaubotConnection}s
	 * read*() methods.
	 * 
	 * @param data
	 *            the data to write to the input stream as byte array
	 */
	public synchronized void writeMockDataToInputStream(byte[] data) {
		for (byte b : data) {
			this.inputQueue.add(b);
		}
	}

	/**
	 * Retrieve an {@link InputStream} to get the data written to this
	 * {@link IBlaubotConnection} via it's write*() methods.
	 * 
	 * @return
	 */
	public InputStream getInputStreamForWrittenConnectionData() {
		return new InputStream() {
			@Override
			public int read() throws IOException {
				Byte b;
				try {
					b = outputQueue.take();
					if (!connected) {
						throw new IOException("Connection was closed");
					}
				} catch (InterruptedException e) {
                    // EOF
					return -1;
				}
				return b != null ? unsignedToBytes(b) : -1;
			}
		};
	}

	/**
	 * Converts the byte to an unsigned integer.
	 * 
	 * @param b
	 * @return
	 */
	private static int unsignedToBytes(byte b) {
		return b & 0xFF;
	}

	private InputStream inputStream = new InputStream() {
		@Override
		public int read() throws IOException {
			Byte b;
			try {
				b = inputQueue.take();
				if (!connected) {
					throw new IOException("Connection was closed");
				}
			} catch (InterruptedException e) {
                // EOF
				return -1;
			}
			return b != null ? unsignedToBytes(b) : -1;
		}
	};

	private OutputStream outputStream = new OutputStream() {
		@Override
		public void write(int b) throws IOException {
			byte toWrite = (byte) (b & 0xFF);
			try {
				if (!connected) {
					throw new IOException("Connection was closed");
				}
				outputQueue.put(toWrite);
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new IOException(e);
			}
		}
	};

	protected Object disconnnectMonitor = new Object();
	@Override
	public  void disconnect() {
		synchronized (disconnnectMonitor) {
			if (!connected) {
				return;
			}
			connected = false;
		}
		// put a byte to the inputQueue to ensure the readFully operations will die with
		// ioexceptions
		try {
			inputQueue.put((byte) 0);
		} catch (InterruptedException e) {
			e.printStackTrace();
            throw new RuntimeException(e);
		}
		this.notifyDisconnected();
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public IBlaubotDevice getRemoteDevice() {
		return device;
	}

	@Override
	public void write(int b) throws SocketTimeoutException, IOException {
		if (!connected) {
			throw new IOException("not connected");
		}
		dataOutputStream.write(b);
	}

	@Override
	public void write(byte[] bytes) throws SocketTimeoutException, IOException {
		if (!connected) {
			throw new IOException("not connected");
		}
		dataOutputStream.write(bytes);
	}

	@Override
	public void write(byte[] bytes, int byteOffset, int byteCount) throws SocketTimeoutException, IOException {
		if (!connected) {
			throw new IOException("not connected");
		}
		dataOutputStream.write(bytes, byteOffset, byteCount);
	}

	@Override
	public int read() throws SocketTimeoutException, IOException {
		if (!connected) {
			throw new IOException("not connected");
		}
		return dataInputStream.read();
	}

	@Override
	public int read(byte[] buffer) throws SocketTimeoutException, IOException {
		if (!connected) {
			throw new IOException("not connected");
		}
		return dataInputStream.read(buffer);
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws SocketTimeoutException, IOException {
		if (!connected) {
			throw new IOException("not connected");
		}
		return dataInputStream.read(buffer, byteOffset, byteCount);
	}

	@Override
	public void readFully(byte[] buffer) throws SocketTimeoutException, IOException {
		if (!connected) {
			throw new IOException("not connected");
		}
		dataInputStream.readFully(buffer);
	}

	@Override
	public void readFully(byte[] buffer, int offset, int byteCount) throws SocketTimeoutException, IOException {
		if (!connected) {
			throw new IOException("not connected");
		}
		dataInputStream.readFully(buffer, offset, byteCount);
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BlaubotConnectionQueueMock that = (BlaubotConnectionQueueMock) o;

        if (uuid != null ? !uuid.equals(that.uuid) : that.uuid != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        return result;
    }
}
