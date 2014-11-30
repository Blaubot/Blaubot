package de.hsrm.blaubot.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionListener;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.util.Log;

/**
 * wrapper for the given {@link IBlaubotConnection} which will listen for
 * incoming {@link BlaubotMessage}s. the {@link StreamManager} will create a new
 * {@link BlaubotMessage} by the received bytes or will throw an exception or
 * call an error callback if there are any failures during this progress (e.g.
 * wrong protocol version)
 * 
 * @author manuelpras
 *
 */
public class StreamManager extends Thread implements IStreamManager {

	public static final ByteOrder MY_BYTE_ORDER = BlaubotConstants.BYTE_ORDER;
	private static final String TAG = "StreamManager";

	private IBlaubotConnection connection;
	private CopyOnWriteArraySet<IStreamListener> listeners;
	private AtomicBoolean canceled;

	public StreamManager(IBlaubotConnection connection) {
		this.connection = connection;
		connection.addConnectionListener(new IBlaubotConnectionListener() {

			@Override
			public void onConnectionClosed(IBlaubotConnection connection) {
				// cancel this stream manager if connection is closed
				cancel();
			}
		});
		listeners = new CopyOnWriteArraySet<IStreamListener>();
		canceled = new AtomicBoolean(false);
	}

	/**
	 * cancel the {@link StreamManager} thread's run() method and removes all
	 * listeners
	 */
	public void cancel() {
		this.canceled.set(true);
		this.listeners.clear();
		this.interrupt();
		if (Log.logDebugMessages()) {
			Log.d(TAG, "Canceled StreamManager");
		}
	}

	/**
	 * listens for incoming messages, checks them and notifies listeners if
	 * necessary
	 */
	public void run() {
		byte[] buffer = null;

		// Keep listening to the InputStream until an exception occurs
		while (!this.canceled.get()) {
			// Read from the InputStream
			try {
				int headerLength = BlaubotMessage.HEADER_BYTE_COUNT;
				buffer = new byte[headerLength];
				connection.readFully(buffer, 0, headerLength);
				BlaubotMessage message = new BlaubotMessage();
				message.createHeader(buffer);

				byte version = message.getProtocolVersion();
				// assert correct protocol version
				if (version != ProtocolManager.PROTOCOL_VERSION || version < 0) {
					for (IStreamListener listener : listeners) {
						listener.onWrongProtocolVersion(ProtocolManager.PROTOCOL_VERSION, version);
					}
					continue;
				}

				if (message.getMessageType().containsPayload()) {
					// 2 byte payload length
					int payloadLengthByteCount = BlaubotMessage.PAYLOAD_LENGTH_BYTE_COUNT;
					buffer = new byte[payloadLengthByteCount];
					connection.readFully(buffer, 0, payloadLengthByteCount);
					short length = ByteBuffer.wrap(buffer).order(MY_BYTE_ORDER).getShort();

					// n byte payload
					buffer = new byte[length];
					connection.readFully(buffer, 0, length);
					byte[] payload = new byte[length];
					ByteBuffer.wrap(buffer).order(MY_BYTE_ORDER).get(payload);

					message.setPayload(payload);
				}
				// notify listeners about new message
				for (IStreamListener listener : listeners) {
					listener.onMessage(message);
				}
			} catch (IOException e) {
				if (Log.logDebugMessages()) {
					Log.d(TAG, "IOException while reading and connection closed");
				}
				this.canceled.set(true);
			}
		}
	}

	public void addListener(IStreamListener listener) {
		listeners.add(listener);
		if (listeners.size() > 1) {
			throw new RuntimeException("too many listeners?!");
		}
	}

	public void removeListener(IStreamListener listener) {
		listeners.remove(listener);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((connection == null) ? 0 : connection.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof StreamManager))
			return false;
		StreamManager other = (StreamManager) obj;
		if (connection == null) {
			if (other.connection != null)
				return false;
		} else if (!connection.equals(other.connection))
			return false;
		return true;
	}

	@Override
	public boolean isCanceled() {
		return this.canceled.get();
	}

}
