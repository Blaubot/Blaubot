package eu.hgross.blaubot.core;

import java.util.List;

import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;

/**
 * Contains helper methods to handle beacon messages
 */
public class BeaconHelper {

    /**
     * Given a beaconMessage and the listener, this method generates and populates the discovery events
     * for the remote end's device as well as it's king (if any).
     * 
     * @param beaconMessage
     * @param discoveryEventListener
     */
    public static void populateEventsFromBeaconMessage(BeaconMessage beaconMessage, IBlaubotDiscoveryEventListener discoveryEventListener) {
        if (discoveryEventListener == null) {
            throw new NullPointerException("DiscoveryEventListener was null");
        }
        final State currentState = beaconMessage.getCurrentState();
        final IBlaubotDevice device = new BlaubotDevice(beaconMessage.getUniqueDeviceId());

        // create and populate discovery event for partner
        final AbstractBlaubotDeviceDiscoveryEvent discoveryEventForPartner = currentState.createDiscoveryEventForDevice(device, beaconMessage.getOwnConnectionMetaDataList());
        discoveryEventListener.onDeviceDiscoveryEvent(discoveryEventForPartner);

        // if partner has a king, create an event for the king as well
        if (!beaconMessage.getKingDeviceUniqueId().isEmpty()) {
            final String kingDeviceUniqueId = beaconMessage.getKingDeviceUniqueId();
            final List<ConnectionMetaDataDTO> kingsConnectionMetaDataList = beaconMessage.getKingsConnectionMetaDataList();
            final BlaubotDevice kingDevice = new BlaubotDevice(kingDeviceUniqueId);
            final AbstractBlaubotDeviceDiscoveryEvent discoveryEventForPartnersKing = State.King.createDiscoveryEventForDevice(kingDevice, kingsConnectionMetaDataList);
            discoveryEventListener.onDeviceDiscoveryEvent(discoveryEventForPartnersKing);
        }
    }
}
