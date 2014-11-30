package de.hsrm.blaubot.protocol.handshake;

import java.util.concurrent.CountDownLatch;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.protocol.client.ProtocolClient;
import de.hsrm.blaubot.protocol.master.ProtocolMaster;

/**
 * Base class for the bidirectional initial handshake between
 * {@link ProtocolClient}s an {@link ProtocolMaster}s on connection
 * establishement.
 * 
 * If the {@link IBlaubotConnection} associated with this task is closed 
 * before the task finished, the task will fail and call 
 * {@link IProtocolHandshakeListener#onFailure(IBlaubotConnection)}.
 * 
 * Every handshake participant has to handle timeouts by themselves.
 * This is usually done with a {@link CountDownLatch} waiting for the 
 * execute to either succeed or fail for a defined amount of time.
 * If a timeout occurs only a connection close will terminate the blocked
 * task.
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public abstract class ProtocolHandshakeTask {
	private IBlaubotConnection connection;
	private short shortDeviceId;
	
	private Runnable taskRunnable = new Runnable() {
		@Override
		public void run() {
			boolean succeeded = onExecute(connection);
			if (handshakeListener == null) {
				return;
			}
			if (succeeded) {
				handshakeListener.onSuccess(getShortDeviceId());
			} else {
				handshakeListener.onFailure(connection);
			}
		}
	};
	private IProtocolHandshakeListener handshakeListener;

	public ProtocolHandshakeTask(IBlaubotConnection connection, IProtocolHandshakeListener handshakeListener) {
		this.connection = connection;
		this.handshakeListener = handshakeListener;
	}

	/**
	 * @param connection
	 * @return true if the handshake succeed, false otherwise
	 */
	public abstract boolean onExecute(IBlaubotConnection connection);

	public void execute() {
		new Thread(taskRunnable).start();
	}

	public short getShortDeviceId() {
		return shortDeviceId;
	}

	public void setShortDeviceId(short shortId) {
		this.shortDeviceId = shortId;
	};
}