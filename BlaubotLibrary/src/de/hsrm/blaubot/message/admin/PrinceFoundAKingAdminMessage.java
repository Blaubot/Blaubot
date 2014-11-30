package de.hsrm.blaubot.message.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.message.BlaubotMessage;

/**
 * Informs the king about the discovery of a new king.
 * 
 * TODO: maybe the message needs additional informations like the adapter(!)
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class PrinceFoundAKingAdminMessage extends AbstractAdminMessage {
	private String uniqueDeviceId;
	
	public PrinceFoundAKingAdminMessage(String uniqueDeviceId) {
		super(CLASSIFIER_PRINCE_FOUND_A_KING_MESSAGE);
		this.uniqueDeviceId = uniqueDeviceId;
	}
	
	public PrinceFoundAKingAdminMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}
	
	
	@Override
	protected byte[] payloadToBytes() {
		String strToSend = uniqueDeviceId;
		return strToSend.getBytes(BlaubotConstants.STRING_CHARSET);
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
	public String getKingsUniqueDeviceId() {
		return uniqueDeviceId;
	}
	
	@Override
	public String toString() {
		return "PrinceFoundAKingAdminMessage [uniqueDeviceId=" + uniqueDeviceId + "]";
	}
}
