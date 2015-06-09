package eu.hgross.blaubot.messaging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.AddSubscriptionAdminMessage;
import eu.hgross.blaubot.admin.AdminMessageFactory;
import eu.hgross.blaubot.admin.RemoveSubscriptionAdminMessage;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IActionListener;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;
import eu.hgross.blaubot.util.Log;

/**
 * Factory class for the creation of BlaubotChannel instances.
 * Keeps all created instances under control.
 *
 * TODO: more documentation here
 */
public class BlaubotChannelManager {
    private static final String LOG_TAG = "BlaubotChannelManager";
    private ConcurrentHashMap<Short, BlaubotChannel> channels;
    private ConcurrentHashMap<IBlaubotConnection, BlaubotMessageManager> messageManagers;
    private CopyOnWriteArrayList<IBlaubotAdminMessageListener> adminMessageListeners;
    private CopyOnWriteArrayList<IBlaubotSubscriptionChangeListener> subscriptionChangeListeners;
    private volatile boolean isMaster = false;
    private String ownUniqueDeviceId;
    /**
     * Synchronizes against subscription updates and initial state transfers
     */
    protected final Object subscriptionLock = new Object();

    /**
     * The message manager that represents the Master device's own connection (since it also acts
     * as a client to itself).
     */
    private volatile BlaubotMessageManager ownMessageManager;

    /**
     * @param ownUniqueDeviceId the own unique device id
     */
    public BlaubotChannelManager(String ownUniqueDeviceId) {
        this.ownUniqueDeviceId = ownUniqueDeviceId;
        this.channels = new ConcurrentHashMap<>();
        this.messageManagers = new ConcurrentHashMap<>();
        this.adminMessageListeners = new CopyOnWriteArrayList<>();
        this.subscriptionChangeListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * A listener that is attached to each managed channel of this channel manager that simply proxies
     * the events to the attached IBlaubotSubscriptionChangeListeners of this manager.
     */
    private IBlaubotSubscriptionChangeListener proxySubscriptionChangeListener = new IBlaubotSubscriptionChangeListener() {
        @Override
        public void onSubscriptionAdded(String uniqueDeviceId, short channelId) {
            notifySubscriptionAdded(uniqueDeviceId, channelId);
        }

        @Override
        public void onSubscriptionRemoved(String uniqueDeviceId, short channelId) {
            notifySubscriptionRemoved(uniqueDeviceId, channelId);
        }

        /**
         * Notifies the attached listeners that a subscription was added.
         * @param uniqueDeviceId the subscribing uniquedeviceid
         * @param channelId the channel id
         */
        private void notifySubscriptionAdded(String uniqueDeviceId, short channelId) {
            for (IBlaubotSubscriptionChangeListener listener : subscriptionChangeListeners) {
                listener.onSubscriptionAdded(uniqueDeviceId, channelId);
            }
        }

        /**
         * Notifies the attached listeners that a subscription was removed.
         * @param uniqueDeviceId the formerly subscribing uniquedeviceid
         * @param channelId the channel id
         */
        private void notifySubscriptionRemoved(String uniqueDeviceId, short channelId) {
            for (IBlaubotSubscriptionChangeListener listener : subscriptionChangeListeners) {
                listener.onSubscriptionRemoved(uniqueDeviceId, channelId);
            }
        }
    };



    /**
     * Creates a channel, if not already existent.
     * If the channel exists, the channel instance will be returned.
     *
     * @param channelId the channel id to create the channel for
     * @return channel instance corresponding to the given channelId
     * @throws java.lang.IllegalArgumentException if the channelId is smaller than 0
     */
    public IBlaubotChannel createOrGetChannel(short channelId) {
        if(channelId < 0) {
            throw new IllegalArgumentException("ChannelIds have to be greater or equal 0.");
        }
        BlaubotChannel channel = channels.get(channelId);
        boolean added = false;

        // we assume the channel is already there, which will be the case 99.xxx% of the time.
        // If not, we do the usual putIfAbsent handling.
        // This gains some performance, since this method is used internally very often.
        if (channel == null) {
            channel = new BlaubotChannel(channelId, this);
            added = channels.putIfAbsent(channelId, channel) == null;
        }
        channel = channels.get(channelId);
        if (added) {
            // ensure activation initially (if we have connections)
            maintainChannelActivation();
            // add proxy listener
            channel.addSubscriptionListener(proxySubscriptionChangeListener);
        }
        return channel;
    }

    /**
     * Sets this ChannelManager to master mode, meaning that this instance is managing all
     * communications or client mode.
     * If the previous state was the same, nothing happens.
     * All the connection managers will be discarded when called, meaning that all connections
     * that are still valid have to be added again.
     *
     * @param isMaster notify if the channel manager is now in master role
     */
    public void setMaster(final boolean isMaster) {
        final boolean prevState = this.isMaster;
        if(isMaster == prevState) {
            // nothing to do
            return;
        }
        this.isMaster = isMaster;

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "setMaster(" + isMaster + ") -> Deactivating all MessageManagers");
        }
        // discard all of the connection management
        ArrayList<CountDownLatch> latches = new ArrayList<>();
        for(final BlaubotMessageManager mm : messageManagers.values()) {
            final CountDownLatch latch = new CountDownLatch(1);
            latches.add(latch);
            mm.deactivate(new IActionListener() {
                @Override
                public void onFinished() {
                    latch.countDown();
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "MessageManager sucessfully stopped: " + mm);
                    }
                }
            });
        }

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Awaiting " + latches.size() + " MessageManagers to shut down ...");
        }
        // block until all managers are properly shut down
        for (CountDownLatch latch : latches) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                continue;
            }
        }

        // then forget about them
        messageManagers.clear();

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "All " + latches.size() + " MessageManagers shut down properly.");
        }

        if(isMaster) {
            // Add our own dummy connection
            final IBlaubotDevice masterDummyDevice = new BlaubotDevice(ownUniqueDeviceId);
            final BlaubotConnectionQueueMock sendingConnection = new BlaubotConnectionQueueMock(masterDummyDevice);
            final IBlaubotConnection receivingConnection = sendingConnection.getOtherEndpointConnection(masterDummyDevice);
            final BlaubotMessageReceiver receiver = new BlaubotMessageReceiver(receivingConnection);
            final BlaubotMessageSender sender = new BlaubotMessageSender(sendingConnection);
            final BlaubotMessageManager ownMm = new BlaubotMessageManager(sender, receiver, this);
            final boolean added = messageManagers.putIfAbsent(receivingConnection, ownMm) == null;
            if (added) {
                ownMessageManager = ownMm;
                ownMm.getMessageReceiver().addMessageListener(messageDispatcher);
                ownMm.activate();
            } else {
                throw new RuntimeException("Could not add reflexive connection to ChannelManager");
            }
        } else {
            // We transitioned from master to not master
            // forget all connections
            // -- all managers should be deactivated
            messageManagers.clear();
            ownMessageManager = null;
        }
    }

    /**
     * Adds a connection to be managed (receiving and sending)
     *
     * @param connection the connection to add
     */
    public void addConnection(IBlaubotConnection connection) {
        if(Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Adding connection: " + connection);
        }
        BlaubotMessageManager mm = new BlaubotMessageManager(connection, this);
        boolean added = messageManagers.putIfAbsent(connection, mm) == null;
        if(added) {
            connection.addConnectionListener(disconnectionListener);
            mm = messageManagers.get(connection);
            // regenerate the AddSubscriptionAdminMessages for each subscription of each
            // channel and add them to the queue before activating the message manager
            synchronized (subscriptionLock) {
                // synchronized against subscription message handling
                for(BlaubotChannel channel : channels.values()) {
                    final ConcurrentSkipListSet<String> subscriptions = channel.getSubscriptions();
                    for(String uniqueDeviceId : subscriptions) {
                        AddSubscriptionAdminMessage addSubscriptionAdminMessage = new AddSubscriptionAdminMessage(uniqueDeviceId, channel.getChannelConfig().getChannelId());
                        mm.getMessageSender().sendMessage(addSubscriptionAdminMessage.toBlaubotMessage());
                    }
                }
            }
            mm.getMessageReceiver().addMessageListener(messageDispatcher);
            // TODO: check when to activate - we could be disabled or something?
            mm.activate(); // starts and sends the subscriptions
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Added connection " + connection + ", send subscriptions and activated MessageManager for it.");
            }
        } else {
            // TODO: what about this case?? -> ignore?
            Log.e(LOG_TAG, "addConnection(..): Connection was not added (was already in map): " + connection);
            throw new RuntimeException("Connection added twice");
        }

        maintainChannelActivation();
    }

    /**
     * Removes a connection from the channel manager so it is not being managed anymore
     * @param connection the connection to remove
     */
    public void removeConnection(IBlaubotConnection connection) {
        if(Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Removing connection: " + connection);
        }
        connection.removeConnectionListener(disconnectionListener);
        BlaubotMessageManager mm = messageManagers.remove(connection);
        if (mm != null) {
            // remove listeners to incoming messages from the MessageManager and deactivate it.
            mm.getMessageReceiver().removeMessageListener(messageDispatcher);
            final CountDownLatch latch = new CountDownLatch(1);
            mm.deactivate(new IActionListener() {
                @Override
                public void onFinished() {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Removed connection and deactivated MessageManager for connection: " + connection);
            }
        } else {
            if(Log.logWarningMessages()) {
                Log.w(LOG_TAG, "removeConnection() was called for a connection that was not managed by this channel manager. Connection: " + connection + "; MessageManagers: " + messageManagers);
            }
        }

        maintainChannelActivation();
    }

    /**
     * Is attached to all connections that are added to the ChannelManager via addConnection(..)
     * simply to call removeConnection(..) on connection failures.
     */
    private final IBlaubotConnectionListener disconnectionListener = new IBlaubotConnectionListener() {
        @Override
        public void onConnectionClosed(IBlaubotConnection connection) {
            removeConnection(connection);
        }
    };

    /**
     * called internally whenever we need to make changes to the channel activation:
     *  - when connections are added
     *  - when connections are removed
     *  - when the channelmanager is deactivated
     *  - when the channelmanager is activated
     */
    private void maintainChannelActivation() {
        // if we have connections, we activate, otherwise deactivate all channels
        if (!hasConnections()) {
            deactivateChannels();
        } else {
            activateChannels();
        }
    }

    /**
     * MessageRouter
     * This is the central point where messages arrive from the managed IBlaubotConnections and will
     * be dispatched to the designated channels or admin message listeners.
     *
     * This listener is appended to all MessageReceivers.
     *
     * The counterparts (sending side) of this can be found in the publishTo*** methods.
     */
    private IBlaubotMessageListener messageDispatcher = new IBlaubotMessageListener() {
        private final String LOG_TAG = "BlaubotChannelManager.messageDispatcher";

        @Override
        public void onMessage(BlaubotMessage message) {
            final BlaubotMessageType messageType = message.getMessageType();

            // simple dispatcher logic: if the firstHop bit is set and we are master, we unset the flag
            // and re-send this message to all our connections (including ourselve).
            // The flag basically ensures, that a message passes the master once before (re)dispatched to
            // the destination.
            if(isMaster && messageType.isFirstHop()) {
                // -- the message need to be dispatched
                // we have to dispatch it to the right places
                // in every case we remove the firstHop flag, to let the receiver know, that the message
                // has reached it's final destination on receive.
                messageType.setIsFirstHop(false);
                // now we decide if we want to dispatch the message to all connected device (note: we are master)
                // or if we need to dispatch more granular
                if(messageType.isAdminMessage()) {
                    // -- this message is an adminMessage that needs to be broadcasted (dispatched to others) further.
                    publishToAllConnections(message);
                } else if (messageType.isKeepAliveMessage()) {
                    // keep alive messages are never broadcasted - assert that
                    throw new RuntimeException("Got a keepAlive message that should be broadcasted - that makes no sense.");
                } else {
                    // this message needs to be dispatched to subscribers
                    dispatchChannelMessage(message);
                }
                return;
            } else if(messageType.isFirstHop()) {
                // Can happen, if we transition to StoppedState
                if (Log.logWarningMessages()) {
                    Log.w(LOG_TAG, "Got a firstHop bit and I am not master.");
                }
            }

            // -- at this point, the incoming message was re-dispatched or destinated to us,
            // so we have to decide where it belongs on our local device (the right listeners).
            // find the message's destination
            // The logic here is the same for master and non-master devices.
            if(messageType.isAdminMessage()) {
                handleAdminMessage(message);
                return;
            } else if(messageType.isKeepAliveMessage()) {
                handleKeepAliveMessage(message);
                return;
            } else {
                // -- obviously meant to be dispatched to a channel.
                // get the channel
                short channelId = message.getChannelId();
                BlaubotChannel channel = (BlaubotChannel) createOrGetChannel(channelId);

                // notify listeners, if any
                channel.notify(message);
            }
        }

        /**
         * Inspects the message and dispatches it to the right BlaubotMessageManagers.
         * @param message the message to dispatch
         * @throws java.lang.IllegalStateException if not in master mode
         */
        private void dispatchChannelMessage(BlaubotMessage message) {
            final short channelId = message.getChannelId();
            if(!isMaster) {
                throw new IllegalStateException("");
            }
            if(channelId < 0) {
                throw new IllegalArgumentException("The message does not contain a valid channel id to be dispatched.");
            }

            BlaubotChannel channel = (BlaubotChannel) createOrGetChannel(channelId);
            final ConcurrentSkipListSet<String> subscriptions = channel.getSubscriptions();
            for(BlaubotMessageManager messageManager : messageManagers.values()) {
                final BlaubotMessageSender messageSender = messageManager.getMessageSender();
                final String uniqueDeviceID = messageSender.getBlaubotConnection().getRemoteDevice().getUniqueDeviceID();
                if(subscriptions.contains(uniqueDeviceID)) {
                    messageSender.sendMessage(message);
                }
            }
        }

        private void handleKeepAliveMessage(BlaubotMessage message) {
            //Log.d(LOG_TAG, "Got keep alive message");
            // TODO: anything needed here?
        }

        /**
         * Handles incoming AdminMessages for the messaging layer.
         * Note that other interested parties can listen to admin messages by adding listeners via addAdminMessageListener()
         *
         * @param message the received admin message to handle
         */
        private void handleAdminMessage(BlaubotMessage message) {
            AbstractAdminMessage adminMessage = AdminMessageFactory.createAdminMessageFromRawMessage(message);
            //Log.d(LOG_TAG, "Got admin message: " + adminMessage);
            if (adminMessage instanceof AddSubscriptionAdminMessage) {
                short channelId = ((AddSubscriptionAdminMessage) adminMessage).getChannelId();
                String uniqueId = ((AddSubscriptionAdminMessage) adminMessage).getUniqueDeviceId();
                BlaubotChannel channel = (BlaubotChannel) createOrGetChannel(channelId);
                channel.addSubscription(uniqueId);
            } else if(adminMessage instanceof RemoveSubscriptionAdminMessage) {
                short channelId = ((RemoveSubscriptionAdminMessage) adminMessage).getChannelId();
                String uniqueId = ((RemoveSubscriptionAdminMessage) adminMessage).getUniqueDeviceId();
                BlaubotChannel channel = (BlaubotChannel) createOrGetChannel(channelId);
                channel.removeSubscription(uniqueId);
            }
            // finally notify all other listeners
            for(IBlaubotAdminMessageListener listener : adminMessageListeners) {
                listener.onAdminMessage(adminMessage);
            }
        }
    };


    /**
     * The uniqueDevice Id
     * @return the unique device id
     */
    protected String getOwnUniqueDeviceId() {
        return ownUniqueDeviceId;
    }

    /**
     * Note: This is a low level messaging method used for internal messaging and admin messages.
     *
     * Sends (queues) the given BlaubotMessage to all connections added via addConnection(..)
     * Obviously this means that if in master mode this method dispatches the message to all connected
     * clients and in client mode to the master.
     *
     * @param message the message that will be published
     * @return the number of message senders to which the message was committed
     */
    public int publishToAllConnections(BlaubotMessage message) {
        int cnt = 0;
        for (BlaubotMessageManager messageManager : messageManagers.values()) {
            messageManager.getMessageSender().sendMessage(message);
            cnt += 1;
        }
        return cnt;
    }

    /**
     * Note: This is a low level messaging method used for internal messaging and admin messages.
     * Use channels for your messages.
     *
     * Sends (queues) the given BlaubotMessage to be sent to the connection(s) of the device
     * with the given uniqueDeviceId (if any managed connection(s) are from  the device with
     * uniqueDeviceId).
     *
     * @param message the message to be sent
     * @param uniqueDeviceId the message recipient
     * @return true iff at least one message sender for this unqiueDeviceId was found
     */
    public boolean publishToSingleDevice(BlaubotMessage message, String uniqueDeviceId) {
        final Set<BlaubotMessageManager> managers = new HashSet<BlaubotMessageManager>();
        // TODO this implementation is probably horribly slow and produces many overhead
        for (BlaubotMessageManager messageManager : messageManagers.values()) {
            final BlaubotMessageSender messageSender = messageManager.getMessageSender();
            if(messageSender.getBlaubotConnection().getRemoteDevice().getUniqueDeviceID().equals(uniqueDeviceId)) {
                managers.add(messageManager);
            }
        }
        for(BlaubotMessageManager messageManager : managers) {
            messageManager.getMessageSender().sendMessage(message);
        }
        if(Log.logWarningMessages()) {
            if(managers.isEmpty()) {
                Log.w(LOG_TAG, "Could not send a message to " + uniqueDeviceId + " because there was no managed connection for this device.");
            }
        }
        return !managers.isEmpty();
    }


    /**
     * Publishes a message posted to a channel to the master, where it is then dispatched further.
     * This method is called by the message pickers from the channels and only represents the first
     * hop a message makes.
     *
     * @param channelMessage the message to be published
     * @return the number of message senders that got our message (sendMessage() calls).
     */
    public int publishChannelMessage(BlaubotMessage channelMessage) {
        /**
         * TODO since this is the first hop and we always know, who is subscribed we can avoid unneeded traffic here by not sending the message at all in cases where we have no subscriptions to this channel
         */
        if(isMaster) {
            // we send it to our own connection with the firstHop bit set
            channelMessage.getMessageType().setIsFirstHop(true);
            ownMessageManager.getMessageSender().sendMessage(channelMessage);
            return 1;
        } else {
            // we send it to the master
            return publishToAllConnections(channelMessage);
        }

    }

    /**
     * Sends a BlaubotMessage as admin message to the master, from where it is dispatched to all clients (including the master).
     *
     * @param adminMessage the adminMessage to be broadcasted
     * @return the number of MessageSenders to which the message was committed
     */
    public int broadcastAdminMessage(BlaubotMessage adminMessage) {
        final BlaubotMessageType messageType = adminMessage.getMessageType();
        messageType.setIsFirstHop(true);
        if(isMaster) {
            // we send it to our own connection with the firstHop bit set
            // this causes the master's channelManager to dispatch the msg
            // to all clients
            ownMessageManager.getMessageSender().sendMessage(adminMessage);
            return 1;
        } else {
            // we send it to the master via the connection
            return publishToAllConnections(adminMessage);
        }
    }

    /**
     * @return true, iff the channel manager has at least one connection
     */
    protected boolean hasConnections() {
        return !messageManagers.isEmpty();
    }

    /**
     * Adds a listener for admin messages.
     * @param adminMessageListener the listener to add
     */
    public void addAdminMessageListener(IBlaubotAdminMessageListener adminMessageListener) {
        this.adminMessageListeners.add(adminMessageListener);
    }

    /**
     * Removes a listener for admin messages.
     * @param adminMessageListener the listener to be removed
     */
    public void removeAdminMessageListener(IBlaubotAdminMessageListener adminMessageListener) {
        this.adminMessageListeners.remove(adminMessageListener);
    }

    /**
     * Removes all connections (and deactivates their senders/receivers).
     * Note: subscriptions will not be touched
     */
    public void reset() {
        // remove all connections and their messagemanagers
        final Collection<BlaubotMessageManager> blaubotMessageManagers = messageManagers.values();
        for(BlaubotMessageManager manager : blaubotMessageManagers) {
//            manager.deactivate();
            removeConnection(manager.getMessageSender().getBlaubotConnection());
        }
    }


    /**
     * Activates all MessageManagers and Channels
     */
    public void activate() {
        for(BlaubotMessageManager messageManager : messageManagers.values()) {
            messageManager.activate();
        }

        // start channels, if we have connections
        maintainChannelActivation();
    }

    /**
     * activates all channels
     */
    private void activateChannels() {
        for (BlaubotChannel channel : channels.values()) {
            channel.activate();
        }
    }

    /**
     * Deactivates all MessageManagers and Channels
     */
    public void deactivate() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Deactivating ChannelManager ...");
        }

        // deactivate channel managers
        ArrayList<CountDownLatch> latches = new ArrayList<>();
        for (BlaubotMessageManager messageManager : messageManagers.values()) {
            final CountDownLatch latch = new CountDownLatch(1);
            latches.add(latch);
            messageManager.deactivate(new IActionListener() {
                @Override
                public void onFinished() {
                    latch.countDown();
                }
            });
        }

        for (CountDownLatch latch : latches) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "ChannelManager deactivated.");
        }
    }

    /**
     * deactivates all channels
     */
    private void deactivateChannels() {
        // deactivate channels
        for (BlaubotChannel channel : channels.values()) {
            channel.deactivate(); // blocking
        }
    }

    /**
     * Creates an object containing detailed informations about the state of the channel manager.
     * Useful for debugging and unit testing.
     *
     * @return the info object
     */
    public BlaubotChannelManagerInfo createChannelManagerInfo() {
        return new BlaubotChannelManagerInfo(channels.values(), messageManagers.values(), ownUniqueDeviceId);
    }


    /**
     * Adds a subscription listener to the manager
     * @param subscriptionChangeListener the listener to add
     */
    public void addSubscriptionListener(IBlaubotSubscriptionChangeListener subscriptionChangeListener) {
        subscriptionChangeListeners.add(subscriptionChangeListener);
    }

    /**
     * Removes a subscription listener from the manager
     * @param subscriptionChangeListener the listener to remove
     */
    public void removeSubscriptionListener(IBlaubotSubscriptionChangeListener subscriptionChangeListener) {
        subscriptionChangeListeners.remove(subscriptionChangeListener);
    }
}
