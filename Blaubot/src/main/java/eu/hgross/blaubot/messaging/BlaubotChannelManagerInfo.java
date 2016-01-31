package eu.hgross.blaubot.messaging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Debug infos created by the BlaubotChannelManager.
 * Exposes some usally protected data, so be cautios.
 */
public class BlaubotChannelManagerInfo {
    private final Collection<BlaubotMessageManager> messageManagers;
    private List<ChannelInfo> channels;
    private List<ConnectionInfo> connections;

    public BlaubotChannelManagerInfo(Collection<BlaubotChannel> channels, Collection<BlaubotMessageManager> messageManagers, String ownDeviceId) {
        this.channels = new ArrayList<>();
        this.connections = new ArrayList<>();

        for (BlaubotChannel channel : channels) {
            ChannelInfo channelInfo = new ChannelInfo(channel, ownDeviceId);
            this.channels.add(channelInfo);
        }

        for (BlaubotMessageManager messageManager : messageManagers) {
            ConnectionInfo connectionInfo = new ConnectionInfo(messageManager);
            this.connections.add(connectionInfo);
        }

        Collections.sort(this.channels, new Comparator<ChannelInfo>() {
            @Override
            public int compare(ChannelInfo o1, ChannelInfo o2) {
                return Integer.valueOf(o1.getChannelConfig().getChannelId()).compareTo(Integer.valueOf(o2.getChannelConfig().getChannelId()));
            }
        });

        this.messageManagers = messageManagers;
    }

    public List<ChannelInfo> getChannels() {
        return channels;
    }

    public List<ConnectionInfo> getConnections() {
        return connections;
    }

    /**
     * Calculates the number of queued bytes for all message senders.
     *
     * @return sum of bytes queued in all the senders together
     */
    public long getNumberOfQueuedMessageSenderBytes() {
        long sum = 0;
        for (BlaubotMessageManager messageManager : messageManagers) {
            sum += messageManager.getMessageSender().getQueuedBytes();
        }
        return sum;
    }

    /**
     * @return the number of messages currently queued inside all the message managers together
     */
    public long getNumberOfQueuedMessageSenderMessages() {
        long sum = 0;
        for (BlaubotMessageManager messageManager : messageManagers) {
            sum += messageManager.getMessageSender().getQueueSize();
        }
        return sum;
    }

    /**
     * @return the number of messages sent by the currently connected message managers
     */
    public long getNumberOfMessagesSent() {
        long sum = 0;
        for (BlaubotMessageManager messageManager : messageManagers) {
            sum += messageManager.getMessageSender().getSentMessages();
        }
        return sum;
    }

    /**
     * Calculates the number of sent bytes by summing it up from all active message senders
     *
     * @return sum of sent bytes by the current message senders
     */
    public long getNumberOfBytesSent() {
        long sum = 0;
        for (BlaubotMessageManager messageManager : messageManagers) {
            sum += messageManager.getMessageSender().getSentPayloadBytes();
        }
        return sum;
    }
}

