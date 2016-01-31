package eu.hgross.blaubot.messaging;

import java.util.BitSet;


/**
 * Fluent API-based class to work with the Type from the BlaubotMessage's header.
 * TODO lacks some documentation on the getter/setters
 */
public class BlaubotMessageType {
    private BitSet bitset;
    private static final int IS_ADMIN_MESSAGE = 0;
    private static final int IS_KEEP_ALIVE_MESSAGE = 1;
    private static final int IS_FIRST_HOP = 2; // basically signals, that a message has to pass the master first before reaching it's final destination
    private static final int CONTAINS_PAYLOAD_BIT = 3;
    private static final int IS_CHUNK = 4;
    private static final int EXCLUDE_SENDER = 5; // if set, a message is not dispatched to the connection, over which the message was received

    public BlaubotMessageType() {
        this.bitset = new BitSet(8);
        this.bitset.set(IS_FIRST_HOP); // set first hop by default
    }

    /**
     * Constructs a BlaubotMessageType object from a byte
     * @param typeByte header byte
     */
    protected static BlaubotMessageType fromByte(byte typeByte) {
        BlaubotMessageType msgType = new BlaubotMessageType();
        BitSet bs = BitSet.valueOf(new byte[] { typeByte });
        msgType.bitset = bs;
        return msgType;
    }

    /**
     * copies a message type
     * @param blaubotMessageType
     */
    protected static BlaubotMessageType copy(BlaubotMessageType blaubotMessageType) {
        final byte b = blaubotMessageType.toByte();
        return fromByte(b);
    }

    /**
     * @return corresponding byte representation
     */
    public byte toByte() {
        return bitset.toByteArray()[0];
    }

    public boolean containsPayload() {
        return bitset.get(CONTAINS_PAYLOAD_BIT);
    }

    public boolean isAdminMessage() {
        return bitset.get(IS_ADMIN_MESSAGE);
    }

    public boolean isKeepAliveMessage() {
        return bitset.get(IS_KEEP_ALIVE_MESSAGE);
    }

    public boolean isFirstHop() {
        return bitset.get(IS_FIRST_HOP);
    }

    public boolean isChunk() {
        return bitset.get(IS_CHUNK);
    }
    
    public boolean isSenderExcluded() {
        return bitset.get(EXCLUDE_SENDER);
    }

    public BlaubotMessageType setContainsPayload(boolean val) {
        bitset.set(CONTAINS_PAYLOAD_BIT, val);
        return this;
    }

    public BlaubotMessageType setIsAdminMessage(boolean val) {
        bitset.set(IS_ADMIN_MESSAGE, val);
        return this;
    }

    public BlaubotMessageType setIsKeepAliveMessage(boolean val) {
        bitset.set(IS_KEEP_ALIVE_MESSAGE, val);
        return this;
    }

    public BlaubotMessageType setIsFirstHop(boolean val) {
        bitset.set(IS_FIRST_HOP, val);
        return this;
    }

    public BlaubotMessageType setIsChunk(boolean val) {
        bitset.set(IS_CHUNK, val);
        return this;
    }

    public BlaubotMessageType setExcludeSender(boolean val) {
        bitset.set(EXCLUDE_SENDER, val);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlaubotMessageType that = (BlaubotMessageType) o;

        if (bitset != null ? !bitset.equals(that.bitset) : that.bitset != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return bitset != null ? bitset.hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BlaubotMessageType{");
        sb.append("containsPayload=").append(containsPayload());
        sb.append(", isAdminMessage=").append(isAdminMessage());
        sb.append(", isKeepAliveMessage=").append(isKeepAliveMessage());
        sb.append(", isFirstHop=").append(isFirstHop());
        sb.append(", isChunk=").append(isChunk());
        sb.append(", bitset=").append(bitset);
        sb.append('}');
        return sb.toString();
    }

}
