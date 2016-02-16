package eu.hgross.blaubot.admin;


import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Abstract class for AdminMessages. The admin messages are identified by their classifiers encoded as one byte.
 * AdminMessages should be created using the {@link AdminMessageFactory}.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public abstract class AbstractAdminMessage {
	/**
	 * The admin message header length in bytes.
	 * It is actually just a one byte discriminator.
	 */
	public static int HEADER_LENGTH = 1;
    protected static final Gson gson = new Gson();
	private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
	public static final byte CLASSIFIER_CENSUS_MESSAGE = 1;
	public static final byte CLASSIFIER_NEW_PRINCE_MESSAGE = 2;
	public static final byte CLASSIFIER_KEEP_ALIVE_MESSAGE = 3;
	public static final byte CLASSIFIER_PRINCE_FOUND_A_KING_MESSAGE = 4;
	public static final byte CLASSIFIER_BOW_DOWN_TO_NEW_KING = 5;
	public static final byte CLASSIFIER_PRINCE_ACK = 6;
    public static final byte CLASSIFIER_ADD_SUBSCRIPTION = 7;
    public static final byte CLASSIFIER_REMOVE_SUBSCRIPTION = 8;
    public static final byte CLASSIFIER_STRING_MESSAGE = 9;

    public static final byte CLASSIFIER_SERVER_CONNECTION_AVAILABLE = 10;
    public static final byte CLASSIFIER_SERVER_CONNECTION_DOWN = 11;
    public static final byte CLASSIFIER_SERVER_CONNECTION_RELAY_PAYLOAD = 12;
    public static final byte CLASSIFIER_CLOSE_SERVER_CONNECTION = 13;

    public static final byte CLASSIFIER_DISCOVERED_DEVICE = 14;
	public static final byte CLASSIFIER_FINISHED_HANDSHAKE = 15;

	private byte classifier;

	protected AbstractAdminMessage(BlaubotMessage message) {
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
		r.setPayload(payload);
        r.getMessageType().setIsAdminMessage(true).setContainsPayload(true).setIsKeepAliveMessage(false).setIsFirstHop(false);
        r.setPriority(BlaubotMessage.Priority.ADMIN);
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
