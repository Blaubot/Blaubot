package de.hsrm.blaubot.message.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.message.BlaubotMessage;

/**
 * Pronounces a new prince device by it's uniqueId string.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class PronouncePrinceAdminMessage extends AbstractAdminMessage {
	private String uniqueDeviceId;

	public PronouncePrinceAdminMessage(String uniqueDeviceId) {
		super(CLASSIFIER_NEW_PRINCE_MESSAGE);
		this.uniqueDeviceId = uniqueDeviceId;
	}

	public PronouncePrinceAdminMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}

	@Override
	protected byte[] payloadToBytes() {
		return uniqueDeviceId.getBytes(BlaubotConstants.STRING_CHARSET);
	}

	@Override
	protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
		byte[] stringBytes = Arrays.copyOfRange(messagePayloadAsBytes.array(), messagePayloadAsBytes.position(), messagePayloadAsBytes.capacity());
		this.uniqueDeviceId = new String(stringBytes, BlaubotConstants.STRING_CHARSET);
	}

	/**
	 * @return the new prince device's uniqueId string
	 */
	public String getUniqueDeviceId() {
		return uniqueDeviceId;
	}

	@Override
	public String toString() {
		return "PronouncePrinceAdminMessage [uniqueDeviceId=" + uniqueDeviceId + "]";
	}

	public static void main(String args[]) {
		PronouncePrinceAdminMessage m = new PronouncePrinceAdminMessage("blabla");
		PronouncePrinceAdminMessage around = new PronouncePrinceAdminMessage(m.toBlaubotMessage());
		System.out.println("" + m.uniqueDeviceId.length() + "___" + around.uniqueDeviceId.length());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((uniqueDeviceId == null) ? 0 : uniqueDeviceId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		PronouncePrinceAdminMessage other = (PronouncePrinceAdminMessage) obj;
		if (uniqueDeviceId == null) {
			if (other.uniqueDeviceId != null)
				return false; 
		} else if (!uniqueDeviceId.equals(other.uniqueDeviceId))
			return false;
		return true;
	}

}
