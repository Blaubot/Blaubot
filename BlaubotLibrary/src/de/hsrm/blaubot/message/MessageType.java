package de.hsrm.blaubot.message;

import java.util.BitSet;

/**
 * Wrapper for the byte representation of the message's types (internally there
 * is a BitSet saving the corresponding 8 Bits). Just use the fluent api in
 * order to change MessageType properties like Application type or NoBroadcast
 * type.
 * 
 * @author manuelpras
 * @see https 
 *      ://scm.mi.hs-rm.de/trac/2014maprojekt/2014maprojekt01/wiki/Nachrichten
 *      %20-%20Formate%20und%20Typen
 */
public class MessageType {

	private BitSet bitSet;

	protected static final int containsPayloadPos = 0;
	protected static final int adminPos = 1;
	protected static final int broadcastPos = 2;
	protected static final int deviceIDPos = 3;
	protected static final int channelIDPos = 4;
	protected static final int multiMessagePos = 5;
	protected static final int keepAlivePos = 6;
	protected static final int multiplexPos = 7;

	public MessageType() {
		// size = 1 byte = 8 bits
		bitSet = new BitSet(8);
	}

	/**
	 * sets the bit at the given index to 1
	 * 
	 * @param index
	 * @return this instance
	 */
	public MessageType setBit(int index) {
		bitSet.set(index);
		return this;
	}

	/**
	 * 
	 * @param index
	 * @return true if the bit at the specified index is set, false otherwise
	 */
	public boolean isBitSet(int index) {
		return bitSet.get(index);
	}

	/**
	 * marks that the corresponding message doesn't contain any payload
	 * 
	 * @return
	 */
	public MessageType NoPayload() {
		bitSet.set(containsPayloadPos, false);
		return this;
	}

	/**
	 * marks that the corresponding message contains some payload
	 * 
	 * @return
	 */
	public MessageType Payload() {
		bitSet.set(containsPayloadPos, true);
		return this;
	}

	/**
	 * @return true if the payload bit is set to 1, false otherwise
	 */
	public boolean containsPayload() {
		return bitSet.get(containsPayloadPos);
	}

	/**
	 * marks that the corresponding message is an admin message
	 * 
	 * @return
	 */
	public MessageType Admin() {
		bitSet.set(adminPos, true);
		return this;
	}

	/**
	 * marks that the corresponding message is an application (non-admin)
	 * message
	 * 
	 * @return
	 */
	public MessageType Application() {
		bitSet.set(adminPos, false);
		return this;
	}

	/**
	 * 
	 * @return true if admin message bit is set to 1, false otherwise
	 */
	public boolean isAdminMessage() {
		return bitSet.get(adminPos);
	}

	/**
	 * 
	 * @return true if admin message bit is set to 0, false otherwise
	 */
	public boolean isApplicationMessage() {
		return !isAdminMessage();
	}

	/**
	 * marks that the corresponding message shall be broadcasted
	 * 
	 * @return
	 */
	public MessageType Broadcast() {
		bitSet.set(broadcastPos, true);
		return this;
	}

	/**
	 * marks that the corresponding message should not be broadcasted
	 * 
	 * @return
	 */
	public MessageType NoBroadcast() {
		bitSet.set(broadcastPos, false);
		return this;
	}

	/**
	 * 
	 * @return true if broadcast bit is set to 1, false otherwise
	 */
	public boolean isBroadcastMessage() {
		return bitSet.get(broadcastPos);
	}

	/**
	 * marks that the corresponding message contains device id info. usually
	 * used by the admin layer, not in application layer!
	 * 
	 * @return
	 */
	public MessageType DeviceID() {
		bitSet.set(deviceIDPos, true);
		return this;
	}

	/**
	 * marks that the corresponding message doesn't contain any device id info.
	 * usually used by the admin layer, not in application layer!
	 * 
	 * @return
	 */
	public MessageType NoDeviceID() {
		bitSet.set(deviceIDPos, false);
		return this;
	}

	/**
	 * 
	 * @return true if device id bit is set to 1, false otherwise
	 */
	public boolean isDeviceID() {
		return bitSet.get(deviceIDPos);
	}

	/**
	 * marks that the corresponding message contains channel id info. usually
	 * used by the admin layer, not in application layer!
	 * 
	 * @return
	 */
	public MessageType ChannelID() {
		bitSet.set(channelIDPos, true);
		return this;
	}

	/**
	 * marks that the corresponding message doesn't contain any channel id info.
	 * usually used by the admin layer, not in application layer!
	 * 
	 * @return
	 */
	public MessageType NoChannelID() {
		bitSet.set(channelIDPos, false);
		return this;
	}

	/**
	 * 
	 * @return true if channel id bit is set to 1, false otherwise
	 */
	public boolean isChannelID() {
		return bitSet.get(channelIDPos);
	}

	/**
	 * marks that the corresponding message contains multiple
	 * {@link BlaubotMessage}s as payload
	 * 
	 * @return
	 */
	public MessageType MultiMessage() {
		bitSet.set(multiMessagePos, true);
		return this;
	}

	/**
	 * marks that the corresponding message doesn't contain multiple
	 * {@link BlaubotMessage}s as payload
	 * 
	 * @return
	 */
	public MessageType NoMultiMessage() {
		bitSet.set(multiMessagePos, false);
		return this;
	}

	/**
	 * 
	 * @return true if multi message bit is set to 1, false otherwise
	 */
	public boolean isMultiMessage() {
		return bitSet.get(multiMessagePos);
	}

	/**
	 * marks that the corresponding message is a keep alive message
	 * 
	 * @return
	 */
	public MessageType KeepAlive() {
		bitSet.set(keepAlivePos, true);
		return this;
	}

	/**
	 * marks that the corresponding message isn't a keep alive message
	 * 
	 * @return
	 */
	public MessageType NoKeepAlive() {
		bitSet.set(keepAlivePos, false);
		return this;
	}

	/**
	 * 
	 * @return true if keep alive bit is set to 1, false otherwise
	 */
	public boolean isKeepAlive() {
		return bitSet.get(keepAlivePos);
	}

	/**
	 * marks that the corresponding message shall be multiplexed (distributed to
	 * other clients) by the master
	 * 
	 * @return
	 */
	public MessageType Multiplex() {
		bitSet.set(multiplexPos, true);
		return this;
	}

	/**
	 * marks that the corresponding message should not be multiplexed by the
	 * master
	 * 
	 * @return
	 */
	public MessageType NoMultiplex() {
		bitSet.set(multiplexPos, false);
		return this;
	}

	/**
	 * 
	 * @return true if multiplex bit is set to 1, false otherwise
	 */
	public boolean isMultiplex() {
		return bitSet.get(multiplexPos);
	}

	/**
	 * creates the corresponding MessageType for the given byteArray
	 * 
	 * @param typeByte
	 *            byte representation of the MessageType
	 * @return corresponding MessageType for the given typeByte
	 */
	public static MessageType fromByte(byte typeByte) {
		BitSet bs = BitSet.valueOf(new byte[] { typeByte });
		MessageType type = new MessageType();
		for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
			type.setBit(i);
		}
		return type;
	}

	/**
	 * @return corresponding byte representation
	 */
	public byte toByte() {
		return bitSet.toByteArray()[0];
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bitSet == null) ? 0 : bitSet.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof MessageType))
			return false;
		MessageType other = (MessageType) obj;
		if (bitSet == null) {
			if (other.bitSet != null)
				return false;
		} else if (!bitSet.equals(other.bitSet))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "MessageType [bitSet=" + bitSet + "]";
	}

}
