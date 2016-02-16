package eu.hgross.blaubot.messaging;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.hgross.blaubot.admin.AddSubscriptionAdminMessage;
import eu.hgross.blaubot.admin.RemoveSubscriptionAdminMessage;
import eu.hgross.blaubot.util.Log;


/**
 * A channel managed by the BlaubotChannelManager.
 * <p/>
 * Subscriptions to a channel can be made via subscribe() and removed by unsubscribe().
 * To listen to messages on this channel, attach a listener via {BlaubotChannel#addMessageListener}.
 * Listeners can be removed via {BlaubotChannel#removeMessageListener}.
 * <p/>
 * Subscriptions are sent immediately to the network, meaning, that if there is no network,
 * no subscription is made.
 * The recommended way to subscribe to channels is to do this by a {ILifecycleListener#onConnected}.
 * <p/>
 * Messages send via {BlaubotChannel#publish} are added to a bounded queue, which is processed due to
 * a defined message picking strategy (@see {IBlaubotMessagePickerStrategy}).
 * The processing is activated/deactivated by the activate/deactivate methods.
 * If activated, a processing thread uses the specified picker strategy to get messages from the
 * queue and hands this messages to the BlaubotChannelManager.
 * <p/>
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

    /**
     * A boolean that is maintained through creation and removal of subscription and indicates, if
     * our own device is subscribed to this very channel.
     * Reason: we don't have to look up if that is the case for each message we send, if the transmit
     * reflexive messages option is set to false
     */
    private volatile boolean ownDeviceIsSubscribed = false;

    /**
     * ExecutorService used to notify listeners about messages if the transmitReflexiveMessages option
     * is set to false to not use the same thread for notifications and to send messages.
     */
    private ExecutorService notificationExecutorService;

    private long sentMessages = 0;
    private long sentBytes = 0;
    private long receivedMessages = 0;
    private long receivedBytes = 0;


    /**
     * If set, no transmission is made whatsoever. Meaning regardless if activated or deactivated,
     * the picking will be skipped as long as this boolean is false.
     */
    private AtomicBoolean doNotTransmit = new AtomicBoolean(false);

    /**
     * The queueProcessor is a Runnable, that uses the channel's config to retrieve
     * the message picker strategy to empty the channel's message queue.
     * It picks messages and hands them to th channel manager.
     */
    private final Runnable queueProcessor = new Runnable() {
        @Override
        public void run() {
            try {
                // suicide if no connections
                if (!channelManager.hasConnections()) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "The ChannelManager has no connections but the channel is activated. Not picking and will deactivate the channel. ");
                    }
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            deactivate();
                        }
                    }).start();
                    return;
                }

                // check if we are allowed to pick
                if (doNotTransmit.get()) {
                    // we are not allowed to send, sleep a while if we have a low message rate and come back later
                    if (channelConfig.getMinMessageRateDelay() < 50) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    return;
                }

                /**
                 * True, iff we are the only subscriber
                 */
                boolean weAreOnlySubscriber = false;

                // check if there are subscribers and do nothing, if not told otherwise
                if (!channelConfig.isTransmitIfNoSubscribers()) {
                    final int subscribers = subscriptions.size();
                    if (subscribers == 0) {
                        // we don't send anything, no subscribers at all
                        return;
                    } else if (subscribers == 1 && ownDeviceIsSubscribed) {
                        weAreOnlySubscriber = true;
                    }
                }

                final IBlaubotMessagePickerStrategy picker = channelConfig.getMessagePicker();
                final BlaubotMessage blaubotMessage = picker.pickNextMessage(messageQueue);
                if (blaubotMessage != null) {
                    final boolean transmitReflexiveMessages = channelConfig.isTransmitReflexiveMessages();
                    final boolean publishToConnections = !(weAreOnlySubscriber && !transmitReflexiveMessages);
                    boolean wasNotSendToAnyConnection = true;
                    // only publish to master, if needed (respect transmitReflexiveMssages option) 
                    if (publishToConnections) {
                        final int connectionCount = channelManager.publishChannelMessage(blaubotMessage);
                        wasNotSendToAnyConnection = connectionCount <= 0;
                        if (wasNotSendToAnyConnection) {
                            if (Log.logWarningMessages()) {
                                Log.w(LOG_TAG, "A picked message was not committed to any MessageSender.");
                            }
                        }
                    }

                    // messages to our own device shall not be received through the master device but 
                    // have to be dispatched by the channel directly to save network traffic (1 hop, back from the mater to us)
                    final boolean notifyLocalListeners = !transmitReflexiveMessages && ownDeviceIsSubscribed;
                    if (notifyLocalListeners) {
                        // -- notify in new thread (to not mix up send and notification threads)
                        // we will not receive it again from the master device because the excludeSender flag will be set on the message,
                        // if isTransmitReflexiveMessages is false.
                        notificationExecutorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                BlaubotChannel.this.notify(blaubotMessage);
                            }
                        });
                    }

                    if (!wasNotSendToAnyConnection || notifyLocalListeners) {
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
     * A runnable that just loops over the queue looper over and over again by adding it to the executor
     * after one run again
     */
    private final Runnable queueLooperTask = new Runnable() {
        @Override
        public void run() {
            queueProcessor.run();

            final ExecutorService service = BlaubotChannel.this.queueProcessorExecutorService;
            if (service != null) {
                try {
                    service.execute(queueLooperTask);
                } catch (RejectedExecutionException e) {
                    // executor is shutting dow
                }
            } // else: we are done (probably deactivated)
        }
    };

    /**
     * The ExecutorService that is used to run the queueProcessor.
     * It is created/shut down by the activate/deactivate methods.
     */
    private volatile ExecutorService queueProcessorExecutorService;
    /**
     * The max time for the queueProcessorExecutorService to shut down on deactivate()
     */
    private static final long TERMINATION_TIMEOUT = 5000;
    /**
     * Locks access to the queueProcessorExecutorService variable.
     */
    private final Object activateDeactivateMonitor = new Object();


    /**
     * @param channelId      the channel id
     * @param channelManager the channelManager instance, that created this channel
     */
    protected BlaubotChannel(short channelId, BlaubotChannelManager channelManager) {
        this.subscriptions = new ConcurrentSkipListSet<>();
        this.subscriptionChangeListeners = new CopyOnWriteArrayList<>();
        this.messageListeners = new CopyOnWriteArrayList<>();
        this.channelManager = channelManager;
        this.channelConfig = new BlaubotChannelConfig(channelId);
        this.channelConfig.addObserver(channelConfigObserver);
        this.notificationExecutorService = Executors.newCachedThreadPool();
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
        return publish(blaubotMessage, false);
    }

    @Override
    public boolean publish(BlaubotMessage blaubotMessage, boolean excludeSender) {
        setUpChannelMessage(blaubotMessage, excludeSender);
        final boolean addedToQueue = messageQueue.offer(blaubotMessage);
        return addedToQueue;
    }

    @Override
    public boolean publish(BlaubotMessage blaubotMessage, long timeout) {
        return publish(blaubotMessage, timeout, false);
    }

    @Override
    public boolean publish(BlaubotMessage blaubotMessage, long timeout, boolean excludeSender) {
        setUpChannelMessage(blaubotMessage, excludeSender);
        try {
            return messageQueue.offer(blaubotMessage, timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Got interrupted trying to offer a message to the queue. Message was not added: " + blaubotMessage);
            }
            return false;
        }
    }

    @Override
    public boolean publish(byte[] payload) {
        return publish(payload, false);
    }

    @Override
    public boolean publish(byte[] payload, boolean excludeSender) {
        BlaubotMessage msg = new BlaubotMessage();
        msg.setPayload(payload);
        return publish(msg, excludeSender);
    }

    @Override
    public boolean publish(byte[] payload, long timeout) {
        return publish(payload, timeout, false);
    }

    @Override
    public boolean publish(byte[] payload, long timeout, boolean excludeSender) {
        BlaubotMessage msg = new BlaubotMessage();
        msg.setPayload(payload);
        return publish(msg, timeout, excludeSender);
    }

    /**
     * Takes a blaubot message and modifies the header according to this channel
     *
     * @param blaubotMessage the message to be published through this channel
     * @param excludeSender  iff true, the message is not dispatched to the sender's connection
     */
    private void setUpChannelMessage(BlaubotMessage blaubotMessage, boolean excludeSender) {
        blaubotMessage.setChannelId(this.channelConfig.getChannelId());
        blaubotMessage.getMessageType().setIsFirstHop(true);
        blaubotMessage.setPriority(channelConfig.getPriority());
        blaubotMessage.getMessageType().setExcludeSender(excludeSender || !channelConfig.isTransmitReflexiveMessages());
    }


    @Override
    public void subscribe() {
        final String ownUniqueDeviceId = channelManager.getOwnUniqueDeviceId();
        final int involvedSenders = sendAddSubscription(ownUniqueDeviceId);

        // always add it locally
        addSubscription(ownUniqueDeviceId);
    }

    @Override
    public void subscribe(IBlaubotMessageListener blaubotMessageListener) {
        messageListeners.add(blaubotMessageListener);
        subscribe();
    }

    @Override
    public void unsubscribe() {
        final String ownUniqueDeviceId = channelManager.getOwnUniqueDeviceId();
        final int involvedSenders = sendRemoveSubscription(ownUniqueDeviceId);

        if (involvedSenders <= 0) {
            // we are not connected, just remove
            removeSubscription(ownUniqueDeviceId);
        }
    }

    @Override
    public void addMessageListener(IBlaubotMessageListener messageListener) {
        messageListeners.add(messageListener);
    }

    @Override
    public void removeMessageListener(IBlaubotMessageListener messageListener) {
        final String ownUniqueDeviceId = channelManager.getOwnUniqueDeviceId();
        messageListeners.remove(messageListener);
        if (messageListeners.isEmpty()) {
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
     *
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
            if (uniqueDeviceID.equals(channelManager.getOwnUniqueDeviceId())) {
                ownDeviceIsSubscribed = true;
            }
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
            if (uniqueDeviceId.equals(channelManager.getOwnUniqueDeviceId())) {
                ownDeviceIsSubscribed = false;
            }
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
     *
     * @param subscriptionChangeListener the listener to add
     */
    public void addSubscriptionListener(IBlaubotSubscriptionChangeListener subscriptionChangeListener) {
        subscriptionChangeListeners.add(subscriptionChangeListener);
    }

    /**
     * Removes a subscription listener from the manager
     *
     * @param subscriptionChangeListener the listener to remove
     */
    public void removeSubscriptionListener(IBlaubotSubscriptionChangeListener subscriptionChangeListener) {
        subscriptionChangeListeners.remove(subscriptionChangeListener);
    }

    /**
     * Notifies the attached listeners that a subscription was added.
     *
     * @param uniqueDeviceId the subscribing uniquedeviceid
     * @param channelId      the channel id
     */
    private void notifySubscriptionAdded(String uniqueDeviceId, short channelId) {
        for (IBlaubotSubscriptionChangeListener listener : subscriptionChangeListeners) {
            listener.onSubscriptionAdded(uniqueDeviceId, channelId);
        }
    }

    /**
     * Notifies the attached listeners that a subscription was removed.
     *
     * @param uniqueDeviceId the formerly subscribing uniquedeviceid
     * @param channelId      the channel id
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
            if (queueProcessorExecutorService != null) {
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

            // TODO: there is a 1 ms delay for the pick all strategy but we want minMessageRateDelay = 0 to be possible. We just have to use a while loop instead of the fixed delay scheduler here 
            final int minMessageRateDelay = channelConfig.getMinMessageRateDelay();
            if (minMessageRateDelay <= 0) {
                queueProcessorExecutorService = Executors.newSingleThreadExecutor();
                queueProcessorExecutorService.submit(queueLooperTask);
            } else {
                // -- minMessageRateDelay > 0
                queueProcessorExecutorService = Executors.newSingleThreadScheduledExecutor();
                ((ScheduledExecutorService) queueProcessorExecutorService).scheduleWithFixedDelay(queueProcessor, 0, minMessageRateDelay, TimeUnit.MILLISECONDS);
            }
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "BlaubotChannel #" + channelConfig.getChannelId() + " activated.");
        }
    }

    /**
     * Deactivates the channel and therefore the message picking.
     * Blocks until the channel has shut down!
     *
     * @return true, iff the channel was activated before
     */
    protected boolean deactivate() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Deactivating BlaubotChannel #" + channelConfig.getChannelId() + " ...");
        }
        boolean wasActivated = false;
        synchronized (activateDeactivateMonitor) {
            if (queueProcessorExecutorService != null) {
                queueProcessorExecutorService.shutdownNow();
                try {
                    final boolean timedOut = !queueProcessorExecutorService.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (timedOut) {
                        throw new RuntimeException("Could not stop channel");
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
                wasActivated = true;
            }
            queueProcessorExecutorService = null;
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
        return queueProcessorExecutorService != null;
    }

    /**
     * The queue capacity
     *
     * @return capacity of the queue
     */
    protected int getQueueCapacity() {
        return channelConfig.getQueueCapacity();
    }

    /**
     * The current amount of messages in the queue
     *
     * @return current amount of messages in the queue
     */
    protected int getQueueSize() {
        return messageQueue.size();
    }

    /**
     * The amount of bytes sent through this channel so far.
     *
     * @return number of bytes
     */
    public long getSentBytes() {
        return sentBytes;
    }

    /**
     * The message count sent through this channel so far.
     *
     * @return number of messages
     */
    public long getSentMessages() {
        return sentMessages;
    }


    /**
     * The message count received by this channel so far.
     *
     * @return number of messages
     */
    public long getReceivedMessages() {
        return receivedMessages;
    }

    /**
     * The amount of bytes received by this channel so far.
     *
     * @return number of bytes
     */
    public long getReceivedBytes() {
        return receivedBytes;
    }

    /**
     * Allows or disallows transmission of messages by this channel.
     * Is used to block picking as long as an initial subscription handshake is pending.
     *
     * @param doNotTransmit if true, no messages will be picked as long as this state is set
     */
    protected void setDoNotTransmit(boolean doNotTransmit) {
        this.doNotTransmit.set(doNotTransmit);
    }
}
