package de.hsrm.blaubot.protocol.client.channel;

import de.hsrm.blaubot.protocol.ProtocolEnums.MessageRateType;

public class ChannelConfigFactory {

	/**
	 * 
	 * @return a {@link ChannelConfig} with a messagerate == 0,
	 *         {@link MessageRateType} == NO_LIMIT
	 */
	public static ChannelConfig getNoLimitConfig() {
		ChannelConfig cc = new ChannelConfig();
		cc.MessageRate(0);
		cc.MessageRateType(MessageRateType.NO_LIMIT);
		return cc;
	}

	/**
	 * 
	 * @return a {@link ChannelConfig} with a messagerate == 0,
	 *         {@link MessageRateType} == {@link MessageRateType#NO_LIMIT} and priority == {@link ChannelConfig#LOWEST_PRIO}
	 */
	public static ChannelConfig getNoLimitWithStandardPriorityConfig() {
		ChannelConfig cc = getNoLimitConfig();
		cc.Priority(ChannelConfig.LOWEST_PRIO);
		return cc;
	}

}
