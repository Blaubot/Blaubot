package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Admin message used to dispatch a beacon event over
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class DiscoveredDeviceAdminMessage extends AbstractAdminMessage {
    private class MessageDTO {
        String uniqueDeviceId;
        State state;
        List<ConnectionMetaDataDTO> connectionMetaDataList;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MessageDTO that = (MessageDTO) o;

            if (state != that.state) return false;
            if (uniqueDeviceId != null ? !uniqueDeviceId.equals(that.uniqueDeviceId) : that.uniqueDeviceId != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = uniqueDeviceId != null ? uniqueDeviceId.hashCode() : 0;
            result = 31 * result + (state != null ? state.hashCode() : 0);
            return result;
        }
    }
    private MessageDTO data;

    /**
     * Creates a message to dispatch a DeviceDiscoveryEvent to another device.
     * @param uniqueDeviceId the uniqueDeviceId of the device that was discovered
     * @param state the state of the discovered device
     * @param connectionMetaDataList the connection meta data of the just discovered device
     */
	public DiscoveredDeviceAdminMessage(String uniqueDeviceId, State state, List<ConnectionMetaDataDTO> connectionMetaDataList) {
		super(CLASSIFIER_DISCOVERED_DEVICE);
        this.data = new MessageDTO();
        this.data.uniqueDeviceId = uniqueDeviceId;
        this.data.connectionMetaDataList = connectionMetaDataList;
        this.data.state = state;
	}

    /**
     * Creates a message to dispatch a DeviceDiscoveryEvent to another device.
     * @param deviceDiscoveryEvent a device discovery event to be dispatched
     */
    public DiscoveredDeviceAdminMessage(AbstractBlaubotDeviceDiscoveryEvent deviceDiscoveryEvent) {
        this(deviceDiscoveryEvent.getRemoteDevice().getUniqueDeviceID(), deviceDiscoveryEvent.getRemoteDeviceState(), deviceDiscoveryEvent.getConnectionMetaData());
    }

	public DiscoveredDeviceAdminMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}

    @Override
    protected byte[] payloadToBytes() {
        String json = gson.toJson(this.data);
        return json.getBytes(BlaubotConstants.STRING_CHARSET);
    }

    /**
     * Creates a new discovery event based on this message
     * @return the discovery event
     */
    public AbstractBlaubotDeviceDiscoveryEvent createDiscoveryEvent() {
        return data.state.createDiscoveryEventForDevice(new BlaubotDevice(data.uniqueDeviceId), data.connectionMetaDataList);
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

	@Override
	public String toString() {
		return "DiscoveredDeviceAdminMessage [uniqueDeviceId=" + data.uniqueDeviceId + ", connectionMetaDataList=" + data.connectionMetaDataList + ", state=" + data.state + "]";
	}
	
	
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DiscoveredDeviceAdminMessage that = (DiscoveredDeviceAdminMessage) o;

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
