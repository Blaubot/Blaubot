package eu.hgross.blaubot.core.statemachine.events;

import java.util.List;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

public class DiscoveredPrinceEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredPrinceEvent(IBlaubotDevice d, List<ConnectionMetaDataDTO> connectionMetaDataDTOs) {
		this.connectionMetaDataDTOList.addAll(connectionMetaDataDTOs);
        this.remoteDevice = d;
	}

    @Override
    public State getRemoteDeviceState() {
        return State.Prince;
    }

    @Override
    public List<ConnectionMetaDataDTO> getConnectionMetaData() {
        return connectionMetaDataDTOList;
    }
}
