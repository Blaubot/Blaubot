package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Pronounces a new prince device by it's uniqueId string.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class AddSubscriptionAdminMessage extends AbstractAdminMessage {
	private String uniqueDeviceId;
    private short channelId;

	public AddSubscriptionAdminMessage(String uniqueDeviceId, short channelId) {
		super(CLASSIFIER_ADD_SUBSCRIPTION);
		this.uniqueDeviceId = uniqueDeviceId;
        this.channelId = channelId;
	}

	public AddSubscriptionAdminMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}

	@Override
	protected byte[] payloadToBytes() {
        byte[] strBytes = uniqueDeviceId.getBytes(BlaubotConstants.STRING_CHARSET);
        int strByteLen = strBytes.length;
        int capacity = 2 + strByteLen; // short + strbytes
        ByteBuffer bb = ByteBuffer.allocate(capacity);
        bb.order(BlaubotConstants.BYTE_ORDER);
        bb.putShort(channelId);
        bb.put(strBytes);
        byte[] bytes = new byte[capacity];
        bb.clear();
        bb.get(bytes);
		return bytes;
	}

	@Override
	protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
        channelId = messagePayloadAsBytes.getShort();
		byte[] stringBytes = Arrays.copyOfRange(messagePayloadAsBytes.array(), messagePayloadAsBytes.position(), messagePayloadAsBytes.capacity());
		this.uniqueDeviceId = new String(stringBytes, BlaubotConstants.STRING_CHARSET);
	}

	/**
	 * @return the subcribing device's uniqueDeviceId
	 */
	public String getUniqueDeviceId() {
		return uniqueDeviceId;
	}

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("AddSubscriptionAdminMessage{");
        sb.append("uniqueDeviceId='").append(uniqueDeviceId).append('\'');
        sb.append(", channelId=").append(channelId);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        AddSubscriptionAdminMessage that = (AddSubscriptionAdminMessage) o;

        if (channelId != that.channelId) return false;
        if (uniqueDeviceId != null ? !uniqueDeviceId.equals(that.uniqueDeviceId) : that.uniqueDeviceId != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (uniqueDeviceId != null ? uniqueDeviceId.hashCode() : 0);
        result = 31 * result + (int) channelId;
        return result;
    }


    /**
     * The channel id to which the device wants to subscribe
     * @return
     */
    public short getChannelId() {
        return channelId;
    }

    public static void main(String args[]) {
        AddSubscriptionAdminMessage msg = new AddSubscriptionAdminMessage("bla", (short)2);
        AddSubscriptionAdminMessage msg2 = new AddSubscriptionAdminMessage(msg.toBlaubotMessage());
        System.out.println(msg + "___" + msg2);

    }
}
