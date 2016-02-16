package eu.hgross.blaubot.core.statemachine.events;

import java.util.ArrayList;
import java.util.List;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

/**
 * Wraps Discovery events from the beacons into state machine events.
 * Note that getConnectionStateMachineState retrieves the state machine's staet on THIS device, not the remote device.
 * To retrieve the remote device's state use getRemoteDeviceState() or check with instanceof.
 *
 */
public abstract class AbstractBlaubotDeviceDiscoveryEvent extends AbstractBlaubotStateMachineEvent {
    /**
     * Can be used in a subclass implementation
     */
    protected List<ConnectionMetaDataDTO> connectionMetaDataDTOList = new ArrayList<>();

    /**
     * Has to be set in the subclass implementation (injection or constructor)
     */
	protected IBlaubotDevice remoteDevice;

    /**
     * Gets the remote device for which a state was retrieved
     * @return the remote device for which we retrieved state informations
     */
    public IBlaubotDevice getRemoteDevice() {
		return this.remoteDevice;
	}

    /**
     * The remote device's state.
     * @return the state of the remote device
     */
    public abstract State getRemoteDeviceState();

    /**
     * The connection meta data objects for all of the remote device's acceptors
     * @return connection meta data informations
     */
    public abstract List<ConnectionMetaDataDTO> getConnectionMetaData();

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("AbstractBlaubotDeviceDiscoveryEvent{");
        sb.append("connectionMetaDataDTOList=").append(connectionMetaDataDTOList);
        sb.append(", remoteDevice=").append(remoteDevice);
        sb.append(", deviceState=").append(getRemoteDeviceState());
        sb.append('}');
        return sb.toString();
    }
}
