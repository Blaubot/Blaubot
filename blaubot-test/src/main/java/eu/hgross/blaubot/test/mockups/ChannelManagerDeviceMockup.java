package eu.hgross.blaubot.test.mockups;

import java.util.UUID;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;

/**
 * A class that holds a BlaubotChannelManager to emulate a mobile device.
 */
public class ChannelManagerDeviceMockup {
    public final BlaubotChannelManager channelManager;
    public final IBlaubotDevice mockDevice;
    private final UUID uuid = UUID.randomUUID(); // for hashCode and equals
    public ChannelManagerDeviceMockup(final String uniqueId) {
        this.channelManager = new BlaubotChannelManager(uniqueId);
        this.mockDevice = new BlaubotDevice(uniqueId);
    }

    /**
     * Emulates a connection to another device on the ChannelManager layer
     * @param mockupDevice the other mocked up device
     */
    public void connectToOtherDevice(ChannelManagerDeviceMockup mockupDevice) {
        // create a mock connection pair
        IBlaubotConnection connection1 = new BlaubotConnectionQueueMock(mockupDevice.mockDevice);
        IBlaubotConnection connection2 = ((BlaubotConnectionQueueMock) connection1).getOtherEndpointConnection(mockDevice);
        channelManager.addConnection(connection1);
        mockupDevice.channelManager.addConnection(connection2);
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChannelManagerDeviceMockup that = (ChannelManagerDeviceMockup) o;

        if (channelManager != null ? !channelManager.equals(that.channelManager) : that.channelManager != null)
            return false;
        if (mockDevice != null ? !mockDevice.equals(that.mockDevice) : that.mockDevice != null)
            return false;
        if (uuid != null ? !uuid.equals(that.uuid) : that.uuid != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = channelManager != null ? channelManager.hashCode() : 0;
        result = 31 * result + (mockDevice != null ? mockDevice.hashCode() : 0);
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        return result;
    }
}
