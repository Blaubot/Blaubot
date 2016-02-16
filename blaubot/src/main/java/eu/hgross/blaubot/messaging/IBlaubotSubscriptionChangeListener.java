package eu.hgross.blaubot.messaging;

/**
 * Notifies about changes of a ChannelManagers internal subscription store
 */
public interface IBlaubotSubscriptionChangeListener {

    /**
     * Gets called, if a subscription was added to the store.
     *
     * @param uniqueDeviceId the unique device id of the device which made the subscription
     * @param channelId the channel id to which the device subscribed to
     */
    void onSubscriptionAdded(String uniqueDeviceId, short channelId);

    /**
     * Gets called, if a subscription was removed from the store.
     *
     * @param uniqueDeviceId the unique device id of the device which removed a subscription
     * @param channelId the channel id from wich the device has unsubscribed
     */
    void onSubscriptionRemoved(String uniqueDeviceId, short channelId);
}
