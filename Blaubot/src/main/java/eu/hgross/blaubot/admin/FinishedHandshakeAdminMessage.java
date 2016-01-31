package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Is sent by the ChannelManager in Client mode.
 * Before it is sent, the channel manager forbids any publishs of the channels until it receives an
 * echo with the isAck flag set. If it receives the echo, the channels are activated.
 * This ensures that the client knows about the subscriptions and can act accordingly before posting.
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 */
public class FinishedHandshakeAdminMessage extends AbstractAdminMessage {
    private class MessageDTO {
        String finishedMessageUuid;
        boolean isAck;

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("MessageDTO{");
            sb.append("finishedMessageUuid='").append(finishedMessageUuid).append('\'');
            sb.append(", isAck=").append(isAck);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MessageDTO that = (MessageDTO) o;

            if (isAck != that.isAck) return false;
            return !(finishedMessageUuid != null ? !finishedMessageUuid.equals(that.finishedMessageUuid) : that.finishedMessageUuid != null);

        }

        @Override
        public int hashCode() {
            int result = finishedMessageUuid != null ? finishedMessageUuid.hashCode() : 0;
            result = 31 * result + (isAck ? 1 : 0);
            return result;
        }
    }

    private MessageDTO data;


    public FinishedHandshakeAdminMessage() {
        super(CLASSIFIER_FINISHED_HANDSHAKE);
        this.data = new MessageDTO();
        this.data.finishedMessageUuid = UUID.randomUUID().toString();
        this.data.isAck = false;
    }

    @Override
    public BlaubotMessage toBlaubotMessage() {
        BlaubotMessage blaubotMessage = super.toBlaubotMessage();
        // lower prio than the add and remove subscription messages is mandatory
        blaubotMessage.setPriority(BlaubotMessage.Priority.ADMIN_LOW);
        blaubotMessage.getMessageType().setIsFirstHop(false);
        return blaubotMessage;
    }

    public FinishedHandshakeAdminMessage(BlaubotMessage rawMessage) {
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

    public boolean getIsAck() {
        return data.isAck;
    }

    public void setIsAck(boolean isAck) {
        data.isAck = isAck;
    }

    public String getMessageUuid() {
        return data.finishedMessageUuid;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("FinishedHandshakeAdminMessage{");
        sb.append("data=").append(data);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        FinishedHandshakeAdminMessage that = (FinishedHandshakeAdminMessage) o;

        return !(data != null ? !data.equals(that.data) : that.data != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (data != null ? data.hashCode() : 0);
        return result;
    }
}
