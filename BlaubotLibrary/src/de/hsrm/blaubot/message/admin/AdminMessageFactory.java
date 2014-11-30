package de.hsrm.blaubot.message.admin;

import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.MessageTypeFactory;
import de.hsrm.blaubot.util.Log;

/**
 * Handles message creation and validation for AdminMessages.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class AdminMessageFactory {
	private static final KeepAliveAdminMessage keepAliveAdminMessage = new KeepAliveAdminMessage();
	private static final String LOG_TAG = "AdminMessageFactory";
	
	/**
	 * Create a {@link AbstractAdminMessage} instance from a received rawMessage.
	 * @param rawMessage the rawMessage
	 * @return
	 */
	public static AbstractAdminMessage createAdminMessageFromRawMessage(BlaubotMessage rawMessage) {
		if (MessageTypeFactory.isKeepAliveMessageType(rawMessage.getMessageType())) {
			return keepAliveAdminMessage;
		}
		if (rawMessage.getPayload() == null) {
			if (Log.logErrorMessages()) {
				Log.e(LOG_TAG, "TODO: mpras fix the keepAlive channel stuff");
			}
			return keepAliveAdminMessage;
		}
		byte classifier = rawMessage.getPayload()[0];
		if(classifier == AbstractAdminMessage.CLASSIFIER_CENSUS_MESSAGE) {
			return new CensusMessage(rawMessage);
		} else if(classifier == AbstractAdminMessage.CLASSIFIER_NEW_PRINCE_MESSAGE) {
			return new PronouncePrinceAdminMessage(rawMessage);
		} else if(classifier == AbstractAdminMessage.CLASSIFIER_PRINCE_FOUND_A_KING_MESSAGE) {
			return new PrinceFoundAKingAdminMessage(rawMessage);
		} else if(classifier == AbstractAdminMessage.CLASSIFIER_BOW_DOWN_TO_NEW_KING) {
			return new BowDownToNewKingAdminMessage(rawMessage);
		} else if(classifier == AbstractAdminMessage.CLASSIFIER_PRINCE_ACK) {
			return new ACKPronouncePrinceAdminMessage(rawMessage);
		} else
			throw new InvalidClassifierException("The given classifier " + classifier + " is unknown (-> invalid).");
	}
	
	/**
	 * Validates if the classifier is valid - throws an exception otherwise.
	 * @param classifier the classifier
	 * @throws InvalidClassifierException if the classifier is unknown.
	 */
	protected static void validateClassifier(byte classifier) throws InvalidClassifierException {
		if(!(classifier == AbstractAdminMessage.CLASSIFIER_CENSUS_MESSAGE || 
		     classifier == AbstractAdminMessage.CLASSIFIER_NEW_PRINCE_MESSAGE ||
		     classifier == AbstractAdminMessage.CLASSIFIER_KEEP_ALIVE_MESSAGE ||
		     classifier == AbstractAdminMessage.CLASSIFIER_PRINCE_FOUND_A_KING_MESSAGE ||
		     classifier == AbstractAdminMessage.CLASSIFIER_BOW_DOWN_TO_NEW_KING ||
		     classifier == AbstractAdminMessage.CLASSIFIER_PRINCE_ACK)) {
			throw new InvalidClassifierException("The given classifier " + classifier + " is unknown (-> invalid).");
		}
	}
	
}
