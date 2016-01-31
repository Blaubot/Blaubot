package eu.hgross.blaubot.ui;

/**
 * Ping message for PingViews to be sent.
 */
public class PingMessage {
    private long timestamp;
    private String senderUniqueDeviceId;

    /**
     * Send date
     *
     * @return the date when this message was sent
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp send timestamp
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSenderUniqueDeviceId() {
        return senderUniqueDeviceId;
    }

    public void setSenderUniqueDeviceId(String senderUniqueDeviceId) {
        this.senderUniqueDeviceId = senderUniqueDeviceId;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("PingMessage{");
        sb.append("senderUniqueDeviceId='").append(senderUniqueDeviceId).append('\'');
        sb.append('}');
        return sb.toString();
    }
}