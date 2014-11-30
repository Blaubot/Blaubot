package de.hsrm.blaubot.message.admin;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.protocol.ProtocolManager;

/**
 * Abstract class for AdminMessage. The admin messages are identified by their classifiers encoded as one byte.
 * AdminMessages schould be created using the {@link AdminMessageFactory}.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public abstract class AbstractAdminMessage {
	private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
	public static final byte CLASSIFIER_CENSUS_MESSAGE = 1;
	public static final byte CLASSIFIER_NEW_PRINCE_MESSAGE = 1 << 1;
	public static final byte CLASSIFIER_KEEP_ALIVE_MESSAGE = 1 << 2;
	public static final byte CLASSIFIER_PRINCE_FOUND_A_KING_MESSAGE = 1 << 3;
	public static final byte CLASSIFIER_BOW_DOWN_TO_NEW_KING = 1 << 4;
	public static final byte CLASSIFIER_PRINCE_ACK = 1 << 5;
	public static final byte CLASSIFIER_PRINCE_ACCEPTED_NOMINATION = 1 << 6;
	private static final MessageType DEFAULT_MESSAGE_TYPE = new MessageType().Admin().Broadcast().Payload();
	
	private byte classifier;
	

	public AbstractAdminMessage(BlaubotMessage message) {
		ByteBuffer bb = ByteBuffer.wrap(message.getPayload());
		bb.order(BYTE_ORDER);
		classifier = bb.get();
		AdminMessageFactory.validateClassifier(classifier);
		this.setUpFromBytes(bb);
	}
	
	
	public AbstractAdminMessage(byte classifier) {
		setClassifier(classifier);
	}
	
	protected void setClassifier(byte classifier) {
		this.classifier = classifier;
	};

	public byte getClassifier() {
		return classifier;
	};
	
	/**
	 * Gets this message's byte representation (NOT including the classifier) 
	 * @return
	 */
	protected abstract byte[] payloadToBytes();
	
	/**
	 * Get this message's byte representation including the classifier bytes
	 * @return
	 */
	protected byte[] classifierAndPayloadBytes() {
		byte[] payloadBytes = payloadToBytes();
		ByteBuffer bb = ByteBuffer.allocate(payloadBytes.length + 1);
		bb.order(BYTE_ORDER);
		bb.put(classifier);
		bb.put(payloadBytes);
		bb.flip();
		return bb.array();
	};
	
	/**
	 * Should set the message's attributes based on the message's payload.
	 * The {@link ByteBuffer} contains the classifier bytes but it's current
	 * index will be set beyond that.
	 * @param messagePayloadAsBytes a ByteBuffer wrapped around the message's payload with it's current index set beyond the classifier bytes
	 */
	protected abstract void setUpFromBytes(ByteBuffer messagePayloadAsBytes);
	
	/**
	 * Creates a {@link BlaubotMessage} representing this {@link AbstractAdminMessage}
	 * @return the {@link BlaubotMessage} representation
	 */
	public BlaubotMessage toBlaubotMessage() {
		byte[] payload = classifierAndPayloadBytes();
		BlaubotMessage r = new BlaubotMessage();
		r.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
		r.setMessageType(DEFAULT_MESSAGE_TYPE);
		r.setPayload(payload);
		return r;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + classifier;
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractAdminMessage other = (AbstractAdminMessage) obj;
		if (classifier != other.classifier)
			return false;
		return true;
	}
	
	
}
