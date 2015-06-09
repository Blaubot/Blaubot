package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Relays a BlaubotMessage to the server.
 * Contains only the BlaubotMessage that has to be sent to the server
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 */
public class RelayAdminMessage extends AbstractAdminMessage {
    private static final int MAX_PAYLOAD = BlaubotConstants.MAX_PAYLOAD_SIZE + AbstractAdminMessage.HEADER_LENGTH + BlaubotMessage.FULL_HEADER_LENGTH;

    private byte[] serializedBlaubotMessage;

    /**
     * @param serializedBlaubotMessage the blaubot message to be relayed to the server as byte array (with header)
     * @throws IllegalArgumentException if the payload (serializedBlaubotMessage) exceeds the maximum payload
     */
    public RelayAdminMessage(byte[] serializedBlaubotMessage) {
        super(CLASSIFIER_SERVER_CONNECTION_RELAY_PAYLOAD);
        // assert size
        if (serializedBlaubotMessage.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("Too big payload for RelayAdminMessage (max " + MAX_PAYLOAD + " but got " + serializedBlaubotMessage.length + ")");
        }
        this.serializedBlaubotMessage = serializedBlaubotMessage;
    }

    /**
     * Note: this is only used internally, don't use this to wrap a BlaubotMessage inside the RelayAdminMessage! use the byte constructor for this
     *
     * @param rawMessage the raw message to extract THIS admin message from
     */
    public RelayAdminMessage(BlaubotMessage rawMessage) {
        super(rawMessage);
    }

    @Override
    protected byte[] payloadToBytes() {
        return serializedBlaubotMessage;
    }

    @Override
    protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
        serializedBlaubotMessage = Arrays.copyOfRange(messagePayloadAsBytes.array(), messagePayloadAsBytes.position(), messagePayloadAsBytes.capacity());
    }

    /**
     * @return the wrapped blaubot message
     */
    public BlaubotMessage getAsBlaubotMessage() {
        return BlaubotMessage.fromByteArray(serializedBlaubotMessage);
    }

    /**
     * @return the wrapped blaubot message
     */
    public byte[] getMessageBytes() {
        return serializedBlaubotMessage;
    }

    @Override
    public BlaubotMessage toBlaubotMessage() {
        BlaubotMessage blaubotMessage = super.toBlaubotMessage();
        // lower priority
        blaubotMessage.setPriority(BlaubotMessage.Priority.ADMIN_LOW);
        return blaubotMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        RelayAdminMessage that = (RelayAdminMessage) o;

        if (!Arrays.equals(serializedBlaubotMessage, that.serializedBlaubotMessage))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (serializedBlaubotMessage != null ? Arrays.hashCode(serializedBlaubotMessage) : 0);
        return result;
    }

    @Override
    public String toString() {
        BlaubotMessage blaubotMessage = BlaubotMessage.fromByteArray(serializedBlaubotMessage);
        try {
            return "RelayAdminMessage[" + AdminMessageFactory.createAdminMessageFromRawMessage(blaubotMessage) + "]";
        } catch (Exception e) {

        }
        return "RelayAdminMessage[" + blaubotMessage + "]";
    }

    //    @Override
//    public String toString() {
//        final StringBuffer sb = new StringBuffer("RelayAdminMessage{");
//        sb.append("serializedBlaubotMessage=");
//        if (serializedBlaubotMessage == null) sb.append("null");
//        else {
//            sb.append('[');
//            for (int i = 0; i < serializedBlaubotMessage.length; ++i)
//                sb.append(i == 0 ? "" : ", ").append(serializedBlaubotMessage[i]);
//            sb.append(']');
//        }
//        sb.append('}');
//        return sb.toString();
//    }

    public static void main(String args[]) {
        BlaubotMessage msg = new BlaubotMessage();
        msg.setPayload("blabla".getBytes());
        final byte[] bytes = msg.toBytes();

        RelayAdminMessage rmsg = new RelayAdminMessage(bytes);
        RelayAdminMessage rmsg2 = new RelayAdminMessage(rmsg.toBlaubotMessage());
        System.out.println(rmsg + "___" + rmsg2);
    }
}
