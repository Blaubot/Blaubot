package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Informs the king about the discovery of a new king.
 *
 * @deprecated use DiscoveredDeviceAdminMessage instead
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class PrinceFoundAKingAdminMessage extends AbstractAdminMessage {
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

	public PrinceFoundAKingAdminMessage(String uniqueDeviceId, List<ConnectionMetaDataDTO> connectionMetaDataList) {
		super(CLASSIFIER_PRINCE_FOUND_A_KING_MESSAGE);
        this.data = new MessageDTO();
        this.data.connectionMetaDataList = connectionMetaDataList;
		this.data.uniqueDeviceId= uniqueDeviceId;
	}
	
	public PrinceFoundAKingAdminMessage(BlaubotMessage rawMessage) {
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
	public String getKingsUniqueDeviceId() {
		return data.uniqueDeviceId;
	}

    /**
     *
     * @return the meta data for the device's connectors
     */
    public List<ConnectionMetaDataDTO> getConnectionMetaDataList() {
        return this.data.connectionMetaDataList;
    }

	@Override
	public String toString() {
		return "PrinceFoundAKingAdminMessage [uniqueDeviceId=" + data.uniqueDeviceId + ", connectionMetaData=" + data.uniqueDeviceId + "]";
	}
}
