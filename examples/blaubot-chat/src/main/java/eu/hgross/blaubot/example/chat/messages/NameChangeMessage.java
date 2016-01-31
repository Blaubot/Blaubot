package eu.hgross.blaubot.example.chat.messages;

import com.google.gson.Gson;

import eu.hgross.blaubot.core.BlaubotConstants;

/**
 * A DTO message for name changes
 */
public class NameChangeMessage {
    private static final Gson gson = new Gson();

    private String previousName;
    private String newName;
    private String deviceUuid;

    private long sendTimestamp = -1;

    public NameChangeMessage() {
        sendTimestamp = System.currentTimeMillis();
    }

    public String getPreviousName() {
        return previousName;
    }

    public void setPreviousName(String previousName) {
        this.previousName = previousName;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public long getSendTimestamp() {
        return sendTimestamp;
    }

    public void setSendTimestamp(long sendTimestamp) {
        this.sendTimestamp = sendTimestamp;
    }

    public String getDeviceUuid() {
        return deviceUuid;
    }

    public void setDeviceUuid(String deviceUuid) {
        this.deviceUuid = deviceUuid;
    }

    /**
     * @return the json string representation as byte array
     */
    public byte[] toBytes() {
        String json = gson.toJson(this);
        return json.getBytes(BlaubotConstants.STRING_CHARSET);
    }

    /**
     * Creates a ChatMessage from their json string bytes
     *
     * @param bytes the byte array
     * @return the chat message
     */
    public static NameChangeMessage fromBytes(byte[] bytes) {
        String json = new String(bytes, BlaubotConstants.STRING_CHARSET);
        return gson.fromJson(json, NameChangeMessage.class);
    }
}
