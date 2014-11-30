package de.hsrm.blaubot.protocol.handshake;

import java.io.IOException;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.util.Log;

/**
 * Executes the client side of the ProtocolHandshake.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class ProtocolHandshakeClientTask extends ProtocolHandshakeTask {
	private static final String TAG = "ProtocolHandshakeClientTask";

	public ProtocolHandshakeClientTask(IBlaubotConnection connection, IProtocolHandshakeListener handshakeListener) {
		super(connection, handshakeListener);
	}

	@Override
	public boolean onExecute(IBlaubotConnection connection) {
		if (Log.logDebugMessages()) {
			Log.d(TAG, "client handshake task executing");
		}
		// -- we are client
		// read a short device id from the master and send an ACK message back
		try {
			if (Log.logDebugMessages()) {
				Log.d(TAG, "Awaiting handshake message from master");
			}
			ProtocolHandshakeMessage receivedHandshakeMessage = ProtocolHandshakeMessage.fromConnection(connection);
			// send ack
			if (Log.logDebugMessages()) {
				Log.d(TAG, "Got handshake message from master: " + receivedHandshakeMessage);
				Log.d(TAG, "Sending handshake ack to master");
			}
			setShortDeviceId(receivedHandshakeMessage.getShortDeviceId());
			if (getShortDeviceId() == 0)
				throw new RuntimeException("shortDeviceID == 0");
			ProtocolHandshakeAckMessage ackMessage = new ProtocolHandshakeAckMessage(getShortDeviceId());
			connection.write(ackMessage.toBytes());
		} catch (IOException e) {
			// TODO: !!!! danger !!! define behaviour for this very possible
			// exception
			if (Log.logErrorMessages()) {
				Log.e(TAG, "Failed to receive handshakeMessage or to send handshakeAckMessage, disconnecting connection.");
				connection.disconnect();
			}
			return false;
		}
		if (Log.logDebugMessages()) {
			Log.d(TAG, "Handshake done on client");
		}
		return true;
	}
}