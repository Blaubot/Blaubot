package eu.hgross.blaubot.messaging;

/**
 * Created by henna on 06.02.15.
 */
public interface IBlaubotChannel {
    /**
     * Posts a BlaubotMessage to the message queue of this channel to be dispatched to subscribers later.
     * Will block, if the channel's message queue is full.
     * @deprecated we don't allow the pre-creation of BlaubotMessages anymore
     * @param blaubotMessage the message
     * @return true, iff the message was added to the queue or false, if the queue was full while trying to publish
     */
    boolean publish(BlaubotMessage blaubotMessage);

    /**
     * Posts a BlaubotMessage to the message queue of this channel to be dispatched to subscribers later.
     * Blocks for 'timeout' milliseconds, if the channel's message queue is full.
     * @deprecated we don't allow the pre-creation of BlaubotMessages anymore
     *
     * @param blaubotMessage the mssage
     * @param timeout the timeout in ms
     * @return true, iff the message was added to the queue or false, if the timeout elapsed before
     */
    boolean publish(BlaubotMessage blaubotMessage, long timeout);

    /**
     * Post the payload to this channel's queue.
     * The payload will be wrapped in a BlaubotMessage.
     *
     * @param payload the payload as byte array
     * @return true, iff the message was added to the queue or false, if the queue was full while trying to publish
     */
    boolean publish(byte[] payload);

    /**
     * Post the payload to this channel's queue.
     * The payload will be wrapped in a BlaubotMessage.
     * Blocks for 'timeout' milliseconds, if the channel's message queue is full.
     *
     * @param payload the payload as byte array
     * @param timeout the timeout in ms
     * @return true, iff the message was added to the queue or false, if the timeout elapsed before
     */
    boolean publish(byte[] payload, long timeout);

    /**
     * Subscribes to this channel and attaches a message listener
     *
     * @param blaubotMessageListener the listener to be informed about new messages on this channel
     */
    void subscribe(IBlaubotMessageListener blaubotMessageListener);

    /**
     * Subscribes to the channel
     */
    void subscribe();

    /**
     * Remove the subscription of this channel
     */
    void unsubscribe();

    /**
     * Gets the channel config
     * @return the channel config
     */
    BlaubotChannelConfig getChannelConfig();

    /**
     * Clears the message queue.
     * Useful in situations when the blaubot instance gets disconnected and there are still unsent
     * messages in the queue.
     */
    void clearMessageQueue();

    /**
     * Add a message listener to this channel to get notifications when messages arrive
     *
     * @param messageListener the message listener to add
     */
    void addMessageListener(IBlaubotMessageListener messageListener);

    /**
     * Removes a previously added message listener from this channel
     * If after the operation no listener is attached to this channel anymore, unsubscribe() is
     * called automatically.
     *
     * @param messageListener the listener to remove
     */
    void removeMessageListener(IBlaubotMessageListener messageListener);
}
