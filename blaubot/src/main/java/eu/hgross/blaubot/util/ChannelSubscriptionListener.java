package eu.hgross.blaubot.util;

/**
 * Created by henna on 02.05.15.
 */

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.AddSubscriptionAdminMessage;
import eu.hgross.blaubot.admin.RemoveSubscriptionAdminMessage;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;

/**
 * Keeps track of subscriptions to channels by checking admin messages and the LifeCycleListeners.
 * It can be used to sniff the current subscriptions and channels out of the admin messages and
 * eventing system at runtime.
 */
public class ChannelSubscriptionListener implements IBlaubotAdminMessageListener, ILifecycleListener {
    private ConcurrentHashMap<Short, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private Object lock = new Object();
    private List<SubscriptionChangeListener> listeners = new CopyOnWriteArrayList<>();

    public interface SubscriptionChangeListener {
        /**
         * Called when the list of subscriptions to this channel changed
         * @param channelId
         */
        void onSubscriptionChanged(short channelId);

        /**
         * Called when a subscription was removed due to actively unsubscribing
         * or a disconnect of the device
         * @param channelId the channel
         * @param unqiueDeviceId the former subscriber's uniqueDeviceId
         */
        void onUnsubscribed(short channelId, String unqiueDeviceId);

        /**
         * Gets called if a subscription was actively made.
         * @param channelId the channel
         * @param uniqueDeviceId the new subscriber
         */
        void onSubscribed(short channelId, String uniqueDeviceId);
    }

    /**
     * The set of known channels that are used
     * @return list of channels that we know of
     */
    public Set<Short> getChannels() {
        return subscriptions.keySet();
    }

    /**
     * creates a set of the known subscribers to a specific channel id.
     * @param channelId the channel to retrieve the list of subscribers for
     * @return the set of subscribers for the given channel id
     */
    public Set<String> getSubscribersOfChannel(short channelId) {
        return subscriptions.get(channelId);
    }



    @Override
    public void onAdminMessage(AbstractAdminMessage adminMessage) {
        if(adminMessage instanceof AddSubscriptionAdminMessage) {
            AddSubscriptionAdminMessage addSubscriptionAdminMessage = (AddSubscriptionAdminMessage) adminMessage;
            final short channelId = addSubscriptionAdminMessage.getChannelId();
            addSubscription(channelId, addSubscriptionAdminMessage.getUniqueDeviceId());
        } else if (adminMessage instanceof RemoveSubscriptionAdminMessage) {
            RemoveSubscriptionAdminMessage removeSubscriptionAdminMessage = (RemoveSubscriptionAdminMessage) adminMessage;
            final short channelId = removeSubscriptionAdminMessage.getChannelId();
            removeSubscription(channelId, removeSubscriptionAdminMessage.getUniqueDeviceId());
        }
    }

    private void addSubscription(short channelId, String uniqueDeviceId) {
        synchronized (lock) {
            subscriptions.putIfAbsent(channelId, new HashSet<String>());
            Set<String> subscribers = subscriptions.get(channelId);
            subscribers.add(uniqueDeviceId);
        }
        for(SubscriptionChangeListener listener : listeners) {
            listener.onSubscriptionChanged(channelId);
            listener.onSubscribed(channelId, uniqueDeviceId);
        }
    }

    private void removeSubscription(short channelId, String uniqueDeviceId) {
        synchronized (lock) {
            Set<String> subscribers = subscriptions.get(channelId);
            if(subscribers != null) {
                subscribers.remove(uniqueDeviceId);
            }
        }
        for(SubscriptionChangeListener listener : listeners) {
            listener.onSubscriptionChanged(channelId);
            listener.onUnsubscribed(channelId, uniqueDeviceId);
        }
    }

    @Override
    public void onConnected() {

    }

    @Override
    public void onDisconnected() {
        synchronized (lock) {
            subscriptions.clear();
        }
    }

    @Override
    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {

    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
        List<Short> unsubscribedChannels = new ArrayList<>();
        // clear subscriptions regarding this device
        synchronized (lock) {
            for (Map.Entry<Short, Set<String>> entry : subscriptions.entrySet()) {
                final Set<String> subscribers = entry.getValue();
                final short channelId = entry.getKey();
                subscribers.remove(blaubotDevice.getUniqueDeviceID());
                unsubscribedChannels.add(channelId);
            }
        }
        for(Short channelId : unsubscribedChannels) {
            for(SubscriptionChangeListener listener : listeners) {
                listener.onSubscriptionChanged(channelId);
                listener.onSubscribed(channelId, blaubotDevice.getUniqueDeviceID());
            }
        }
    }

    @Override
    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {

    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {

    }

    /**
     * Adds a listener to be informed when the subscriptions change.
     * @param changeListener
     */
    public void addSubscriptionChangeListener(SubscriptionChangeListener changeListener) {
        listeners.add(changeListener);
    }

    /**
     * removes a formerly added listener
     * @param changeListener
     */
    public void removeSubscriptionChangeListener(SubscriptionChangeListener changeListener) {
        listeners.remove(changeListener);
    }

};