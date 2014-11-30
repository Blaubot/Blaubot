package de.hsrm.blaubot.message;

/**
 * creates predefined {@link MessageType} instances and offers methods for
 * testing if a given messageType is of a specific MessageType
 * 
 * @author manuelpras
 * 
 */
public class MessageTypeFactory {

	/**
	 * 
	 * @return a message type which is by default an admin type containing no
	 *         payload, not being broadcasted
	 */
	public static MessageType createKeepAliveMessageType() {
		return new MessageType().Admin().NoPayload().NoBroadcast().KeepAlive();
	}

	/**
	 * @return a message type which is by default an admin type containing
	 *         payload, being broadcasted
	 */
	public static MessageType createBroadcastDeviceIDMessageType() {
		return new MessageType().Admin().Broadcast().Payload().DeviceID();
	}

	/**
	 * @return a message type which is by default an admin type containing
	 *         payload, being broadcasted
	 */
	public static MessageType createBroadcastChannelIDMessageType() {
		return new MessageType().Admin().Broadcast().Payload().ChannelID();
	}

	/**
	 * tests if the given MessageType is a broadcastDeviceIDMessageType
	 * 
	 * @param messageType
	 * @return true if test successful
	 */
	public static boolean isBroadcastDeviceIDMessageType(MessageType messageType) {
		return messageType.isBitSet(MessageType.deviceIDPos);
	}

	/**
	 * tests if the given MessageType is a broadcastChannelIDMessageType
	 * 
	 * @param messageType
	 * @return true if test successful
	 */
	public static boolean isBroadcastChannelIDMessageType(MessageType messageType) {
		return messageType.isBitSet(MessageType.channelIDPos);
	}

	/**
	 * tests if the given MessageType is a keepAliveMessageType
	 * 
	 * @param messageType
	 * @return true if test successful
	 */
	public static boolean isKeepAliveMessageType(MessageType messageType) {
		return messageType.isBitSet(MessageType.keepAlivePos);
	}

}
