package eu.hgross.blaubot.core.statemachine.events;

import java.util.List;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

public class DiscoveredStoppedEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredStoppedEvent(IBlaubotDevice device, List<ConnectionMetaDataDTO> connectionMetaDataDTOs) {
		this.connectionMetaDataDTOList.addAll(connectionMetaDataDTOs);
        this.remoteDevice = device;
	}

    @Override
    public State getRemoteDeviceState() {
        return State.Stopped;
    }

    @Override
    public List<ConnectionMetaDataDTO> getConnectionMetaData() {
        return connectionMetaDataDTOList;
    }
}
