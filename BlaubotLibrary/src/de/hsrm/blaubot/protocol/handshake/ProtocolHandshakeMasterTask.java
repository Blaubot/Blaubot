package de.hsrm.blaubot.protocol.handshake;

import java.io.IOException;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.util.Log;

/**
 * Executes the master side of the ProtocolHandshanke.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class ProtocolHandshakeMasterTask extends ProtocolHandshakeTask {
	private static final String TAG = "ProtocolHandshakeMasterTask";

	/**
	 * Create a task that assigns deviceId to the client device connected via connection.
	 * 
	 * @param deviceId The short device id for the device connected via connection
	 * @param connection the connection from this client device
	 * @param handshakeListener
	 */
	public ProtocolHandshakeMasterTask(short deviceId, IBlaubotConnection connection, IProtocolHandshakeListener handshakeListener) {
		super(connection, handshakeListener);
		this.setShortDeviceId(deviceId);
	}

	@Override
	public boolean onExecute(IBlaubotConnection connection) {
		if (Log.logDebugMessages()) {
			Log.d(TAG, "master handshake task executing for unique ID == " + connection.getRemoteDevice().getUniqueDeviceID());
		}
		// send the handshakeMessage
		ProtocolHandshakeMessage handshakeMessage = new ProtocolHandshakeMessage(getShortDeviceId());
		try {
			if(Log.logDebugMessages()) {
				Log.d(TAG, "Sending the HandshakeMessage to the client (" + System.currentTimeMillis() + ")");
			}
			connection.write(handshakeMessage.toBytes());
		} catch (IOException e) {
			if(Log.logErrorMessages()) {
				Log.e(TAG, "Failed to send handshakeMessage, closing connection");
			}
			connection.disconnect();
			return false;
		}
		
		// retreive the ack (hopefully)
		try {
			if(Log.logDebugMessages()) {
				Log.d(TAG, "Waiting for the HandshakeAckMessage from the client");
			}
			ProtocolHandshakeAckMessage receivedAckMessage = ProtocolHandshakeAckMessage.fromConnection(connection);
			if(receivedAckMessage.getShortDeviceId() != getShortDeviceId()) {
				throw new RuntimeException("Received an ACK message for a wrong (not assigned) shortDeviceId from the client.");
			}
		} catch (IOException e) {
			if(Log.logErrorMessages()) {
				Log.e(TAG, "Failed to receive the ACKMessage for the ProtocolHandshakeMessage that assigned " + getShortDeviceId() + " as short device id to the client. Closing connection.");
			}
			connection.disconnect();
			return false;
		}
		if(Log.logDebugMessages()) {
			Log.d(TAG, "Handshake done on master ");
		}
		return true;
	}
	
}