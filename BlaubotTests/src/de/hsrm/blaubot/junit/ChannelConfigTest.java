package de.hsrm.blaubot.junit;

import static org.junit.Assert.*;

import org.junit.Test;

import de.hsrm.blaubot.protocol.ProtocolEnums.MessageRateType;
import de.hsrm.blaubot.protocol.client.channel.ChannelConfig;
import de.hsrm.blaubot.protocol.client.channel.ChannelConfigFactory;

public class ChannelConfigTest {

	final ChannelConfigFactory ccf = new ChannelConfigFactory();

	@Test
	public void testNoLimitConfig() {
		// create a channel config with no limit message rate type and check
		// values for correctness
		ChannelConfig noLimitConfig = ccf.getNoLimitConfig();
		assertEquals(MessageRateType.NO_LIMIT, noLimitConfig.getMessageRateType());
	}

	@Test
	public void testNoLimitWithStandardPriorityConfig() {
		// create a channel config with no limit message rate type and lowest
		// priority and check values for correctness
		ChannelConfig noLimitWithStandardPriorityConfig = ccf.getNoLimitWithStandardPriorityConfig();
		assertEquals(MessageRateType.NO_LIMIT, noLimitWithStandardPriorityConfig.getMessageRateType());
		assertEquals(ChannelConfig.LOWEST_PRIO, noLimitWithStandardPriorityConfig.getPriority());
	}

	@Test
	public void testConfigPriority() {
		ChannelConfig cc = new ChannelConfig();
		// set a priority to the config
		int priority = 7;
		cc.Priority(priority);
		// then another value like the message rate
		cc.MessageRate(0);
		// and check if the returned priority is correct
		assertEquals(priority, cc.getPriority());
	}

	@Test
	public void testConfigId() {
		ChannelConfig cc = new ChannelConfig();
		// set a id to the config
		short id = 7;
		cc.Id(id);
		// then another value like the message rate
		cc.MessageRate(0);
		// and check if the returned id is correct
		assertEquals(id, cc.getId());
	}

}
