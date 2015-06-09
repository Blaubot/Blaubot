package eu.hgross.blaubot.messaging;

import java.util.Observable;

import eu.hgross.blaubot.util.Log;

/**
 * Configuration for a BlaubotChannel.
 * Can limit message rates and set picking strategies as well as priorities.
 * The channel configuration is local - changes made are only reflected to the local device and
 * not communicated to other devices.
 */
public class BlaubotChannelConfig extends Observable {
    private static final String LOG_TAG = "BlaubotChannelConfig";
    /**
     * Use this constant for {BlaubotChannelConfig#setMessageRateLimit} to not use a message rate limit.
     */
    public static final int MESSAGE_RATE_NO_LIMIT = -1;
    /**
     * Default maximum size of the message queue
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 200;
    /**
     * The channel id for this channel.
     */
    private short channelId;
    /**
     * The message picker to be used
     */
    private IBlaubotMessagePickerStrategy messagePicker;

    /**
     * The message rate limit to be used (minimum delay between to messages in ms)
     * Defaults to no limit.
     */
    private int minMessageRateDelay;

    /**
     * The priority with which messages of this channel are send.
     */
    private BlaubotMessage.Priority priority;

    /**
     * The max size of the message queue.
     */
    private int queueCapacity;

    /**
     * Constructs a channel config for a channel id using the default
     * MessagePickerStrategy (PROCESS_ALL).
     * To change the MessagePickerStrategy, @see {BlaubotChannelConfig#setMessagePickerStrategy}.
     *
     * @param channelId the channel's id
     */
    public BlaubotChannelConfig(short channelId) {
        this.channelId = channelId;
        _setMessageRateLimit(MESSAGE_RATE_NO_LIMIT);
        _setMessagePickerStrategy(MessagePickerStrategy.PROCESS_ALL);
        _setPriority(BlaubotMessage.Priority.NORMAL);
        _setQueueCapacity(DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Sets the priority without notifying observers
     * @param priority the priority for this channel
     */
    private void _setPriority(BlaubotMessage.Priority priority) {
        if (Log.logWarningMessages()) {
            if (priority.equals(BlaubotMessage.Priority.ADMIN)) {
                if (Log.logWarningMessages()) {
                    Log.w(LOG_TAG, "The priority of channel #" + channelId + " was set to ADMIN. I really hope you know what you are doing.");
                }
            }
        }
        this.priority = priority;
    }

    /**
     * Sets the size of the message queue without notifying observers
     * @param queueCapacity the queue size
     */
    private void _setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * Sets max the size of the message queue.
     *
     * Attention:
     * If there are currently more than queueCapacity messages in the message queue, all messages
     * that exceed the queue capacity will be dropped. This is likely to happen, if the queue size
     * is decreased at runtime.
     *
     * @param queueCapacity the new capacity.
     */
    public void setQueueCapacity(int queueCapacity) {
        _setQueueCapacity(queueCapacity);
        setChanged();
        notifyObservers(Boolean.TRUE);
    }

    /**
     * The max size of the message queue
     *
     * @return max number of messages in the queue
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Sets the message rate attribute without activating/deactivating threads and stuff.
     * @param minMessageRateDelay the message rate limit
     */
    private void _setMessageRateLimit(int minMessageRateDelay) {
        this.minMessageRateDelay = minMessageRateDelay;
    }

    /**
     * The channel id
     * @return channel id
     */
    public short getChannelId() {
        return channelId;
    }

    /**
     * Gets the mesage picker strategy specified by the user
     * @return the message picker strategy
     */
    protected IBlaubotMessagePickerStrategy getMessagePicker() {
        return messagePicker;
    }

    /**
     * @return the currently used picker strategy
     */
    public MessagePickerStrategy getPickerStrategy() {
        return messagePicker.getConstant();
    }

    /**
     * Sets the MessagePickerStrategy that defines, how fast messages are send or discarded.
     * @see eu.hgross.blaubot.messaging.BlaubotChannelConfig.MessagePickerStrategy
     *
     * @param strategy the strategy to use
     * @return this channel config instance
     */
    public BlaubotChannelConfig setMessagePickerStrategy(MessagePickerStrategy strategy) {
        _setMessagePickerStrategy(strategy);
        // notify listeners
        setChanged();
        notifyObservers(Boolean.FALSE);
        return this;
    }

    /**
     * Sets the strategy without notifying the observers.
     * @see {BlaubotChannelConfig#setMessagePickerStrategy}
     * @param strategy the strategy to use
     */
    private void _setMessagePickerStrategy(MessagePickerStrategy strategy) {
        messagePicker = strategy.getPickerStrategy();
    }

    /**
     * Sets the message rate limit.
     * @param minMessageRateDelay the minimum delay between two messages that are picked to be sent in milliseconds
     * @return this channel config object
     */
    public BlaubotChannelConfig setMessageRateLimit(int minMessageRateDelay) {
        // maintain the attribute
        _setMessageRateLimit(minMessageRateDelay);

        // notify listeners
        setChanged();
        notifyObservers(Boolean.TRUE);
        return this;
    }

    /**
     * Sets the priority for messages sent through this channel.
     * @param priority the priority
     * @return this config instance
     */
    public BlaubotChannelConfig setPriority(BlaubotMessage.Priority priority) {
        _setPriority(priority);
        setChanged();
        notifyObservers(Boolean.FALSE);
        return this;
    }

    /**
     * The priority for messages sent through this channel.
     * @return the priority
     */
    public BlaubotMessage.Priority getPriority() {
        return priority;
    }

    /**
     * The minimun delay between two messages.
     * @return minimun delay between two messages as configured by the user
     */
    public int getMinMessageRateDelay() {
        return MESSAGE_RATE_NO_LIMIT == minMessageRateDelay ? 1 : minMessageRateDelay;
    }

    /**
     * Unique identifier for PickingStrategy-Implementations.
     */
    public enum MessagePickerStrategy {
        /**
         * Messages are picked one by one and all are processed sequentally
         */
        PROCESS_ALL,
        /**
         * Picks the newest message in the queue and removes all older messages without sending them.
         */
        DISCARD_OLD,
        /**
         * Picks the oldest message in the queue and discards all newer ones.
         */
        DISCARD_NEW;

        /**
         * Creates the picker for this strategy.
         * @return the picker
         * @throws UnsupportedOperationException, if the strategy requires a messageRate
         */
        private IBlaubotMessagePickerStrategy getPickerStrategy() {
            if (this.equals(PROCESS_ALL)) {
                return new ProcessAllPickerStrategy();
            } else if (this.equals(DISCARD_NEW)) {
                return new DiscardNewPickerStrategy();
            } else if (this.equals(DISCARD_OLD)) {
                return new DiscardOldPickerStrategy();
            } else {
                throw new RuntimeException("Unknown strategy");
            }
        }
    }

}
