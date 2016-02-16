package eu.hgross.blaubot.geobeacon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.TimeoutList;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.BlaubotMessageReceiver;
import eu.hgross.blaubot.messaging.BlaubotMessageSender;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.util.Log;

/**
 * A simple server that receives messages from beacons containing their state and connection metadata
 * and some geolocation informations.
 * This information is then used to inform nearby, connected beacons about status updates
 */
public class GeoBeaconServer {
    private static final String LOG_TAG = "GeoBeaconServer";
    private Object startStopMonitor = new Object();

    /**
     * The acceptors by which the server can be reached
     */
    private final List<IBlaubotConnectionAcceptor> acceptors;
    /**
     * UniqueDeviceId -> Last GeoBeaconMessage
     */
    private TimeoutList<GeoBeaconMessage> geoBeaconMessages;
    /**
     * the radius in KM in which devices are notified about "nearby" devices
     */
    private final double geoRadius;
    /**
     * by uniquedeviceid and beaconUuid
     */
    private Set<GeoBeaconServerClient> clients;
    /**
     * This set contains all clients, for which an "initial message set" was sent.
     * This means if a client connects for the first time, it is not in this set.
     * If this client sends a GeoBeaconMessage, we gather all GeoBeaconMEssages
     * of devices nearby to this client and send them to the client and add the
     * client to this set.
     * If the client disconnects, it is removed from the set.
     */
    private Set<GeoBeaconServerClient> sentInitialMessageSet;


    /**
     * Creates the server using the given acceptors.
     * Make sure to add the corresponding connectors and connection data to the GeoBeacon.
     * @param geoRadius the radius in KM in which devices are notified about "nearby" devices
     * @param acceptors the acceptors to use
     */
    public GeoBeaconServer(double geoRadius, IBlaubotConnectionAcceptor... acceptors) {
        this.geoRadius = geoRadius;
        this.acceptors = Arrays.asList(acceptors);
        this.geoBeaconMessages = new TimeoutList(GeoBeaconConstants.MAX_AGE_BEACON_MESSAGES);
        this.clients = Collections.newSetFromMap(new ConcurrentHashMap<GeoBeaconServerClient, Boolean>());
        this.sentInitialMessageSet = Collections.newSetFromMap(new ConcurrentHashMap<GeoBeaconServerClient, Boolean>());
        for (IBlaubotConnectionAcceptor acceptor : acceptors) {
            acceptor.setAcceptorListener(acceptorListener);
        }
    }

    /**
     * Called when a GeoLocationBeacon sends data.
     */
    private IBlaubotMessageListener messageListener = new IBlaubotMessageListener() {
        @Override
        public void onMessage(BlaubotMessage blaubotMessage) {
            final GeoBeaconMessage geoBeaconMessage = GeoBeaconUtil.blaubotMessageToGeoBeaconMessage(blaubotMessage);
            geoBeaconMessages.report(geoBeaconMessage);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Got GeoBeaconMessage from " + geoBeaconMessage.getBeaconMessage().getUniqueDeviceId() + ": " + geoBeaconMessage);
            }
            notifyBeacons(geoBeaconMessage);
        }
    };

    /**
     * Called when a client disconnects.
     */
    private IBlaubotConnectionListener disconnectListener = new IBlaubotConnectionListener() {
        @Override
        public void onConnectionClosed(IBlaubotConnection connection) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "GeoBeaconClient disconnected: " + connection.getRemoteDevice().getUniqueDeviceID());
            }

            GeoBeaconServerClient toRemove = null;
            // find our client and remove from set on disconnect
            for (GeoBeaconServerClient curCl : clients) {
                if (curCl.getConnection().getRemoteDevice().getUniqueDeviceID().equals(connection.getRemoteDevice().getUniqueDeviceID())) {
                    curCl.getMessageReceiver().removeMessageListener(messageListener);
                    toRemove = curCl;
                }
            }
            if (toRemove != null) {
                clients.remove(toRemove);
                sentInitialMessageSet.remove(toRemove);
            }
        }
    };

    /**
     * Handles all incoming connections
     */
    private final IBlaubotIncomingConnectionListener acceptorListener = new IBlaubotIncomingConnectionListener() {
        @Override
        public void onConnectionEstablished(IBlaubotConnection connection) {
            connection.addConnectionListener(disconnectListener);
            GeoBeaconServerClient client = new GeoBeaconServerClient(connection);
            client.getMessageReceiver().addMessageListener(messageListener);
            clients.add(client);
            client.activate();
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "New GeoBeaconClient: " + client);
                Log.d(LOG_TAG, "Items: " + geoBeaconMessages.getItems());
            }
//            notifyOneBeacon(client);
        }
    };

    /**
     * Starts the beacon server
     */
    public void startBeaconServer() {
        final boolean allStarted = BlaubotAdapterHelper.startedCount(acceptors, null) == acceptors.size();
        if (allStarted) {
            return;
        }
        synchronized (startStopMonitor) {
            BlaubotAdapterHelper.startAcceptors(acceptors);
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "GeoBeaconServer started.");
        }
    }

    /**
     * Stops the beacon server
     */
    public void stopBeaconServer() {
        if (BlaubotAdapterHelper.startedCount(acceptors, null) == 0) {
            return;
        }
        synchronized (startStopMonitor) {
            BlaubotAdapterHelper.stopAcceptors(acceptors);
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "GeoBeaconServer stopped.");
        }
    }

    /**
     * Given a message notifies connected beacons nearby to the coordinates given in the message.
     *
     * @param message the message
     */
    private void notifyBeacons(GeoBeaconMessage message) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Notifying clients ...");
        }
        // we gather all beacon messages in a radius around message's radius and
        // then we check if a client exists for each of the gathered messages
        // if yes, we send the message to these clients
        // there is a special case: If a client freshly connected, he does not know
        // anything about the other devices around him after sending "message"
        // for this case we have a set that contains all clients for which a 
        // "initial message set" was send. If the client for message.beaconmessage.unqiueDeviceId
        // is not in this set, we send all messages that we gathered before to this client
        // and add this client to the set

        // filter by nearby beacons (geodata) and beaconUUID
        Collection<GeoBeaconMessage> nearbyBeaconMessages = gatherNearbyBeaconMessages(message);
        Collection<GeoBeaconServerClient> nearbyClients = getClientsByBeaconMessageCollection(nearbyBeaconMessages);

        // send the just received message to all nearby clients
        for (GeoBeaconServerClient client : nearbyClients) {
            String uniqueDeviceID = client.getConnection().getRemoteDevice().getUniqueDeviceID();
            if (uniqueDeviceID.equals(message.getBeaconMessage().getUniqueDeviceId())) {
                continue; // don't echo
            }
            BlaubotMessage msg = GeoBeaconUtil.geoBeaconMessageToBlaubotMessage(message);
            client.getMessageSender().sendMessage(msg);
        }

        // get the client that send the message
        GeoBeaconServerClient sender = null;
        for (GeoBeaconServerClient client : clients) {
            if (client.getConnection().getRemoteDevice().equals(message.getBeaconMessage().getUniqueDeviceId())) {
                sender = client;
                break;
            }
        }
        if (sender != null && !sentInitialMessageSet.contains(sender)) {
            // the sender never got a full update of all nearby messages, so we will send them to him
            for (GeoBeaconMessage geoBeaconMessage : nearbyBeaconMessages) {
                // send the just received message to all nearby clients
                String uniqueDeviceID = sender.getConnection().getRemoteDevice().getUniqueDeviceID();
                if (uniqueDeviceID.equals(geoBeaconMessage.getBeaconMessage().getUniqueDeviceId())) {
                    continue; // don't echo the just received msg
                }
                BlaubotMessage msg = GeoBeaconUtil.geoBeaconMessageToBlaubotMessage(geoBeaconMessage);
                sender.getMessageSender().sendMessage(msg);
            }
        }
    }

    /**
     * Given a collection of GeoBeaconMessages, this methods returns the collection of connected clients
     * for this messages.
     *
     * @param beaconMessages the beacon messages to get the clients for
     * @return the client collection
     */
    private Collection<GeoBeaconServerClient> getClientsByBeaconMessageCollection(Collection<GeoBeaconMessage> beaconMessages) {
        final Set<String> uniqueDeviceIdSet = new HashSet<>();
        for (GeoBeaconMessage message : beaconMessages) {
            uniqueDeviceIdSet.add(message.getBeaconMessage().getUniqueDeviceId());
        }

        final Set<GeoBeaconServerClient> clientSet = new HashSet<>();
        for (GeoBeaconServerClient geoBeaconServerClient : clients) {
            String uniqueDeviceID = geoBeaconServerClient.getConnection().getRemoteDevice().getUniqueDeviceID();
            if (uniqueDeviceIdSet.contains(uniqueDeviceID)) {
                clientSet.add(geoBeaconServerClient);
            }
        }

        return clientSet;
    }

    /**
     * Based on the configured radius gathers all nearby GeoBeaconMessages around message.
     * Also filters by beacon uuid of message.
     *
     * @param message the message (center)
     * @return the list of messages surrounding message by the defined radius
     */
    private Collection<GeoBeaconMessage> gatherNearbyBeaconMessages(GeoBeaconMessage message) {
        ArrayList<GeoBeaconMessage> list = new ArrayList<>();
        for (GeoBeaconMessage geoBeaconMessage : geoBeaconMessages.getItems()) {
            if (!geoBeaconMessage.getBeaconUuid().equals(message.getBeaconUuid())) {
                continue; // wrong uuid
            }
            GeoData geoData = geoBeaconMessage.getGeoData();
            if (geoData != null && message.getGeoData() != null) {
                double distance = GeoBeaconUtil.distanceBetweenGeoBeaconMessages(geoData, message.getGeoData());
                if (distance <= geoRadius) {
                    // if it is nearby, add
                    list.add(geoBeaconMessage);
                }
            } else {
                Log.w(LOG_TAG, "No geodata available. Ignoring GEO_RADIUS and putting it into the list");
                list.add(geoBeaconMessage);
            }
        }
        return list;
    }

    /**
     * Notifies one beacon (client) about the messages received from nearby devices (if any).
     *
     * @param geoBeaconServerClient
     */
    private void notifyOneBeacon(GeoBeaconServerClient geoBeaconServerClient) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Notifying one client ...");
        }
        final BlaubotMessageSender messageSender = geoBeaconServerClient.getMessageSender();
        // TODO filter by nearby beacons (geodata) and beaconUUID
        // send all messages for now
        for (GeoBeaconMessage geoBeaconMessage : geoBeaconMessages.getItems()) {
            final BlaubotMessage blaubotMessage = GeoBeaconUtil.geoBeaconMessageToBlaubotMessage(geoBeaconMessage);
            messageSender.sendMessage(blaubotMessage);
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Done notifying one client.");
        }
    }

    /**
     * Returns the acceptors on which this server is listening on.
     * 
     * @return the list of acceptors
     */
    public List<IBlaubotConnectionAcceptor> getAcceptors() {
        return acceptors;
    }

    /**
     * Given an IBlaubotConneciton, this objects holds the sender and receiver.
     */
    private static class GeoBeaconServerClient {
        private IBlaubotConnection connection;
        private BlaubotMessageSender messageSender;
        private BlaubotMessageReceiver messageReceiver;

        public GeoBeaconServerClient(IBlaubotConnection connection) {
            this.connection = connection;
            this.connection.addConnectionListener(new IBlaubotConnectionListener() {
                @Override
                public void onConnectionClosed(IBlaubotConnection connection) {
                    deactivate();
                }
            });
            this.messageReceiver = new BlaubotMessageReceiver(connection);
            this.messageSender = new BlaubotMessageSender(connection);
        }

        /**
         * Activates sender and receiver.
         */
        public void activate() {
            messageSender.activate();
            messageReceiver.activate();
        }

        /**
         * Deactivates sender and receiver.
         */
        public void deactivate() {
            if (messageSender != null) {
                messageSender.deactivate(null);
            }
            if (messageReceiver != null) {
                messageReceiver.deactivate(null);
            }
        }

        public IBlaubotConnection getConnection() {
            return connection;
        }

        public BlaubotMessageSender getMessageSender() {
            return messageSender;
        }

        public BlaubotMessageReceiver getMessageReceiver() {
            return messageReceiver;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            GeoBeaconServerClient that = (GeoBeaconServerClient) o;

            return connection.equals(that.connection);

        }

        @Override
        public int hashCode() {
            return connection.hashCode();
        }
    }
}

