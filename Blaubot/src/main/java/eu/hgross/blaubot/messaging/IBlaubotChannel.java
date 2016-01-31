package eu.hgross.blaubot.messaging;

/**
 * The channel is the main object to be used for sending and receiving messages.
 * A subscription can be made by the {@link IBlaubotChannel#subscribe(IBlaubotMessageListener)} and {@link IBlaubotChannel#subscribe()} methods.
 * Additional message listeners can be added using {@link IBlaubotChannel#addMessageListener(IBlaubotMessageListener)} or removed using {@link IBlaubotChannel#removeMessageListener(IBlaubotMessageListener)}.
 * A subscription can be removed using {@link IBlaubotChannel#unsubscribe()}.
 * 
 * A channel uses a message queue that is drained using a defined {@link IBlaubotMessagePickerStrategy}.
 * To send messages, use the publish methods.
 * 
 * The picking strategy and some other configurations can be changed at runtime, using the {@link BlaubotChannelConfig}, which is retrievable using {@link #getChannelConfig()}.
 */
public interface IBlaubotChannel {
    /**
     * Posts a BlaubotMessage to the message queue of this channel to be dispatched to subscribers later.
     * 
     * @deprecated we don't allow the pre-creation of BlaubotMessages anymore
     * @param blaubotMessage the message
     * @return true, iff the message was added to the queue or false, if the queue was full while trying to publish
     */
    boolean publish(BlaubotMessage blaubotMessage);

    /**
     * Posts a BlaubotMessage to the message queue of this channel to be dispatched to subscribers later.
     * Will block, if the channel's message queue is full.
     * @deprecated we don't allow the pre-creation of BlaubotMessages anymore
     * @param blaubotMessage the message
     * @param excludeSender if true, the message will not be dispatched back to this channel but to all other subscribers.
     * @return true, iff the message was added to the queue or false, if the queue was full while trying to publish
     */
    boolean publish(BlaubotMessage blaubotMessage, boolean excludeSender);

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
     * Posts a BlaubotMessage to the message queue of this channel to be dispatched to subscribers later.
     * Blocks for 'timeout' milliseconds, if the channel's message queue is full.
     * @deprecated we don't allow the pre-creation of BlaubotMessages anymore
     *
     * @param blaubotMessage the mssage
     * @param timeout the timeout in ms
     * @param excludeSender if true, the message will not be dispatched back to this channel but to all other subscribers.
     * @return true, iff the message was added to the queue or false, if the timeout elapsed before
     */
    boolean publish(BlaubotMessage blaubotMessage, long timeout, boolean excludeSender);

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
     *
     * @param payload the payload as byte array
     * @param excludeSender if true, the message will not be dispatched back to this channel but to all other subscribers.
     * @return true, iff the message was added to the queue or false, if the queue was full while trying to publish
     */
    boolean publish(byte[] payload, boolean excludeSender);

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
     * Post the payload to this channel's queue.
     * The payload will be wrapped in a BlaubotMessage.
     * Blocks for 'timeout' milliseconds, if the channel's message queue is full.
     *
     * @param payload the payload as byte array
     * @param timeout the timeout in ms
     * @param excludeSender if true, the message will not be dispatched back to this channel but to all other subscribers.
     * @return true, iff the message was added to the queue or false, if the timeout elapsed before
     */
    boolean publish(byte[] payload, long timeout, boolean excludeSender);
    
    
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
     * Add a message listener to this channel to get notifications when messages arrive.
     * Note that this method does not subscribe automatically subscribe to the channel.
     * Call subscribe() if you want to get messages dispatched to this channel.
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
