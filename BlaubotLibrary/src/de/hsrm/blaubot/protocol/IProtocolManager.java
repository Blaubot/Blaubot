package de.hsrm.blaubot.protocol;

import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.protocol.client.channel.Channel;
import de.hsrm.blaubot.protocol.client.channel.ChannelFactory;

/**
 * Interface of ProtocolManager for use within Blaubot only. User of this API is
 * Blaubot and Blaubots ConnectionManagement. The {@link IProtocolManager}
 * manages the protocol layer and is the interface between protocol- and
 * connection-layer
 * 
 */
public interface IProtocolManager extends IProtocolLifecycle {

	/**
	 * @return the channel factory of this {@link Blaubot} instance
	 */
	public ChannelFactory getChannelFactory();

	/**
	 * call this method in order to indicate whether or not this {@link Blaubot}
	 * instance is the protocol master which multiplexes {@link BlaubotMessage}s
	 * between the clients in this network
	 * 
	 * @param isMaster
	 */
	public void setMaster(boolean isMaster);

	/**
	 * add a new connection to the {@link IProtocolManager} which then will be
	 * used by this instance in order to send messages to the corresponding
	 * client.
	 * 
	 * @param connection
	 */
	public void addConnection(IBlaubotConnection connection);

	/**
	 * causes the protocol layer to be reset. this means that the current
	 * ProtocolContext gets reset and all activated channels get deactivated
	 */
	public void reset();

	/**
	 * gets called if there is an unsubscription for the given channel. the
	 * {@link IProtocolManager} will then broadcast this information to all
	 * clients in the network
	 * 
	 * @param channel
	 */
	void onChannelUnsubscription(Channel channel);

	/**
	 * gets called if there is an subscription for the given channel. the
	 * {@link IProtocolManager} will then broadcast this information to all
	 * clients in the network
	 * 
	 * @param channel
	 */
	void onChannelSubscription(Channel channel);

}