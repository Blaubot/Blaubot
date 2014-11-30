package de.hsrm.blaubot.protocol.handshake;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.protocol.client.ProtocolClient;
import de.hsrm.blaubot.protocol.master.ProtocolMaster;

/**
 * Represents an answer to the {@link ProtocolHandshakeMessage} which was sent
 * from a {@link ProtocolMaster} to the {@link ProtocolClient}.
 * 
 * The ACK message concerns a specific shortDeviceId received by the initial
 * {@link ProtocolHandshakeMessage}.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class ProtocolHandshakeAckMessage {
	private static final int BYTE_LENGTH = 2;
	short shortDeviceId;

	/**
	 * @param shortDeviceId
	 *            short device id received from the remote endpoint of the
	 *            {@link IBlaubotConnection}
	 */
	public ProtocolHandshakeAckMessage(short shortDeviceId) {
		super();
		this.shortDeviceId = shortDeviceId;
	}

	/**
	 * 
	 * @return short device id received from the remote endpoint of the
	 *         {@link IBlaubotConnection}
	 */
	public short getShortDeviceId() {
		return shortDeviceId;
	}

	/**
	 * creates a {@link ProtocolHandshakeAckMessage} corresponding to the given
	 * byte array
	 * 
	 * @param bytes
	 *            byte array which contains the data for the
	 *            {@link ProtocolHandshakeAckMessage}
	 * @return {@link ProtocolHandshakeAckMessage} represented by the given byte
	 *         array
	 */
	public static ProtocolHandshakeAckMessage fromBytes(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(BlaubotConstants.BYTE_ORDER);
		ProtocolHandshakeAckMessage msg = new ProtocolHandshakeAckMessage((short) 0);
		msg.shortDeviceId = bb.getShort();
		return msg;
	}

	/**
	 * reads the data for the {@link ProtocolHandshakeAckMessage} from the given
	 * {@link IBlaubotConnection}
	 * 
	 * @param connection
	 *            {@link IBlaubotConnection} which is used to communicate with
	 *            the remote handshake partner
	 * @return resulting {@link ProtocolHandshakeAckMessage}, created from the
	 *         bytes which have been received by the given connection
	 * @throws SocketTimeoutException
	 * @throws IOException
	 */
	public static ProtocolHandshakeAckMessage fromConnection(IBlaubotConnection connection) throws SocketTimeoutException, IOException {
		byte[] bytes = new byte[BYTE_LENGTH];
		connection.readFully(bytes, 0, BYTE_LENGTH);
		return fromBytes(bytes);
	}

	/**
	 * @return byte array representation of this
	 *         {@link ProtocolHandshakeAckMessage} instance
	 */
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
		ProtocolHandshakeAckMessage other = (ProtocolHandshakeAckMessage) obj;
		if (shortDeviceId != other.shortDeviceId)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ProtocolHandshakeAckMessage [shortDeviceId=" + shortDeviceId + "]";
	}

}
