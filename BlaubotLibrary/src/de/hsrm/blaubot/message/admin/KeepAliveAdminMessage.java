package de.hsrm.blaubot.message.admin;

import java.nio.ByteBuffer;

import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.message.MessageTypeFactory;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.protocol.ProtocolManager;

/**
 * Adapter for keep alive raw messages to admin message instances.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class KeepAliveAdminMessage extends AbstractAdminMessage {
	private static final MessageType MSG_TYPE = MessageTypeFactory.createKeepAliveMessageType();

	public KeepAliveAdminMessage() {
		super(CLASSIFIER_KEEP_ALIVE_MESSAGE);
	}
	
	public KeepAliveAdminMessage(BlaubotMessage BlaubotMessage) {
		super(BlaubotMessage);
	}
	
	@Override
	protected byte[] payloadToBytes() {
		return new byte[0];
	}

	@Override
	protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
		// nothing to do
	}

	@Override
	public String toString() {
		return "KeepAliveAdminMessage []";
	}
	
	@Override
	public BlaubotMessage toBlaubotMessage() {
		byte[] payload = classifierAndPayloadBytes();
		BlaubotMessage r = new BlaubotMessage();
		r.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
		r.setMessageType(MSG_TYPE);
		r.setPayload(payload);
		return r;
	}
	
	
}
