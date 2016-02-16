package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.Arrays;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * A generic admin message that can send strings.
 * Basically introduced for unit testing.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class StringAdminMessage extends AbstractAdminMessage {
	private String string;

	public StringAdminMessage(String string) {
		super(CLASSIFIER_STRING_MESSAGE);
		this.string = string;
	}

	public StringAdminMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}

	@Override
	protected byte[] payloadToBytes() {
		return string.getBytes(BlaubotConstants.STRING_CHARSET);
	}

	@Override
	protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
		byte[] stringBytes = Arrays.copyOfRange(messagePayloadAsBytes.array(), messagePayloadAsBytes.position(), messagePayloadAsBytes.capacity());
		this.string = new String(stringBytes, BlaubotConstants.STRING_CHARSET);
	}

    public String getString() {
        return string;
    }


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((string == null) ? 0 : string.hashCode());
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
		StringAdminMessage other = (StringAdminMessage) obj;
		if (string == null) {
			if (other.string != null)
				return false; 
		} else if (!string.equals(other.string))
			return false;
		return true;
	}

}
