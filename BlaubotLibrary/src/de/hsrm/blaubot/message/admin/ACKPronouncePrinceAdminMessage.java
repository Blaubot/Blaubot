package de.hsrm.blaubot.message.admin;

import java.io.UnsupportedEncodingException;
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
public class ACKPronouncePrinceAdminMessage extends AbstractAdminMessage {
	private String uniqueDeviceId;
	
	public ACKPronouncePrinceAdminMessage(String uniqueDeviceId) {
		super(CLASSIFIER_PRINCE_ACK);
		this.uniqueDeviceId = uniqueDeviceId;
	}
	
	public ACKPronouncePrinceAdminMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}
	
	@Override
	protected byte[] payloadToBytes() {
		try {
			return uniqueDeviceId.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			// every VM supports UTF-8
			throw new RuntimeException("VM does not support UTF-8 - are you doing some strange things?");
		}
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
		return "ACKPronouncePrinceAdminMessage [uniqueDeviceId=" + uniqueDeviceId + "]";
	}
	
	
	public static void main(String args[]) {
		ACKPronouncePrinceAdminMessage m = new ACKPronouncePrinceAdminMessage("blabla");
		ACKPronouncePrinceAdminMessage around = new ACKPronouncePrinceAdminMessage(m.toBlaubotMessage());
		System.out.println(""+m.uniqueDeviceId.length() + "___" + around.uniqueDeviceId.length());
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
		ACKPronouncePrinceAdminMessage other = (ACKPronouncePrinceAdminMessage) obj;
		if (uniqueDeviceId == null) {
			if (other.uniqueDeviceId != null)
				return false;
		} else if (!uniqueDeviceId.equals(other.uniqueDeviceId))
			return false;
		return true;
	}
	
	
}
