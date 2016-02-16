package eu.hgross.blaubot.util;

import java.util.concurrent.atomic.AtomicBoolean;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotKingdom;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.AddSubscriptionAdminMessage;
import eu.hgross.blaubot.admin.RemoveSubscriptionAdminMessage;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;

/**
 * Handles the state of a subscription using an AdminMessageListener and LifecycleListener.
 * Has to be attached to the channelmanager/lifecycle dispatcher before start like this:
 *
 * watcher.registerWithBlaubot(myBlaubot)
 * XOR
 * watcher.registerWithBlaubotServer(myBlaubot)
 *
 * if a subscription was made, onSubscribed(..) is called.
 * if a subscription is no longer valid, onUnsubscribed() is called.
 */
public abstract class SubscriptionWatcher implements ILifecycleListener, IBlaubotAdminMessageListener {
    private final short channelId;
    private AtomicBoolean subscribed = new AtomicBoolean(false);
    private IBlaubotDevice ownDevice;

    public SubscriptionWatcher(short channelId) {
        this.channelId = channelId;
    }

    public void registerWithBlaubot(Blaubot blaubot) {
        blaubot.getChannelManager().addAdminMessageListener(this);
        blaubot.addLifecycleListener(this);
        ownDevice = blaubot.getOwnDevice();
    }

    public void unregisterFromBlaubot(Blaubot blaubot) {
        blaubot.getChannelManager().removeAdminMessageListener(this);
        blaubot.removeLifecycleListener(this);
        ownDevice = null;
    }

    public void registerWithBlaubotKingdom(BlaubotKingdom blaubotKingdom) {
        // TODO: the blaubotKingdom needs a method to get to know when the kingdom was disconnected!
        blaubotKingdom.addLifecycleListener(this);
        blaubotKingdom.getChannelManager().addAdminMessageListener(this);
        ownDevice = blaubotKingdom.getOwnDevice();
    }

    public void unregisterFromBlaubotKingdom(BlaubotKingdom blaubotKingdom) {
        blaubotKingdom.removeLifecycleListener(this);
        blaubotKingdom.getChannelManager().removeAdminMessageListener(this);
        ownDevice = null;
    }

    @Override
    public void onAdminMessage(AbstractAdminMessage adminMessage) {
        final String ourUniqueDeviceId = ownDevice.getUniqueDeviceID();
        if (adminMessage instanceof AddSubscriptionAdminMessage) {
            final AddSubscriptionAdminMessage addSubscriptionAdminMessage = (AddSubscriptionAdminMessage) adminMessage;
            final String uniqueDeviceId = addSubscriptionAdminMessage.getUniqueDeviceId();

            final boolean ourUniqueId = uniqueDeviceId.equals(ourUniqueDeviceId);
            final boolean ourChannel = addSubscriptionAdminMessage.getChannelId() == channelId;
            if (ourChannel && ourUniqueId) {
                subscribed.set(true);
                onSubscribed(channelId);
            }
        } else if (adminMessage instanceof RemoveSubscriptionAdminMessage) {
            final RemoveSubscriptionAdminMessage removeSubscriptionAdminMessage = (RemoveSubscriptionAdminMessage) adminMessage;
            final boolean ourUniqueId = removeSubscriptionAdminMessage.getUniqueDeviceId().equals(ourUniqueDeviceId);
            final boolean ourChannel = removeSubscriptionAdminMessage.getChannelId() == channelId;
            if (ourChannel && ourUniqueId) {
                subscribed.set(false);
                onUnsubscribed(channelId);
            }
        }
    }

    /**
     * Called when a subscription to the channel was made.
     * @param channelId the channel id
     */
    public abstract void onUnsubscribed(short channelId);

    /**
     * Called when the subscription was lost due to an unsubscribe or onDisconnected event
     * @param channelId the channel id
     */
    public abstract void onSubscribed(short channelId);

    @Override
    public void onConnected() {

    }

    @Override
    public void onDisconnected() {
        subscribed.set(false);
        onUnsubscribed(channelId);
    }

    @Override
    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {

    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {

    }

    @Override
    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {

    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {

    }

    /**
     * @return true, iff the registered instance is currently subscribed to the channel
     */
    public boolean isSubscribed() {
        return subscribed.get();
    }
}
