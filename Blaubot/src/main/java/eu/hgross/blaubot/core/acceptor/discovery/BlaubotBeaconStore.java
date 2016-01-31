package eu.hgross.blaubot.core.acceptor.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;

/**
 * Default IBlaubotBeaconStore implementation.
 */
public class BlaubotBeaconStore implements IBlaubotBeaconStore, IBlaubotDiscoveryEventListener {
    private static final String LOG_TAG = "BlaubotBeaconStore";
    /**
     * If the ConnectionManager is attached to the BeaconService, it receives the DiscoveryEvents from the beacons
     * and always holds the last received discovery event for an unique device id.
     * These events hold the needed data to connect to the acceptors of these devices.
     */
    private final ConcurrentHashMap<String, AbstractBlaubotDeviceDiscoveryEvent> lastBeaconEvents;
    private final ConcurrentHashMap<String, List<ConnectionMetaDataDTO>> lastConnectiontMetaData;

    public BlaubotBeaconStore() {
        this.lastBeaconEvents = new ConcurrentHashMap<>();
        this.lastConnectiontMetaData = new ConcurrentHashMap<>();
    }

    @Override
    public List<ConnectionMetaDataDTO> getLastKnownConnectionMetaData(String uniqueDeviceId) {
        return lastConnectiontMetaData.get(uniqueDeviceId);
    }

    @Override
    public State getLastKnownState(String uniqueDeviceId) {
        final AbstractBlaubotDeviceDiscoveryEvent lastDiscoveryEvent = getLastDiscoveryEvent(uniqueDeviceId);
        if (lastDiscoveryEvent == null) {
            return null;
        }
        return lastDiscoveryEvent.getRemoteDeviceState();
    }

    @Override
    public AbstractBlaubotDeviceDiscoveryEvent getLastDiscoveryEvent(String uniqueDeviceID) {
        return this.lastBeaconEvents.get(uniqueDeviceID);
    }

    @Override
    public void putDiscoveryEvent(BeaconMessage theirBeaconMessage, IBlaubotDevice remoteDevice) {
        // create event and put it to the store (no populating!)
        final eu.hgross.blaubot.core.State theirState = theirBeaconMessage.getCurrentState();
        AbstractBlaubotDeviceDiscoveryEvent discoveryEvent = theirState.createDiscoveryEventForDevice(remoteDevice, theirBeaconMessage.getOwnConnectionMetaDataList());
        onDeviceDiscoveryEvent(discoveryEvent);
    }

    @Override
    public void putConnectionMetaData(String uniqueDeviceId, List<ConnectionMetaDataDTO> connectionMetaDataList) {
        this.lastConnectiontMetaData.put(uniqueDeviceId, connectionMetaDataList);
    }

    public void putConnectionMetaData(String uniqueDeviceId, ConnectionMetaDataDTO connectionMetaData) {
        final ArrayList<ConnectionMetaDataDTO> dtos = new ArrayList<>();
        dtos.add(connectionMetaData);
        this.lastConnectiontMetaData.put(uniqueDeviceId, dtos);
    }
    
    @Override
    public void onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
        final String uniqueDeviceID = discoveryEvent.getRemoteDevice().getUniqueDeviceID();
        this.lastBeaconEvents.put(uniqueDeviceID, discoveryEvent);
        putConnectionMetaData(uniqueDeviceID, discoveryEvent.getConnectionMetaData());
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BlaubotBeaconStore{");
        sb.append("lastConnectiontMetaData=").append(lastConnectiontMetaData);
        sb.append(", lastBeaconEvents=").append(lastBeaconEvents);
        sb.append('}');
        return sb.toString();
    }
}
