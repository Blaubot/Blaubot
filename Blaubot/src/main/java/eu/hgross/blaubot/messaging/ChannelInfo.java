package eu.hgross.blaubot.messaging;

import java.util.concurrent.ConcurrentSkipListSet;

public class ChannelInfo {
    private final String ownDeviceId;
    private BlaubotChannel channel;

    public ChannelInfo(BlaubotChannel channel, String ownDeviceId) {
        this.channel = channel;
        this.ownDeviceId = ownDeviceId;
    }

    public BlaubotChannelConfig getChannelConfig() {
        return channel.getChannelConfig();
    }

    public int getQueueSize() {
        return channel.getQueueSize();
    }

    public int getQueueCapacity() {
        return channel.getQueueCapacity();
    }

    public ConcurrentSkipListSet<String> getSubscriptions() {
        return channel.getSubscriptions();
    }

    public boolean isActive() {
        return channel.isActive();
    }

    public long getSentBytes() {
        return channel.getSentBytes();
    }

    public long getSentMessages() {
        return channel.getSentMessages();
    }

    public long getReceivedMessages() {
        return channel.getReceivedMessages();
    }

    public long getReceivedBytes() {
        return channel.getReceivedBytes();
    }

    public BlaubotChannel getChannel() {
        return channel;
    }

    public boolean isOwnDeviceSubscriberToChannel() {
        return getSubscriptions().contains(ownDeviceId);
    }
}
