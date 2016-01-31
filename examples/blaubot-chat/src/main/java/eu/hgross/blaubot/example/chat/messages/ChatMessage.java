package eu.hgross.blaubot.example.chat.messages;

import com.google.gson.Gson;

import eu.hgross.blaubot.core.BlaubotConstants;

/**
 * A DTO for chat messages
 */
public class ChatMessage {
    private static final Gson gson = new Gson();

    private String message;
    private ChatUser originator;
    private long sendTimestamp = -1;

    public ChatMessage() {
        sendTimestamp = System.currentTimeMillis();
    }

    public long getSendTimestamp() {
        return sendTimestamp;
    }

    public void setSendTimestamp(long sendTimestamp) {
        this.sendTimestamp = sendTimestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
    public static ChatMessage fromBytes(byte[] bytes) {
        String json = new String(bytes, BlaubotConstants.STRING_CHARSET);
        return gson.fromJson(json, ChatMessage.class);
    }
}
