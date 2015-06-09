package eu.hgross.blaubot.messaging;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.admin.AddSubscriptionAdminMessage;
import eu.hgross.blaubot.admin.RemoveSubscriptionAdminMessage;
import eu.hgross.blaubot.util.Log;


/**
 * A channel managed by the BlaubotChannelManager.
 *
 * Subscriptions to a channel can be made via subscribe() and removed by unsubscribe().
 * To listen to messages on this channel, attach a listener via {BlaubotChannel#addMessageListener}.
 * Listeners can be removed via {BlaubotChannel#removeMessageListener}.
 *
 * Subscriptions are sent immediately to the network, meaning, that if there is no network,
 * no subscription is made.
 * The recommended way to subscribe to channels is to do this by a {ILifecycleListener#onConnected}.
 *
 * Messages send via {BlaubotChannel#publish} are added to a bounded queue, which is processed due to
 * a defined message picking strategy (@see {IBlaubotMessagePickerStrategy}).
 * The processing is activated/deactivated by the activate/deactivate methods.
 * If activated, a processing thread uses the specified picker strategy to get messages from the
 * queue and hands this messages to the BlaubotChannelManager.
 *
 * To influence the MessagePicking and message rates, @see {BlaubotChannel#getChannelConfig}.
 * The picking and rates can be changed at runtime.
 */
public class BlaubotChannel implements IBlaubotChannel {
    private static final String LOG_TAG = "BlaubotChannel";
    /**
     * The channel config used for this channel.
     * Defines the picking strategy and channel id.
     */
    private final BlaubotChannelConfig channelConfig;
    /**
     * The channel manager that created this instance (BlaubotChannelManager#createOrGetChannel}
     */
    private final BlaubotChannelManager channelManager;
    /**
     * Set of UniqueDeviceIds that subscribed to this channel
     */
    private final ConcurrentSkipListSet<String> subscriptions;
    /**
     * Attached listeners to this channel.
     */
    private final CopyOnWriteArrayList<IBlaubotMessageListener> messageListeners;

    /**
     * Listeners which get called, if subscriptions are modified.
     */
    private CopyOnWriteArrayList<IBlaubotSubscriptionChangeListener> subscriptionChangeListeners;

    /**
     * The bounded queue where all messages go to on {BlaubotChannel#publish} calls.
     * See the queueProcessor doc.
     */
    private BlockingQueue<BlaubotMessage> messageQueue;

    private long sentMessages = 0;
    private long sentBytes = 0;
    private long receivedMessages = 0;
    private long receivedBytes = 0;

    /**
     * The queueProcessor is a Runnable, that uses the channel's config to retrieve
     * the message picker strategy to empty the channel's message queue.
     * It picks messages and hands them to th channel manager.
     */
    private Runnable queueProcessor = new Runnable() {
        @Override
        public void run() {
            try {
                if (!channelManager.hasConnections()) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "The ChannelManager has no connections but the channel is activated. Not picking and will deactivate the channel.");
                    }
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            deactivate();
                        }
                    }).start();
                    return;
                }
                final IBlaubotMessagePickerStrategy picker = channelConfig.getMessagePicker();
                final BlaubotMessage blaubotMessage = picker.pickNextMessage(messageQueue);
                if (blaubotMessage != null) {
                    final int connectionCount = channelManager.publishChannelMessage(blaubotMessage);
                    final boolean wasNotSendToAnyConnection = connectionCount <= 0;
                    if (wasNotSendToAnyConnection) {
                        if (Log.logWarningMessages()) {
                            Log.w(LOG_TAG, "A picked message was not committed to any MessageSender.");
                        }
                    } else {
                        sentBytes += blaubotMessage.getPayload().length;
                        sentMessages += 1;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    };

    /**
     * The ExecutorService that is used to run the queueProcessor.
     * It is created/shut down by the activate/deactivate methods.
     */
    private ScheduledExecutorService executorService;
    /**
     * The max time for the executorService to shut down on deactivate()
     */
    private static final long TERMINATION_TIMEOUT = 5000;
    /**
     * Locks access to the executorService variable.
     */
    private final Object activateDeactivateMonitor = new Object();


    /**
     * @param channelId the channel id
     * @param channelManager the channelManager instance, that created this channel
     */
    protected BlaubotChannel(short channelId, BlaubotChannelManager channelManager) {
        this.subscriptions = new ConcurrentSkipListSet<>();
        this.subscriptionChangeListeners = new CopyOnWriteArrayList<>();
        this.messageListeners = new CopyOnWriteArrayList<>();
        this.channelManager = channelManager;
        this.channelConfig = new BlaubotChannelConfig(channelId);
        this.channelConfig.addObserver(channelConfigObserver);
        this.setUpMessageQueue();
    }

    /**
     * Creates the message queue.
     * If the message queue is not null, a new one is created and the messages of the old queue
     * are appended to the new one.
     */
    private void setUpMessageQueue() {
        final ArrayBlockingQueue<BlaubotMessage> newMessageQueue = new ArrayBlockingQueue<>(channelConfig.getQueueCapacity());
        int sizeBefore = 0;
        boolean allTransferred = true;
        if (this.messageQueue != null) {
            sizeBefore = messageQueue.size();
            try {
                // drain the old queue to the new one
                this.messageQueue.drainTo(newMessageQueue);
            } catch (IllegalStateException e) {
                // -- the new queue size is to small for the messages
                allTransferred = false;
            }
        }
        this.messageQueue = newMessageQueue;
        final int sizeAfter = messageQueue.size();
        if (sizeAfter != sizeBefore) {
            allTransferred = false;
        }
        if (!allTransferred) {
            // log error and move on
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not add all of the previous messages to the queue (new queue size was smaller than the amount of messages in the queue). Dropped all messages exceeding the capacity.");
            }
        }
    }

    /**
     * Listens to changes of the channel config at runtime and restarts
     * the channel if needed.
     */
    private Observer channelConfigObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            if (o == channelConfig) {
                if (arg instanceof Boolean && ((Boolean) arg).booleanValue()) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "BlaubotChannelConfig changed and restart of channel needed. Restarting BlaubotChannel ...");
                    }
                    // restart channel, if the config notified that this is necessary, which is
                    // told us by the second arg - yeah we could introduce a new listener, but why.
                    restart();
                }
            }
        }
    };

    /**
     * Deactivates, then activates the channel to reflect changes made to the channel config.
     * (Executor restart)
     */
    private synchronized void restart() {
        boolean wasActiveBefore = deactivate();
        if (wasActiveBefore) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "BlaubotChannel #" + channelConfig.getChannelId() + " was activated before the restart, re-activating ...");
            }
            activate();
        } else {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "BlaubotChannel #" + channelConfig.getChannelId() + " was not activated before the restart. Not activating the channel.");
            }
        }
    }

    @Override
    public boolean publish(BlaubotMessage blaubotMessage) {
        setUpChannelMessage(blaubotMessage);
        final boolean addedToQueue = messageQueue.offer(blaubotMessage);
        return addedToQueue;
    }

    @Override
    public boolean publish(BlaubotMessage blaubotMessage, long timeout) {
        setUpChannelMessage(blaubotMessage);
        try {
            return messageQueue.offer(blaubotMessage, timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Got interrupted trying to offer a message to the queue. Message was not added: " + blaubotMessage);
            }
            return false;
        }
    }

    /**
     * Takes a blaubot message and modifies the header according to this channel
     * @param blaubotMessage the message to be published through this channel
     */
    private void setUpChannelMessage(BlaubotMessage blaubotMessage) {
        blaubotMessage.setChannelId(this.channelConfig.getChannelId());
        blaubotMessage.getMessageType().setIsFirstHop(true);
        blaubotMessage.setPriority(channelConfig.getPriority());
    }

    @Override
    public boolean publish(byte[] payload) {
        BlaubotMessage msg = new BlaubotMessage();
        msg.setPayload(payload);
        return publish(msg);
    }

    @Override
    public boolean publish(byte[] payload, long timeout) {
        BlaubotMessage msg = new BlaubotMessage();
        msg.setPayload(payload);
        return publish(msg, timeout);
    }

    @Override
    public void subscribe() {
        final String ownUniqueDeviceId = channelManager.getOwnUniqueDeviceId();
        final int involvedSenders = sendAddSubscription(ownUniqueDeviceId);

        /**
         * if the channel is created before the channelmanager is started, the subscription is not send,
         * which we know because there will be no involved message sender
         */
        if (involvedSenders <= 0) {
            addSubscription(ownUniqueDeviceId);
        }
    }

    @Override
    public void subscribe(IBlaubotMessageListener blaubotMessageListener) {
        messageListeners.add(blaubotMessageListener);
        subscribe();
    }

    @Override
    public void unsubscribe() {
        final String ownUniqueDeviceId = channelManager.getOwnUniqueDeviceId();
        sendRemoveSubscription(ownUniqueDeviceId);
    }

    @Override
    public void addMessageListener(IBlaubotMessageListener messageListener) {
        messageListeners.add(messageListener);
    }

    @Override
    public void removeMessageListener(IBlaubotMessageListener messageListener) {
        final String ownUniqueDeviceId = channelManager.getOwnUniqueDeviceId();
        messageListeners.remove(messageListener);
        if(messageListeners.isEmpty()) {
            unsubscribe();
        }
    }

    /**
     * Sends the AddSubscriptionMessage to the master.
     * The Subscription itself is added, when the master sends the message back and addSubscription is called
     * by the ChannelManager
     *
     * @param uniqueDeviceID the unique
     * @return number of MessageManagers to which the message was committed
     */
    protected int sendAddSubscription(String uniqueDeviceID) {
        AddSubscriptionAdminMessage msg = new AddSubscriptionAdminMessage(uniqueDeviceID, channelConfig.getChannelId());
        return channelManager.broadcastAdminMessage(msg.toBlaubotMessage());
    }

    /**
     * Sends the RemoveSubscriptionMessage to the master.
     * The subscription itsef is removed, when the master sends the message back and removeSubscription gets
     * called by the ChannelManager.
     * @param uniqueDeviceId
     * @return number of MessageManagers to which the message was committed
     */
    protected int sendRemoveSubscription(String uniqueDeviceId) {
        RemoveSubscriptionAdminMessage msg = new RemoveSubscriptionAdminMessage(uniqueDeviceId, channelConfig.getChannelId());
        return channelManager.broadcastAdminMessage(msg.toBlaubotMessage());
    }

    /**
     * Adds a subscription to uniqueDeviceId to this channel.
     * The operation is idempotent.
     *
     * @param uniqueDeviceID
     */
    protected void addSubscription(String uniqueDeviceID) {
        synchronized (channelManager.subscriptionLock) {
            subscriptions.add(uniqueDeviceID);
        }
        notifySubscriptionAdded(uniqueDeviceID, channelConfig.getChannelId());
    }

    /**
     * Removes the subscription of uniqueDeviceId to this channel
     * The operation is idempotent.
     *
     * @param uniqueDeviceId
     */
    protected void removeSubscription(String uniqueDeviceId) {
        synchronized (channelManager.subscriptionLock) {
            subscriptions.remove(uniqueDeviceId);
        }
        notifySubscriptionRemoved(uniqueDeviceId, channelConfig.getChannelId());
    }

    protected ConcurrentSkipListSet<String> getSubscriptions() {
        return subscriptions;
    }

    /**
     * Notifies this channel about a new message.
     * Gets called from the outside (BlaubotChannelManager.messageDispatcher).
     *
     * @param message the message posted to this channel
     */
    protected void notify(BlaubotMessage message) {
        receivedBytes += message.getPayload().length;
        receivedMessages += 1;
        for (IBlaubotMessageListener listener : messageListeners) {
            listener.onMessage(message);
        }
    }

    /**
     * The channel config specifying the message picking strategy and message rates as well
     * as the id.
     * The config's values can be changed at runtime.
     * Changes are only reflected, if the channel is activated, meaning the net ChannelManager is activated
     * or respectively we are connected to a blaubot network.
     *
     * @return the channel config
     */
    public BlaubotChannelConfig getChannelConfig() {
        return channelConfig;
    }

    @Override
    public void clearMessageQueue() {
        messageQueue.clear();
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

    /**
     * Activates the channel and therefore the message picking
     */
    protected void activate() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Activating BlaubotChannel #" + channelConfig.getChannelId() + " ...");
        }
        synchronized (activateDeactivateMonitor) {
            if (executorService != null) {
                if (Log.logWarningMessages()) {
                    // TODO actually not a warning and might happen -> debug when evaluated
                    Log.w(LOG_TAG, "activate() called but channel was already activated. Doing nothing!");
                }
                return;
            }
            // check if we have to adjust the queue size
            if (messageQueue.size() != channelConfig.getQueueCapacity()) {
                setUpMessageQueue();
            }

            executorService = Executors.newSingleThreadScheduledExecutor();
            final int minMessageRateDelay = channelConfig.getMinMessageRateDelay();
            executorService.scheduleWithFixedDelay(queueProcessor, 0, minMessageRateDelay, TimeUnit.MILLISECONDS);
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "BlaubotChannel #" + channelConfig.getChannelId() + " activated.");
        }
    }

    /**
     * Deactivates the channel and therefore the message picking.
     * Blocks until the channel has shut down!
     * @return true, iff the channel was activated before
     */
    protected boolean deactivate() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Deactivating BlaubotChannel #" + channelConfig.getChannelId() + " ...");
        }
        boolean wasActivated = false;
        synchronized (activateDeactivateMonitor) {
            if (executorService != null) {
                executorService.shutdown();
                try {
                    executorService.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // ignore
                }
                wasActivated = true;
            }
            executorService = null;
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "BlaubotChannel #" + channelConfig.getChannelId() + " deactivated.");
        }
        return wasActivated;
    }

    /**
     * @return true, iff active (= executor started)
     */
    protected boolean isActive() {
        return executorService != null;
    }

    /**
     * The queue capacity
     * @return capacity of the queue
     */
    protected int getQueueCapacity() {
        return channelConfig.getQueueCapacity();
    }

    /**
     * The current amount of messages in the queue
     * @return current amount of messages in the queue
     */
    protected int getQueueSize() {
        return messageQueue.size();
    }

    /**
     * The amount of bytes sent through this channel so far.
     * @return number of bytes
     */
    public long getSentBytes() {
        return sentBytes;
    }

    /**
     * The message count sent through this channel so far.
     * @return number of messages
     */
    public long getSentMessages() {
        return sentMessages;
    }


    /**
     * The message count received by this channel so far.
     * @return number of messages
     */
    public long getReceivedMessages() {
        return receivedMessages;
    }

    /**
     * The amount of bytes received by this channel so far.
     * @return number of bytes
     */
    public long getReceivedBytes() {
        return receivedBytes;
    }
}
