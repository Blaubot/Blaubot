package de.hsrm.blaubot.message;

import java.nio.ByteBuffer;
import java.util.Arrays;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.protocol.StreamManager;

/**
 * wrapper for blaubot message chunks. for conveniently extracting message parts
 * of interest like the payload or channel id of the message instead of having
 * to extract them from the underlying raw byte array.
 * 
 * @author manuelpras
 */
public class BlaubotMessage {

	/**
	 * 1 byte protocol version, 1 byte type, 2 byte channel id, 2 byte source
	 * device id
	 */
	public final static int HEADER_BYTE_COUNT = 6;
	public final static int PAYLOAD_LENGTH_BYTE_COUNT = 2;

	private byte protocolVersion;
	private MessageType messageType;
	private byte[] payload;
	private int priority;
	private short sourceDeviceId;
	private short channelId;

	/**
	 * version of the protocol which was used to send the message
	 * 
	 * @return
	 */
	public byte getProtocolVersion() {
		return this.protocolVersion;
	}

	/**
	 * {@link MessageType} of the message
	 * 
	 * @return
	 */
	public MessageType getMessageType() {
		return this.messageType;
	}

	/**
	 * @return the {@link #getMessageType()} represented as a byte
	 */
	public byte getMessageTypeAsByte() {
		return this.messageType.toByte();
	}

	/**
	 * @return length of the payload byte array if a payload is set, 0 otherwise
	 */
	public short getPayloadLength() {
		return (short) (this.payload == null ? 0 : this.payload.length);
	}

	/**
	 * @return payload as byte array if any, null otherwise
	 */
	public byte[] getPayload() {
		return this.payload;
	}

	/**
	 * priority of the message, usually represented by the priority
	 * configuration of the channel which was used to send the message
	 * 
	 * @return
	 */
	public int getPriority() {
		return this.priority;
	}

	/**
	 * @return the short device ID of the device which originally sent the
	 *         message
	 */
	public short getSourceDeviceId() {
		return this.sourceDeviceId;
	}

	/**
	 * @return the ID of the channel is used to send the message
	 */
	public short getChannelId() {
		return this.channelId;
	}

	public void setProtocolVersion(byte protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	public void setMessageType(MessageType messageType) {
		this.messageType = messageType;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public void setSourceDeviceId(short sourceDeviceId) {
		this.sourceDeviceId = sourceDeviceId;
	}

	public void setChannelId(short channelId) {
		this.channelId = channelId;
	}

	/**
	 * @return the byte array containing the message parts
	 */
	public byte[] toByteArray() {
		short payloadLength = 0;
		// check if header length is correct!!!
		int headerLength = HEADER_BYTE_COUNT;
		final boolean containsPayload = (getPayload() != null && getPayload().length > 0);
		if (containsPayload) {
			payloadLength = (short) getPayload().length;
			headerLength += 2;
		}
		final int capacity = headerLength + payloadLength;
		ByteBuffer buffer = ByteBuffer.allocate(capacity).order(StreamManager.MY_BYTE_ORDER);
		buffer.put(getProtocolVersion());
		buffer.put(getMessageTypeAsByte());
		buffer.putShort(getChannelId());
		buffer.putShort(getSourceDeviceId());

		if (containsPayload) {
			buffer.putShort(payloadLength);
			buffer.put(getPayload());
		}
		byte[] bytes = new byte[capacity];
		buffer.clear();
		buffer.get(bytes);
		return bytes;
	}

	@Override
	public String toString() {
		return "BlaubotMessage [protocolVersion=" + protocolVersion + ", messageType=" + messageType + ", priority=" + priority + ", sourceDeviceId=" + sourceDeviceId + ", channelId=" + channelId + "]";
	}

	public BlaubotMessage createHeader(byte[] headerBytes) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(headerBytes).order(BlaubotConstants.BYTE_ORDER);
		byte version = byteBuffer.get();
		setProtocolVersion(version);

		byte type = byteBuffer.get();
		MessageType messageType = MessageType.fromByte(type);
		setMessageType(messageType);

		short channelId = byteBuffer.getShort();
		setChannelId(channelId);

		short sourceDeviceId = byteBuffer.getShort();
		setSourceDeviceId(sourceDeviceId);

		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + channelId;
		result = prime * result + ((messageType == null) ? 0 : messageType.hashCode());
		result = prime * result + Arrays.hashCode(payload);
		result = prime * result + protocolVersion;
		result = prime * result + sourceDeviceId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof BlaubotMessage))
			return false;
		BlaubotMessage other = (BlaubotMessage) obj;
		if (channelId != other.channelId)
			return false;
		if (messageType == null) {
			if (other.messageType != null)
				return false;
		} else if (!messageType.equals(other.messageType))
			return false;
		if (!Arrays.equals(payload, other.payload))
			return false;
		if (protocolVersion != other.protocolVersion)
			return false;
		if (sourceDeviceId != other.sourceDeviceId)
			return false;
		return true;
	}

}
