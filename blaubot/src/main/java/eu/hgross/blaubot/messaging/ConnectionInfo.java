package eu.hgross.blaubot.messaging;

import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;

public class ConnectionInfo {
    private final BlaubotMessageManager messageManager;
    private final BlaubotMessageSender messageSender;
    private final BlaubotMessageReceiver messageReceiver;

    public ConnectionInfo(BlaubotMessageManager messageManager) {
        this.messageManager = messageManager;
        this.messageSender = messageManager.getMessageSender();
        this.messageReceiver = messageManager.getMessageReceiver();
    }

    public int getMessageSenderQueueSize() {
        return messageSender.getQueueSize();
    }

    public IBlaubotDevice getBlaubotDevice() {
        return messageSender.getBlaubotConnection().getRemoteDevice();
    }

    public long getSentMessages() {
        return messageSender.getSentMessages();
    }

    public long getSentPayloadBytes() {
        return messageSender.getSentPayloadBytes();
    }

    public IBlaubotConnection getBlaubotConnection() {
        return messageSender.getBlaubotConnection();
    }

    public long getReceivedMessages() {
        return messageReceiver.getReceivedMessages();
    }

    public long getReceivedPayloadBytes() {
        return messageReceiver.getReceivedPayloadBytes();
    }
}
