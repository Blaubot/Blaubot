package eu.hgross.blaubot.admin;

import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Handles message creation and validation for AdminMessages.
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 */
public class AdminMessageFactory {
    private static final String LOG_TAG = "AdminMessageFactory";

    /**
     * Create a {@link AbstractAdminMessage} instance from a received rawMessage.
     *
     * @param rawMessage the rawMessage
     * @return
     */
    public static AbstractAdminMessage createAdminMessageFromRawMessage(BlaubotMessage rawMessage) {
        byte classifier = rawMessage.getPayload()[0];
        if (classifier == AbstractAdminMessage.CLASSIFIER_CENSUS_MESSAGE) {
            return new CensusMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_NEW_PRINCE_MESSAGE) {
            return new PronouncePrinceAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_PRINCE_FOUND_A_KING_MESSAGE) {
            return new PrinceFoundAKingAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_BOW_DOWN_TO_NEW_KING) {
            return new BowDownToNewKingAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_PRINCE_ACK) {
            return new ACKPronouncePrinceAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_ADD_SUBSCRIPTION) {
            return new AddSubscriptionAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_REMOVE_SUBSCRIPTION) {
            return new RemoveSubscriptionAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_STRING_MESSAGE) {
            return new StringAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_SERVER_CONNECTION_AVAILABLE) {
            return new ServerConnectionAvailableAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_SERVER_CONNECTION_DOWN) {
            return new ServerConnectionDownAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_SERVER_CONNECTION_RELAY_PAYLOAD) {
            return new RelayAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_CLOSE_SERVER_CONNECTION) {
            return new CloseRelayConnectionAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_DISCOVERED_DEVICE) {
            return new DiscoveredDeviceAdminMessage(rawMessage);
        } else if (classifier == AbstractAdminMessage.CLASSIFIER_FINISHED_HANDSHAKE) {
            return new FinishedHandshakeAdminMessage(rawMessage);
        } else
            throw new InvalidClassifierException("The given classifier " + classifier + " is unknown (-> invalid).");
    }

    /**
     * Validates if the classifier is valid - throws an exception otherwise.
     *
     * @param classifier the classifier
     * @throws InvalidClassifierException if the classifier is unknown.
     */
    protected static void validateClassifier(byte classifier) throws InvalidClassifierException {
        if (!(classifier == AbstractAdminMessage.CLASSIFIER_CENSUS_MESSAGE ||
                classifier == AbstractAdminMessage.CLASSIFIER_NEW_PRINCE_MESSAGE ||
                classifier == AbstractAdminMessage.CLASSIFIER_KEEP_ALIVE_MESSAGE ||
                classifier == AbstractAdminMessage.CLASSIFIER_PRINCE_FOUND_A_KING_MESSAGE ||
                classifier == AbstractAdminMessage.CLASSIFIER_BOW_DOWN_TO_NEW_KING ||
                classifier == AbstractAdminMessage.CLASSIFIER_PRINCE_ACK ||
                classifier == AbstractAdminMessage.CLASSIFIER_ADD_SUBSCRIPTION ||
                classifier == AbstractAdminMessage.CLASSIFIER_REMOVE_SUBSCRIPTION ||
                classifier == AbstractAdminMessage.CLASSIFIER_STRING_MESSAGE ||
                classifier == AbstractAdminMessage.CLASSIFIER_SERVER_CONNECTION_AVAILABLE ||
                classifier == AbstractAdminMessage.CLASSIFIER_SERVER_CONNECTION_DOWN ||
                classifier == AbstractAdminMessage.CLASSIFIER_SERVER_CONNECTION_RELAY_PAYLOAD ||
                classifier == AbstractAdminMessage.CLASSIFIER_CLOSE_SERVER_CONNECTION ||
                classifier == AbstractAdminMessage.CLASSIFIER_DISCOVERED_DEVICE ||
                classifier == AbstractAdminMessage.CLASSIFIER_FINISHED_HANDSHAKE)) {
            throw new InvalidClassifierException("The given classifier " + classifier + " is unknown (-> invalid).");
        }
    }

}
