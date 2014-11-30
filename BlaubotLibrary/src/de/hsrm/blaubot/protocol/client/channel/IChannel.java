package de.hsrm.blaubot.protocol.client.channel;

import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.protocol.IMessageListener;

/**
 * implement this interface to provide basic blaubot channel functionality like
 * posting data or adding / removing subscriptions
 * 
 * @author manuelpras
 *
 */
public interface IChannel {

	/**
	 * callback for new messages for this channel
	 * 
	 * @param message
	 * @param sourceDeviceUniqueID
	 * @param sourceProtocolDeviceID
	 */
	public void onMessage(BlaubotMessage message);

	/**
	 * config of this channel (id, sending policy etc.)
	 * 
	 * @return
	 */
	public ChannelConfig getConfig();

	/**
	 * post the given payload via this channel
	 * 
	 * @param payload
	 */
	public void post(byte[] payload);

	/**
	 * post the given message via this channel
	 * 
	 * @param message
	 */
	public void post(BlaubotMessage message);

	/**
	 * subscribe to this channel and receive updates as soon as new message come
	 * in via this channel
	 * 
	 * @param listener
	 *            listener for callback
	 */
	public void subscribe(IMessageListener listener);

	/**
	 * unsubscribe from this channel in order to not receive updates anymore
	 * 
	 * @param listener
	 */
	public void unsubscribe(IMessageListener listener);

	/**
	 * add "mute" listener (means that subscription won't be broadcasted ->
	 * usefull for master which only listens for incoming messages but is no
	 * real subscriber)
	 * 
	 * @param listener
	 */
	public void addListener(IMessageListener listener);

	/**
	 * remove given listener from list
	 * 
	 * @param listener
	 */
	public void removeListener(IMessageListener listener);

}
