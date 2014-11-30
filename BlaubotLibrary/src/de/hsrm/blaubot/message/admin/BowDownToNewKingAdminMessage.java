package de.hsrm.blaubot.message.admin;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.message.BlaubotMessage;

/**
 * Informs the peasants and the prince that they have to join another king's kingdom.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BowDownToNewKingAdminMessage extends AbstractAdminMessage {
	private String uniqueDeviceId;
	
	public BowDownToNewKingAdminMessage(String uniqueDeviceId) {
		super(CLASSIFIER_BOW_DOWN_TO_NEW_KING);
		this.uniqueDeviceId = uniqueDeviceId;
	}
	
	public BowDownToNewKingAdminMessage(IBlaubotDevice newKingDevice) {
		super(CLASSIFIER_BOW_DOWN_TO_NEW_KING);
		this.uniqueDeviceId = newKingDevice.getUniqueDeviceID();
	}
	
	public BowDownToNewKingAdminMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}
	
	
	@Override
	protected byte[] payloadToBytes() {
		String strToSend = uniqueDeviceId;
		try {
			return strToSend.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			// every VM supports UTF-8
			throw new RuntimeException("VM does not support UTF-8 - are you doing some strange things?");
		}
	}

	@Override
	protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
		byte[] stringBytes = Arrays.copyOfRange(messagePayloadAsBytes.array(), messagePayloadAsBytes.position(), messagePayloadAsBytes.capacity());
		String readString = new String(stringBytes, BlaubotConstants.STRING_CHARSET);
		uniqueDeviceId = readString;
	}

	/**
	 * 
	 * @return the discovered king's unique device id
	 */
	public String getNewKingsUniqueDeviceId() {
		return uniqueDeviceId;
	}
	
	@Override
	public String toString() {
		return "BowDownToNewKingAdminMessage [uniqueDeviceId=" + uniqueDeviceId + "]";
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
		BowDownToNewKingAdminMessage other = (BowDownToNewKingAdminMessage) obj;
		if (uniqueDeviceId == null) {
			if (other.uniqueDeviceId != null)
				return false;
		} else if (!uniqueDeviceId.equals(other.uniqueDeviceId))
			return false;
		return true;
	}
	
	
}
