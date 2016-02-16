package eu.hgross.blaubot.core.acceptor.discovery;

import java.util.List;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;

/**
 * A data store for beacon events and therefore last known states and connectivity informations
 */
public interface IBlaubotBeaconStore {

    /**
     * Get the last known connection meta data for a device
     * @param uniqueDeviceId the unique device id of the device for which the connection meta data should be retrieved
     * @return the last known connection meta data list for the device or null, if unknown
     */
    public List<ConnectionMetaDataDTO> getLastKnownConnectionMetaData(String uniqueDeviceId);


    /**
     * Retrieve the last known state for a device
     * @param uniqueDeviceId the device's unique id
     * @return the last known state or null, if unknown
     */
    public State getLastKnownState(String uniqueDeviceId);

    /**
     * Get the last received discovery event for a given uniqueDeviceId
     *
     * @param uniqueDeviceID the device id
     * @return the last event or null, if no event received so far
     */
    public AbstractBlaubotDeviceDiscoveryEvent getLastDiscoveryEvent(String uniqueDeviceID);

    /**
     * Adds a discovery event by beacon message to the store.
     *
     * @param theirBeaconMessage the discovery event to add
     * @param remoteDevice the remote device from which the message was received
     */
    public void putDiscoveryEvent(BeaconMessage theirBeaconMessage, IBlaubotDevice remoteDevice);

    /**
     * Puts connection meta data received from anywhere to the store (mostly used in acceptors and the
     * AdminMessageBeacon)
     *
     * @param uniqueDeviceId the unique device id
     * @param connectionMetaDataList the device's connection meta data
     */
    public void putConnectionMetaData(String uniqueDeviceId, List<ConnectionMetaDataDTO> connectionMetaDataList);
}
