package eu.hgross.blaubot.core.statemachine.events;

import java.util.List;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

public class DiscoveredFreeEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredFreeEvent(IBlaubotDevice device, List<ConnectionMetaDataDTO> connectionMetaDataDTOs) {
		this.connectionMetaDataDTOList.addAll(connectionMetaDataDTOs);
        this.remoteDevice = device;
	}

    @Override
    public State getRemoteDeviceState() {
        return State.Free;
    }

    @Override
    public List<ConnectionMetaDataDTO> getConnectionMetaData() {
        return connectionMetaDataDTOList;
    }
}
