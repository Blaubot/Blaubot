package eu.hgross.blaubot.example.chat.messages;

/**
 * A user DTO
 */
public class ChatUser {
    private String userName;
    private String deviceUuid;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDeviceUuid() {
        return deviceUuid;
    }

    public void setDeviceUuid(String deviceUuid) {
        this.deviceUuid = deviceUuid;
    }
}
