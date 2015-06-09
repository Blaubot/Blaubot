package eu.hgross.blaubot.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;

/**
 * A connection abstraction.
 * 
 * First of all an implementation has to offer read and write methods similiar 
 * to the well known {@link Socket} class as well as the convenience readFully 
 * methods known from the {@link DataInputStream} class.
 * 
 * Furthermore an implementation MUST inform it's {@link IBlaubotConnectionListener}s
 * if this connection disconnects - whether from an intended disconnect or a 
 * connection loss.
 * 
 * A disconnected connection object is not of further interest to blaubot and 
 * should to be thought of as dead and ready for garbage collection.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotConnection  {
	/**
	 * Disconnects the connection (if connected), nothing otherwise
	 */
	public void disconnect();
	public boolean isConnected();

    /**
     * Adds a connection listener to get informed if the connection gets closed.
     * @param listener the listener to add
     */
	public void addConnectionListener(IBlaubotConnectionListener listener);
	public void removeConnectionListener(IBlaubotConnectionListener listener);

	/**
	 * The remote device connected to our device.
	 * @return the remote device
	 */
	public IBlaubotDevice getRemoteDevice();

	public void write(int b) throws SocketTimeoutException, IOException;
	public void write(byte[] bytes) throws SocketTimeoutException, IOException;
	public void write(byte[] bytes, int byteOffset, int byteCount) throws SocketTimeoutException, IOException;

	public int read() throws SocketTimeoutException, IOException;
	public int read(byte[] buffer) throws SocketTimeoutException, IOException;
	public int read(byte[] buffer, int byteOffset, int byteCount) throws SocketTimeoutException, IOException;
	public void readFully(byte[] buffer) throws SocketTimeoutException, IOException;
	public void readFully(byte[] buffer, int offset, int byteCount) throws SocketTimeoutException, IOException;
	
}
