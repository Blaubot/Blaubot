package de.hsrm.blaubot.protocol.client.channel;

import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.protocol.IMessageListener;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.client.ProtocolClient;

/**
 * special channel which extends the normal {@link Channel} functionality by
 * broadcasting {@link BlaubotMessage}s instead of sending them to one specific
 * client in the network
 * 
 * @author manuelpras
 *
 */
public class BroadcastChannel extends Channel {

	private ProtocolClient protocolClient;

	public BroadcastChannel(ProtocolManager protocolManager, ProtocolClient protocolClient, ChannelConfig config) {
		super(protocolManager, protocolClient, config);
		this.protocolClient = protocolClient;
	}

	/**
	 * broadcasts the queued message to all devices (ignoring subscriptions)
	 */
	@Override
	protected void sendQueuedMessage(BlaubotMessage message) {
		// send message to all devices
		this.protocolClient.broadcastMessage(message);
	}

	@Override
	public void subscribe(IMessageListener listener) {
		addListener(listener);
	}

	@Override
	public void unsubscribe(IMessageListener listener) {
		removeListener(listener);
	}

}
