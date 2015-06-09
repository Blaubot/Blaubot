package eu.hgross.blaubot.core;

import java.util.List;

import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.events.DiscoveredKingEvent;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.admin.ACKPronouncePrinceAdminMessage;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.BowDownToNewKingAdminMessage;
import eu.hgross.blaubot.admin.DiscoveredDeviceAdminMessage;
import eu.hgross.blaubot.admin.PrinceFoundAKingAdminMessage;
import eu.hgross.blaubot.admin.PronouncePrinceAdminMessage;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.util.Log;

/**
 * A beacon implementation that is added to every blaubot instance.
 * It does not discover anything but hears for admin messages to emulate discovery events.
 * That is if a prince found a king and sends the PrinceFoundAKing Admin message.
 */
public class AdminMessageBeacon implements IBlaubotBeacon, IBlaubotAdminMessageListener {
    private static final String LOG_TAG = "AdminMessageBeacon";
    private IBlaubotDiscoveryEventListener discoveryEventListener;
    private IBlaubotBeaconStore beaconStore;
    private IBlaubotListeningStateListener listeningStateListener;
    private volatile boolean started;
    private Blaubot blaubot;

    @Override
    public void onAdminMessage(AbstractAdminMessage adminMessage) {
        if(adminMessage instanceof PrinceFoundAKingAdminMessage) {
            final PrinceFoundAKingAdminMessage princeFoundAKingAdminMessage = (PrinceFoundAKingAdminMessage) adminMessage;
            IBlaubotDevice discoveredKingDevice = new BlaubotDevice(princeFoundAKingAdminMessage.getKingsUniqueDeviceId());
            final List<ConnectionMetaDataDTO> connectionMetaDataList = princeFoundAKingAdminMessage.getConnectionMetaDataList();
            final DiscoveredKingEvent event = new DiscoveredKingEvent(discoveredKingDevice, connectionMetaDataList);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Propagating DiscoveredKing event ");
            }

            // trigger listener
            if (discoveryEventListener != null ) {
                discoveryEventListener.onDeviceDiscoveryEvent(event);
            }

        } else if(adminMessage instanceof BowDownToNewKingAdminMessage) {
            final BowDownToNewKingAdminMessage bowDownToNewKingAdminMessage = (BowDownToNewKingAdminMessage) adminMessage;
            final List<ConnectionMetaDataDTO> newKingsConnectionMetaDataList = bowDownToNewKingAdminMessage.getNewKingsConnectionMetaDataList();
            final String kingsUniqueDeviceId = bowDownToNewKingAdminMessage.getNewKingsUniqueDeviceId();
            beaconStore.putConnectionMetaData(kingsUniqueDeviceId, newKingsConnectionMetaDataList);
        } else if(adminMessage instanceof PronouncePrinceAdminMessage) {
            final PronouncePrinceAdminMessage pronouncePrinceAdminMessage = (PronouncePrinceAdminMessage) adminMessage;
            final String uniqueDeviceId = pronouncePrinceAdminMessage.getUniqueDeviceId();
            final List<ConnectionMetaDataDTO> connectionDataList = pronouncePrinceAdminMessage.getConnectionDataList();
            if(uniqueDeviceId != null && connectionDataList != null) {
                // possible if king never got a beacon message from the prince before
                beaconStore.putConnectionMetaData(uniqueDeviceId, connectionDataList);
            }
        } else if(adminMessage instanceof ACKPronouncePrinceAdminMessage) {
            final ACKPronouncePrinceAdminMessage ackAdminMessage = (ACKPronouncePrinceAdminMessage) adminMessage;
            final String uniqueDeviceId = ackAdminMessage.getUniqueDeviceId();
            final List<ConnectionMetaDataDTO> connectionDataList = ackAdminMessage.getConnectionDataList();
            if(uniqueDeviceId == null && connectionDataList == null) {
                // should never happen
                throw new RuntimeException("Got incomplete data in prince ack: " + adminMessage);
            }
            beaconStore.putConnectionMetaData(uniqueDeviceId, connectionDataList);
        } else if(adminMessage instanceof DiscoveredDeviceAdminMessage) {
            final DiscoveredDeviceAdminMessage discoveredDeviceAdminMessage = (DiscoveredDeviceAdminMessage) adminMessage;
            final AbstractBlaubotDeviceDiscoveryEvent discoveryEvent = discoveredDeviceAdminMessage.createDiscoveryEvent();

            // trigger listener
            if (discoveryEventListener != null ) {
                discoveryEventListener.onDeviceDiscoveryEvent(discoveryEvent);
            }
        }
    }

    @Override
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
    public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
        this.discoveryEventListener = discoveryEventListener;
    }

    @Override
    public void onConnectionStateMachineStateChanged(IBlaubotState state) {
        // ignore
    }

    @Override
    public void setDiscoveryActivated(boolean active) {
        // ignore, we don't discover
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return null; // ignore, no problem
    }

    @Override
    public void startListening() {
        // call listener
        started = true;
        if (listeningStateListener != null) {
            listeningStateListener.onListeningStarted(this);
        }
    }

    @Override
    public void stopListening() {
        // call listener
        started = false;
        if (listeningStateListener != null) {
            listeningStateListener.onListeningStopped(this);
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
        this.listeningStateListener = stateListener;
    }

    @Override
    public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
        // ignore
    }

    @Override
    public ConnectionMetaDataDTO getConnectionMetaData() {
        // ignore
        return null;
    }
}
