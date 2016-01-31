package eu.hgross.blaubot.geobeacon;

import com.google.gson.Gson;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.core.BeaconHelper;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.BlaubotMessageReceiver;
import eu.hgross.blaubot.messaging.BlaubotMessageSender;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.util.Log;

/**
 * A Beacon that commits its current state to a server flavoured with geolocation data
 * to get updates about nearby devices that do the same.
 * <p/>
 * Does not commit updates, if in passive mode.
 * GeoLocation data has to be commited to this beacon instance by calling setGeoData(...). 
 * 
 * This beacon class should be subclassed to integrate the platform specific geo location
 * provider into the subclass' internals. Call setGeoData() to provide this data.
 */
public abstract class GeoLocationBeacon implements IBlaubotBeacon {
    private static final String LOG_TAG = "GeoLocationBeacon";
    private boolean discoveryActive;
    private Gson gson;
    private Blaubot blaubot;
    private IBlaubotBeaconStore beaconStore;
    private IBlaubotListeningStateListener listeningStateListener;
    private IBlaubotIncomingConnectionListener acceptorListener;
    private IBlaubotDiscoveryEventListener discoveryEventListener;
    private final List<IBlaubotConnector> connectors;
    /**
     * The last known csm state to adjust some timings for republishing.
     */
    private State currentState;

    /**
     * Timestamp of the last beacon message publishing
     */
    private long lastGeoBeaconMessagePublish;

    /**
     * receiver of the current connection to the beacon server
     */
    private BlaubotMessageReceiver currentGeoBeaconConnectionMessageReceiver;
    /**
     * sender of the current connection to the beacon server
     */
    private BlaubotMessageSender currentGeoBeaconConnectionMessageSender;

    /**
     * The current beacon message containing the state of the csm
     */
    private BeaconMessage currentBeaconMessage;
    /**
     * The last known geo data for this device.
     * Will be published to the GeoBeaconServer
     */
    private GeoData currentGeoData;

    /**
     * The executor service used to connect to the GeoBeaconServer
     */
    private ScheduledExecutorService beaconServerConnectExecutor;
    private static final long CONNECT_PERIOD = 3000;
    private Runnable connectTask = new Runnable() {
        @Override
        public synchronized void run() {
            if (currentGeoBeaconConnectionMessageSender != null) {
                return; // do nothing, if connceted
            }
            // connect
            for (IBlaubotConnector connector : connectors) {
                final IBlaubotConnection geoBeaconServerConnection = connector.connectToBlaubotDevice(new BlaubotDevice(GeoBeaconConstants.GEO_BEACON_SERVER_UNIQUE_DEVICE_ID));
                if (geoBeaconServerConnection != null) {
                    currentGeoBeaconConnectionMessageSender = new BlaubotMessageSender(geoBeaconServerConnection);
                    currentGeoBeaconConnectionMessageReceiver = new BlaubotMessageReceiver(geoBeaconServerConnection);
                    currentGeoBeaconConnectionMessageReceiver.addMessageListener(messageListener);
                    currentGeoBeaconConnectionMessageSender.activate();
                    currentGeoBeaconConnectionMessageReceiver.activate();
                    try {
                        publishGeoBeaconMessageToServer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }

        }
    };

    /**
     * We need to periodically re-publish the message to the server because there is a fixed max age
     * for the records until they get erased. This executor will re-publish if needed
     */
    private ScheduledExecutorService republishExecutor;
    private static final long REPUBLISH_TASK_PERIOD = 3000;
    private Runnable republishTask = new Runnable() {
        /**
         * Checks whether we need to update the state at the server because the last republish is too old
         * @return true, iff we should update
         */
        private boolean doRepublish() {
            final long now = System.currentTimeMillis();
            // we either use the max age (very slow re-publishs), if we are in a network 
            // or we send pretty fast, if we are eager to find partners
            final double MAX_PERIOD_SINCE_LAST_PUBLISH = currentState != null && currentState == State.Free ? REPUBLISH_TASK_PERIOD : GeoBeaconConstants.MAX_AGE_BEACON_MESSAGES * 0.75;
            return (now - lastGeoBeaconMessagePublish) > MAX_PERIOD_SINCE_LAST_PUBLISH;
        }

        @Override
        public void run() {
            if (!discoveryActive) {
                return;
            }

            if (doRepublish()) {
                publishGeoBeaconMessageToServer();
            }
        }
    };


    /**
     * Gets notified by the beacon server if new interesting beacons are nearby
     */
    private IBlaubotMessageListener messageListener = new IBlaubotMessageListener() {
        @Override
        public void onMessage(BlaubotMessage blaubotMessage) {
            final GeoBeaconMessage geoBeaconMessage = GeoBeaconUtil.blaubotMessageToGeoBeaconMessage(blaubotMessage);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Got message from GeoBeaconServer: " + geoBeaconMessage);
            }
            if (discoveryEventListener != null) {
                // create discovery event(s) and notify
                final BeaconMessage beaconMessage = geoBeaconMessage.getBeaconMessage();
                BeaconHelper.populateEventsFromBeaconMessage(beaconMessage, discoveryEventListener);
            }
        }
    };

    private UUID beaconUUID;

    /**
     * @param beaconStore The beacon store holding the connection meta data for the given connectors to connect to the GeoBeaconServer's acceptors.
     * @param connectors  connectors to be used to establish a connection to the beacon server
     */
    public GeoLocationBeacon(IBlaubotBeaconStore beaconStore, IBlaubotConnector... connectors) {
        this.connectors = Arrays.asList(connectors);
        for (IBlaubotConnector connector : connectors) {
            connector.setBeaconStore(beaconStore);
        }
        this.gson = new Gson();
    }

    @Override
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
        this.beaconUUID = blaubot.getUuidSet().getBeaconUUID();
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return null;
    }

    @Override
    public synchronized void startListening() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Starting GeoLocationBeacon ...");
        }
        if (beaconServerConnectExecutor != null) {
            // already started
            return;
        }
        // start connect thread
        this.beaconServerConnectExecutor = Executors.newSingleThreadScheduledExecutor();
        this.beaconServerConnectExecutor.scheduleWithFixedDelay(connectTask, 0, CONNECT_PERIOD, TimeUnit.MILLISECONDS);

        // start re-publish thread
        this.republishExecutor = Executors.newSingleThreadScheduledExecutor();
        this.republishExecutor.scheduleWithFixedDelay(republishTask, 0, REPUBLISH_TASK_PERIOD, TimeUnit.MILLISECONDS);

        // notify started
        if (this.listeningStateListener != null) {
            this.listeningStateListener.onListeningStarted(this);
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "GeoLocationBeacon started.");
        }
    }

    @Override
    public synchronized void stopListening() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Stopping GeoLocationBeacon ...");
        }
        // stop connect thread
        if (beaconServerConnectExecutor != null) {
            beaconServerConnectExecutor.shutdownNow();
            try {
                beaconServerConnectExecutor.awaitTermination(CONNECT_PERIOD * 10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {

            } finally {
                beaconServerConnectExecutor = null;
            }
        }
        if (republishExecutor != null) {
            republishExecutor.shutdownNow();
            try {
                republishExecutor.awaitTermination(REPUBLISH_TASK_PERIOD * 10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {

            } finally {
                republishExecutor = null;
            }
        }

        // disconnect connection and deactivate sender/receiver
        if (this.currentGeoBeaconConnectionMessageReceiver != null) {
            currentGeoBeaconConnectionMessageReceiver.getBlaubotConnection().disconnect();
            this.currentGeoBeaconConnectionMessageReceiver.deactivate(null);
            this.currentGeoBeaconConnectionMessageSender.deactivate(null);
            this.currentGeoBeaconConnectionMessageReceiver.removeMessageListener(messageListener);
            this.currentGeoBeaconConnectionMessageReceiver = null;
            this.currentGeoBeaconConnectionMessageSender = null;
        }

        // notify
        if (this.listeningStateListener != null) {
            this.listeningStateListener.onListeningStopped(this);
        }

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "GeoLocationBeacon stopped.");
        }
    }

    @Override
    public synchronized boolean isStarted() {
        return beaconServerConnectExecutor != null;
    }

    @Override
    public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
        this.listeningStateListener = stateListener;
    }

    @Override
    public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
        this.acceptorListener = acceptorListener;
    }

    @Override
    public ConnectionMetaDataDTO getConnectionMetaData() {
        // TODO separate the acceptor interface from the beacon interface
        return new ConnectionMetaDataDTO();
    }

    @Override
    public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
        this.discoveryEventListener = discoveryEventListener;
    }

    @Override
    public void onConnectionStateMachineStateChanged(IBlaubotState state) {
        this.currentState = State.getStateByStatemachineClass(state.getClass());
        this.currentBeaconMessage = blaubot.getConnectionStateMachine().getBeaconService().getCurrentBeaconMessage();
        publishGeoBeaconMessageToServer();
    }

    @Override
    public void setDiscoveryActivated(boolean active) {
        discoveryActive = active;
    }

    /**
     * Sets the current geo data
     *
     * @param geoData the best known location of this device
     */
    public void setGeoData(GeoData geoData) {
        this.currentGeoData = geoData;
        publishGeoBeaconMessageToServer();
    }

    /**
     * Builds and publishes the current GeoBeaconMessage to the server
     */
    private void publishGeoBeaconMessageToServer() {
        lastGeoBeaconMessagePublish = 0; // mark that we need to re-publish
        if (currentGeoBeaconConnectionMessageSender == null || currentBeaconMessage == null || beaconUUID == null) {
            return;
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Publishing state to beacon server ...");
        }
        GeoBeaconMessage geoBeaconMessage = new GeoBeaconMessage(currentBeaconMessage, currentGeoData, beaconUUID.toString());
        GeoBeaconMessageDTO dto = new GeoBeaconMessageDTO(geoBeaconMessage);
        byte[] geoMessageDtoBytes = gson.toJson(dto).getBytes(BlaubotConstants.STRING_CHARSET);
        BlaubotMessage msg = new BlaubotMessage();
        msg.setPayload(geoMessageDtoBytes);
        this.currentGeoBeaconConnectionMessageSender.sendMessage(msg);
        this.lastGeoBeaconMessagePublish = System.currentTimeMillis();
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Published state to beacon server.");
        }
    }

}
