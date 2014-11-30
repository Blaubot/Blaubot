package de.hsrm.blaubot.protocol.client.channel;


/**
 * Constants for {@link ChannelConfig} objects defining a basic set of channels
 * for internal communication channels. Use negative values for admin configs!!!
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class ChannelConstants {
	public static final short DEFAULT_CHANNEL = 0;
	public static final short CONNECTION_ADMIN_CHANNEL = -1;
	public static final short PROTOCOL_ADMIN_CHANNEL = -2;
	public static final short ADMIN_BROADCAST_CHANNEL = Short.MIN_VALUE;

	public static final ChannelConfig CONNECTION_ADMIN_CHANNEL_CONFIG = new ChannelConfigFactory().getNoLimitConfig().Id(CONNECTION_ADMIN_CHANNEL);
	public static final ChannelConfig PROTOCOL_ADMIN_CHANNEL_CONFIG = new ChannelConfigFactory().getNoLimitConfig().Id(PROTOCOL_ADMIN_CHANNEL);
}
