package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Informs the peasants and the prince that they have to join another king's kingdom.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BowDownToNewKingAdminMessage extends AbstractAdminMessage {
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

    public BowDownToNewKingAdminMessage(String uniqueDeviceId, List<ConnectionMetaDataDTO> connectionMetaDataList) {
        super(CLASSIFIER_BOW_DOWN_TO_NEW_KING);
        this.data = new MessageDTO();
        this.data.connectionMetaDataList = connectionMetaDataList;
        this.data.uniqueDeviceId= uniqueDeviceId;
    }

    public BowDownToNewKingAdminMessage(BlaubotMessage rawMessage) {
        super(rawMessage);
    }


    @Override
    protected byte[] payloadToBytes() {
        String strToSend = gson.toJson(data);
        return strToSend.getBytes(BlaubotConstants.STRING_CHARSET);
    }

    @Override
    protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
        byte[] stringBytes = Arrays.copyOfRange(messagePayloadAsBytes.array(), messagePayloadAsBytes.position(), messagePayloadAsBytes.capacity());
        String readString = new String(stringBytes, BlaubotConstants.STRING_CHARSET);
        this.data = gson.fromJson(readString, MessageDTO.class);
    }


    /**
	 * 
	 * @return the discovered king's unique device id
	 */
	public String getNewKingsUniqueDeviceId() {
		return data.uniqueDeviceId;
	}


    /**
     *
     * @return the meta data for the device's connectors
     */
    public List<ConnectionMetaDataDTO> getNewKingsConnectionMetaDataList() {
        return this.data.connectionMetaDataList;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BowDownToNewKingAdminMessage{");
        sb.append("data=").append(data);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BowDownToNewKingAdminMessage that = (BowDownToNewKingAdminMessage) o;

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
