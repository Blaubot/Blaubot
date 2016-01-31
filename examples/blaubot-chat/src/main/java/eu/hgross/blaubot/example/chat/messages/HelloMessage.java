package eu.hgross.blaubot.example.chat.messages;

import com.google.gson.Gson;

import eu.hgross.blaubot.core.BlaubotConstants;

/**
 * Message to say hello ;-)
 */
public class HelloMessage {
    private static final Gson gson = new Gson();
    private ChatUser originator;
    private long sendTimestamp = -1;

    public HelloMessage() {
        sendTimestamp = System.currentTimeMillis();
    }

    public long getSendTimestamp() {
        return sendTimestamp;
    }

    public void setSendTimestamp(long sendTimestamp) {
        this.sendTimestamp = sendTimestamp;
    }

    public ChatUser getOriginator() {
        return originator;
    }

    public void setOriginator(ChatUser originator) {
        this.originator = originator;
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
    public static HelloMessage fromBytes(byte[] bytes) {
        String json = new String(bytes, BlaubotConstants.STRING_CHARSET);
        return gson.fromJson(json, HelloMessage.class);
    }
}
