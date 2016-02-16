package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Pronounces a new prince device by it's uniqueId as well as his connection meta data.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class PronouncePrinceAdminMessage extends AbstractAdminMessage {
    private class MessageDTO {
        String uniqueDeviceId;
        List<ConnectionMetaDataDTO> connectionMetaDataList;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MessageDTO that = (MessageDTO) o;

            if (connectionMetaDataList != null ? !connectionMetaDataList.equals(that.connectionMetaDataList) : that.connectionMetaDataList != null)
                return false;
            if (uniqueDeviceId != null ? !uniqueDeviceId.equals(that.uniqueDeviceId) : that.uniqueDeviceId != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = uniqueDeviceId != null ? uniqueDeviceId.hashCode() : 0;
            result = 31 * result + (connectionMetaDataList != null ? connectionMetaDataList.hashCode() : 0);
            return result;
        }
    }
    private MessageDTO data;


	public PronouncePrinceAdminMessage(String uniqueDeviceId, List<ConnectionMetaDataDTO> connectionMetaDataList) {
		super(CLASSIFIER_NEW_PRINCE_MESSAGE);
        this.data = new MessageDTO();
		this.data.uniqueDeviceId = uniqueDeviceId;
        this.data.connectionMetaDataList = connectionMetaDataList;
	}

	public PronouncePrinceAdminMessage(BlaubotMessage rawMessage) {
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
	 * @return the new prince device's uniqueId string
	 */
	public String getUniqueDeviceId() {
		return data.uniqueDeviceId;
	}

    /**
     * @return the new prince device's connection metadata
     */
    public List<ConnectionMetaDataDTO> getConnectionDataList() {
        return data.connectionMetaDataList;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("PronouncePrinceAdminMessage{");
        sb.append("data=").append(data);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        PronouncePrinceAdminMessage that = (PronouncePrinceAdminMessage) o;

        if (data != null ? !data.equals(that.data) : that.data != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (data != null ? data.hashCode() : 0);
        return result;
    }

    public static void main(String args[]) {
		PronouncePrinceAdminMessage m = new PronouncePrinceAdminMessage("blabla", new ArrayList<ConnectionMetaDataDTO>());
		PronouncePrinceAdminMessage around = new PronouncePrinceAdminMessage(m.toBlaubotMessage());
		System.out.println("" + m.data.uniqueDeviceId.length() + "___" + around.data.uniqueDeviceId.length());
	}


}
