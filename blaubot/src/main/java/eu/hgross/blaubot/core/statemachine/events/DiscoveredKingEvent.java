package eu.hgross.blaubot.core.statemachine.events;

import java.util.List;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

public class DiscoveredKingEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredKingEvent(IBlaubotDevice d, List<ConnectionMetaDataDTO> connectionMetaDataDTOs) {
		if(d == null) {
            throw new NullPointerException();
        }
        this.connectionMetaDataDTOList.addAll(connectionMetaDataDTOs);
		this.remoteDevice = d;
	}

    @Override
    public State getRemoteDeviceState() {
        return State.King;
    }

    @Override
    public List<ConnectionMetaDataDTO> getConnectionMetaData() {
        return connectionMetaDataDTOList;
    }
}