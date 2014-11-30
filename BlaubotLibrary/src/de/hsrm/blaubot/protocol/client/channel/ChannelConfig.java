package de.hsrm.blaubot.protocol.client.channel;

import de.hsrm.blaubot.protocol.ProtocolEnums.MessageRateType;

/**
 * configuration for an {@link IChannel}. configures sending behavior and the
 * channel id
 * 
 * @author manuelpras
 * 
 */
public class ChannelConfig {

	/**
	 * default channel config with id = 0 and no fixed message rate
	 */
	public final static ChannelConfig DEFAULT_CONFIG = (new ChannelConfig()).Id(ChannelConstants.DEFAULT_CHANNEL).MessageRate(0).MessageRateType(MessageRateType.NO_LIMIT);
	/**
	 * == Integer.MAX_VALUE
	 */
	public static final int LOWEST_PRIO = Integer.MAX_VALUE;
	/**
	 * == 0
	 */
	public static final int HIGHEST_PRIO = 0;

	private short id;
	private int priority;
	private MessageRateType messageRateType;
	private int messageRate;

	/**
	 * 
	 * @param priority
	 *            channels with a high priority (natural ordering, meaning that
	 *            -1 has a higher priority than 1) will be preferred when
	 *            sending messages
	 * @return this instance
	 */
	public ChannelConfig Priority(int priority) {
		this.priority = priority;
		return this;
	}

	/**
	 * 
	 * @param id
	 *            has to be a positive value. this id will be used to
	 *            distinguish between several channels
	 * @return this instance
	 */
	public ChannelConfig Id(short id) {
		this.id = id;
		return this;
	}

	/**
	 * 
	 * @param messageRate
	 *            specifies the cycles per second in which the messages shall be
	 *            sent. if no limit is used the infrastructure tries to send the
	 *            message as soon as the appear. but there's no warranty that
	 *            this will be successful!
	 * @return this instance
	 */
	public ChannelConfig MessageRate(int messageRate) {
		this.messageRate = messageRate;
		return this;
	}

	/**
	 * 
	 * @param messageRateType
	 *            the type of message rate
	 * @return this instance
	 */
	public ChannelConfig MessageRateType(MessageRateType messageRateType) {
		this.messageRateType = messageRateType;
		return this;
	}

	public short getId() {
		return id;
	}

	public int getPriority() {
		return priority;
	}

	public MessageRateType getMessageRateType() {
		return messageRateType;
	}

	public int getMessageRate() {
		return messageRate;
	}

	@Override
	public String toString() {
		return "ChannelConfig [id=" + id + ", priority=" + priority + ", messageRateType=" + messageRateType + ", messageRate=" + messageRate + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof ChannelConfig))
			return false;
		ChannelConfig other = (ChannelConfig) obj;
		if (id != other.id)
			return false;
		return true;
	}

}
