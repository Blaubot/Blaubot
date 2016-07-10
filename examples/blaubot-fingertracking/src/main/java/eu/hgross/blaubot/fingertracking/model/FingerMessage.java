package eu.hgross.blaubot.fingertracking.model;

import java.util.Arrays;

/**
 * Simple DTO class to transfer touch positions.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class FingerMessage {
	private String clientUUID;
	private Finger[] fingers;
	private long timestamp = System.currentTimeMillis();
	private int color;

	public String getClientUUID() {
		return clientUUID;
	}

	public void setClientUUID(String clientUUID) {
		this.clientUUID = clientUUID;
	}

	public Finger[] getFingers() {
		return fingers;
	}

	public void setFingers(Finger[] fingers) {
		this.fingers = fingers;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public String toString() {
		return "FingerMessage [clientUUID=" + clientUUID + ", fingers=" + Arrays.toString(fingers) + ", timestamp=" + timestamp + "]";
	}

	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
	}
	
}
