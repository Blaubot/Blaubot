package eu.hgross.blaubot.mock;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import eu.hgross.blaubot.core.AbstractBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;

/**
 * Mock object for a BlaubotConnection
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotConnectionMock extends AbstractBlaubotConnection implements IBlaubotConnection {

	private IBlaubotDevice mockDevice;
	private DataInputStream din;
	private OutputStream out;
	private Socket socket;

	public BlaubotConnectionMock(IBlaubotDevice device, Socket socket) {
		this.mockDevice = device;
		this.socket = socket;

		try {
			InputStream in = socket.getInputStream();
			this.din = new DataInputStream(in);
			this.out = socket.getOutputStream();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void disconnect() {
		try {
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		notifyDisconnected();
	}

	@Override
	public boolean isConnected() {
		return this.socket.isConnected();
	}

	@Override
	public IBlaubotDevice getRemoteDevice() {
		return mockDevice;
	}

	@Override
	public void write(int b) throws IOException {
		this.out.write(b);
	}

	@Override
	public void write(byte[] bytes) throws IOException {
		this.out.write(bytes);
	}

	@Override
	public void write(byte[] bytes, int byteOffset, int byteCount) throws IOException {
		this.out.write(bytes, byteOffset, byteCount);
	}

	@Override
	public int read() throws IOException {
		return din.read();
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		return din.read(buffer);
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
		return din.read(buffer, byteOffset, byteCount);
	}

	@Override
	public void readFully(byte[] buffer) throws IOException {
		din.readFully(buffer);
	}

	@Override
	public void readFully(byte[] buffer, int offset, int byteCount) throws IOException {
		din.readFully(buffer, offset, byteCount);
	}

}
