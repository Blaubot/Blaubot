package eu.hgross.blaubot.messaging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Debug infos created by the BlaubotChannelManager.
 * Exposes some usally protected data, so be cautios.
 *
 */
public class BlaubotChannelManagerInfo {
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
    }

    public List<ChannelInfo> getChannels() {
        return channels;
    }

    public List<ConnectionInfo> getConnections() {
        return connections;
    }
}

