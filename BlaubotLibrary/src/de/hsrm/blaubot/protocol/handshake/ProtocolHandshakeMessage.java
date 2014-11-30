package de.hsrm.blaubot.protocol.handshake;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.protocol.client.ProtocolClient;
import de.hsrm.blaubot.protocol.master.ProtocolMaster;

/**
 * Is sent by the {@link ProtocolMaster} to a {@link ProtocolClient} to assign a
 * short device id.
 * 
 * @see ProtocolHandshakeAckMessage
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class ProtocolHandshakeMessage {
	private static final int BYTE_LENGTH = 2;
	short shortDeviceId;

	public ProtocolHandshakeMessage(short shortDeviceId) {
		super();
		this.shortDeviceId = shortDeviceId;
	}

	public short getShortDeviceId() {
		return shortDeviceId;
	}

	public static ProtocolHandshakeMessage fromBytes(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(BlaubotConstants.BYTE_ORDER);
		ProtocolHandshakeMessage msg = new ProtocolHandshakeMessage((short) 0);
		msg.shortDeviceId = bb.getShort();
		return msg;
	}

	public static ProtocolHandshakeMessage fromConnection(IBlaubotConnection connection) throws SocketTimeoutException, IOException {
		byte[] bytes = new byte[BYTE_LENGTH];
		connection.readFully(bytes, 0, BYTE_LENGTH);
		return fromBytes(bytes);
	}

	public byte[] toBytes() {
		ByteBuffer bb = ByteBuffer.allocate(BYTE_LENGTH);
		bb.order(BlaubotConstants.BYTE_ORDER);
		bb.putShort(shortDeviceId);
		bb.flip();
		return bb.array();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + shortDeviceId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProtocolHandshakeMessage other = (ProtocolHandshakeMessage) obj;
		if (shortDeviceId != other.shortDeviceId)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ProtocolHandshakeMessage [shortDeviceId=" + shortDeviceId + "]";
	}

}
