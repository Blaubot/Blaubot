package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Notifies the king, that a server connection is available on the sending device (uniqueDeviceId included)
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class ServerConnectionAvailableAdminMessage extends AbstractAdminMessage {
    private class MessageDTO {
        String mediatorUniqueDeviceId;
        String recipientUniqueDeviceId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MessageDTO that = (MessageDTO) o;

            if (mediatorUniqueDeviceId != null ? !mediatorUniqueDeviceId.equals(that.mediatorUniqueDeviceId) : that.mediatorUniqueDeviceId != null)
                return false;
            if (recipientUniqueDeviceId != null ? !recipientUniqueDeviceId.equals(that.recipientUniqueDeviceId) : that.recipientUniqueDeviceId != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = mediatorUniqueDeviceId != null ? mediatorUniqueDeviceId.hashCode() : 0;
            result = 31 * result + (recipientUniqueDeviceId != null ? recipientUniqueDeviceId.hashCode() : 0);
            return result;
        }
    }
    private MessageDTO data;

	public ServerConnectionAvailableAdminMessage(String mediatorUniqueDeviceId, String recipientUniqueDeviceId) {
		super(CLASSIFIER_SERVER_CONNECTION_AVAILABLE);
        this.data = new MessageDTO();
        this.data.mediatorUniqueDeviceId = mediatorUniqueDeviceId;
        this.data.recipientUniqueDeviceId = recipientUniqueDeviceId;
	}

	public ServerConnectionAvailableAdminMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}

    @Override
    protected byte[] payloadToBytes() {
        String json = gson.toJson(this.data);
        return json.getBytes(BlaubotConstants.STRING_CHARSET);
    }


    @Override
    protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
        byte[] stringBytes = Arrays.copyOfRange(messagePayloadAsBytes.array(), messagePayloadAsBytes.position(), messagePayloadAsBytes.capacity());
        String json = new String(stringBytes, BlaubotConstants.STRING_CHARSET);
        MessageDTO dto = gson.fromJson(json, MessageDTO.class);
        this.data = dto;
    }

	/**
	 * @return the mediator's unqiue device id (the relaying device)
	 */
	public String getMediatorUniqueDeviceId() {
		return data.mediatorUniqueDeviceId;
	}

    /**
     * @return the recipient's unique device id (where the connection was made to)
     */
    public String getRecipientUniqueDeviceId() {
        return data.recipientUniqueDeviceId;
    }

	@Override
	public String toString() {
		return "ServerConnectionAvailableAdminMessage [mediatorUniqueDeviceId=" + data.mediatorUniqueDeviceId + ", recipientUnqiueDeviceId=" + data.recipientUniqueDeviceId + "]";
	}
	
	
	public static void main(String args[]) {
		ServerConnectionAvailableAdminMessage m = new ServerConnectionAvailableAdminMessage("blabla", "recipient");
		ServerConnectionAvailableAdminMessage around = new ServerConnectionAvailableAdminMessage(m.toBlaubotMessage());
		System.out.println(""+m.data.mediatorUniqueDeviceId.length() + "___" + around.data.mediatorUniqueDeviceId.length());
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ServerConnectionAvailableAdminMessage that = (ServerConnectionAvailableAdminMessage) o;

        if (data != null ? !data.equals(that.data) : that.data != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (data != null ? data.hashCode() : 0);
        return result;
    }
}
