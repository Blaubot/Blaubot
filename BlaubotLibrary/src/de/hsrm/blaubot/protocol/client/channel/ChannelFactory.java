package de.hsrm.blaubot.protocol.client.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.protocol.ProtocolContext;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.client.ProtocolClient;
import de.hsrm.blaubot.util.Log;

/**
 * factory for creating and managing {@link Channel} instances with minimum
 * effort.
 * 
 * @author manuelpras
 *
 */
public class ChannelFactory {

	protected static final String TAG = "ChannelFactory";

	private ProtocolManager protocolManager;
	private ProtocolClient protocolClient;
	private List<Short> usedChannelIDs;
	private ConcurrentHashMap<Short, IChannel> createdChannels;
	private final Object CHANNEL_ID_LOCK = new Object();
	// channels:
	private ChannelConfigFactory channelConfigFactory;
	// admin
	private BroadcastChannel adminBroadcastChannel;
	private BroadcastChannel connectionLayerBroadcastChannel;
	private BroadcastChannel channelSubscriptionChannel;
	private ConcurrentHashMap<String, Channel> adminDeviceChannels;
	private List<Channel> adminChannels;
	// user
	private List<Channel> userChannels;

	/**
	 * @param protocolManager
	 *            current {@link ProtocolManager}
	 * @param protocolClient
	 *            current {@link ProtocolClient}
	 */
	public ChannelFactory(ProtocolManager protocolManager, ProtocolClient protocolClient) {
		this.protocolManager = protocolManager;
		this.protocolClient = protocolClient;

		this.usedChannelIDs = new ArrayList<Short>();
		this.userChannels = new ArrayList<Channel>();
		this.adminChannels = new ArrayList<Channel>();
		this.createdChannels = new ConcurrentHashMap<Short, IChannel>();
		this.adminDeviceChannels = new ConcurrentHashMap<String, Channel>();
		this.channelConfigFactory = new ChannelConfigFactory();
	}

	// ================================================================================
	// Base Stuff
	// ================================================================================

	/**
	 * @return all {@link Channel} instances which have been created using this
	 *         factory instance
	 */
	public ConcurrentHashMap<Short, IChannel> getCreatedChannels() {
		return this.createdChannels;
	}

	/**
	 * add shelved subscriptions for the given channel if any and add channel to
	 * created channels list
	 * 
	 * @param channel
	 */
	private void onNewChannelCreated(Channel channel) {
		short channelID = channel.getConfig().getId();
		this.createdChannels.putIfAbsent(channelID, channel);
	}

	private final Object CREATE_LOCK = new Object();

	/**
	 * create and activate a new channel with the given config if not already
	 * existing
	 * 
	 * @param config
	 * @return
	 */
	public Channel createUserChannel(ChannelConfig config) {
		checkAndStoreChannelID(config.getId());
		Channel channel = new Channel(protocolManager, protocolClient, config);
		// block while checking and adding channel
		synchronized (CREATE_LOCK) {
			// throw exception if already existing (equals if id is the same)
			if (this.userChannels.contains(channel)) {
				throw new RuntimeException("Channel with given Config already existing!");
			}
			this.userChannels.add(channel);
		}
		onNewChannelCreated(channel);

		channel.activate();
		return channel;
	}

	/**
	 * deactivate all registered channels, including admin channels, excluding
	 * broadcast channel
	 * 
	 * @see deactivateChannel(Channel channel)
	 */
	public void deactivateAllChannels() {
		for (Channel channel : this.userChannels) {
			deactivateChannel(channel);
		}
		for (Channel channel : this.adminChannels) {
			deactivateChannel(channel);
		}
		for (Channel channel : this.adminDeviceChannels.values()) {
			deactivateChannel(channel);
		}
	}

	/**
	 * deactivate the given channel by stopping its executor service and
	 * clearing the message queue
	 * 
	 * @param channel
	 */
	public void deactivateChannel(Channel channel) {
		if (channel == null)
			throw new NullPointerException("Got null instead of a channel.");
		channel.deactivate();
	}

	/**
	 * activate all registered channels (including admin channels!)
	 * 
	 * @see activateChannel(Channel channel)
	 */
	public void activateAllChannels() {
		for (Channel channel : this.userChannels) {
			activateChannel(channel);
		}
		for (Channel channel : this.adminChannels) {
			activateChannel(channel);
		}
		for (Channel channel : this.adminDeviceChannels.values()) {
			activateChannel(channel);
		}
	}

	/**
	 * activate the given channel by starting its executor service and
	 * initializing the message queue
	 * 
	 * @param channel
	 * @throws NullPointerException
	 *             if channel is null
	 */
	public void activateChannel(Channel channel) {
		if (channel == null) {
			return;
		}
		channel.activate();
	}

	// ================================================================================
	// Admin Stuff
	// ================================================================================

	private final static int HIGHEST_PRIORITY = Integer.MIN_VALUE;

	private static final long ADMIN_DEVICE_CHANNEL_CREATION_TIMEOUT = 3;
	private final int adminBroadcastChannelPriority = HIGHEST_PRIORITY;
	private final int adminDeviceChannelPriority = HIGHEST_PRIORITY + 1;
	private final int channelSubscriptionChannelPriority = HIGHEST_PRIORITY + 2;
	private final int connectionLayerBroadcastChannelPriority = HIGHEST_PRIORITY + 3;

	/**
	 * create and activate a new channel with the given channel ID if not
	 * already existing. config is of type: new ChannelConfig(channelID, 0,
	 * MessageRateType.NO_LIMIT, Integer.MIN_VALUE + 1);
	 * 
	 * @param channelID
	 * @return
	 */
	private synchronized BroadcastChannel createAdminChannel(short channelID, int priority) {
		checkAndStoreChannelID(channelID);
		ChannelConfig config = this.channelConfigFactory.getNoLimitConfig().Id(channelID).Priority(priority);
		BroadcastChannel channel = new BroadcastChannel(protocolManager, protocolClient, config);
		this.adminChannels.add(channel);

		onNewChannelCreated(channel);
		channel.activate();
		return channel;
	}

	/**
	 * @return the broadcast {@link Channel} used for broadcasting admin
	 *         messages (only used by admin layer)
	 */
	public BroadcastChannel getAdminBroadcastChannel() {
		if (adminBroadcastChannel == null) {
			short channelID = ChannelConstants.ADMIN_BROADCAST_CHANNEL;
			this.adminBroadcastChannel = createAdminChannel(channelID, this.adminBroadcastChannelPriority);
		}
		return adminBroadcastChannel;
	}

	/**
	 * @param device
	 * @return corresponding channel for given device
	 */
	public Channel getAdminDeviceChannel(IBlaubotDevice device) {
		return getAdminDeviceChannel(device.getUniqueDeviceID());
	}

	/**
	 * creates channel and adds it to mapping if necessary. blocking call!
	 * 
	 * @param uniqueDeviceId
	 * @return corresponding channel for given unique device id or null if
	 *         timeout occurs
	 */
	public Channel getAdminDeviceChannel(final String uniqueDeviceId) {
		// create channel, add it to map if necessary, return it
		final ProtocolContext context = this.protocolManager.getContext();
		FutureTask<Short> future = context.getShortDeviceId(uniqueDeviceId);
		try {
			Short shortDeviceId = future.get(ADMIN_DEVICE_CHANNEL_CREATION_TIMEOUT, TimeUnit.SECONDS);
			Short channelId = context.createChannelId(uniqueDeviceId, shortDeviceId);
			// store ID in order to check IDs created in the future
			storeChannelID(channelId);
			ChannelConfig config = this.channelConfigFactory.getNoLimitConfig().Id(channelId).Priority(adminDeviceChannelPriority);
			Channel newChannel = new Channel(protocolManager, protocolClient, config);
			adminDeviceChannels.putIfAbsent(uniqueDeviceId, newChannel);

			// get old value or (if old value was null) new created channel
			Channel actualChannel = adminDeviceChannels.get(uniqueDeviceId);
			onNewChannelCreated(actualChannel);
			actualChannel.activate();
			return actualChannel;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
		String msg = String.format("could not get device admin channel (timeout?) for unique device id: %s (on device = %s)", uniqueDeviceId, context.getOwnUniqueDeviceID());
		if (Log.logErrorMessages())
			Log.e(TAG, msg);
		throw new RuntimeException(msg);
	}

	/**
	 * blocks until the channel for the given own unique device ID is available,
	 * then returns it
	 * 
	 * @return
	 */
	public Channel getOwnDeviceAdminChannel() {
		String ownUniqueDeviceID = this.protocolManager.getContext().getOwnUniqueDeviceID();
		return getAdminDeviceChannel(ownUniqueDeviceID);
	}

	/**
	 * broadcast channel for connection layer (@hgross), uses channel id == -1
	 */
	public BroadcastChannel getConnectionLayerBroadcastChannel() {
		if (connectionLayerBroadcastChannel == null) {
			short channelID = (short) -1;
			this.connectionLayerBroadcastChannel = createAdminChannel(channelID, this.connectionLayerBroadcastChannelPriority);
		}
		return connectionLayerBroadcastChannel;
	}

	/**
	 * channel for sending channel subscription messages
	 */
	public BroadcastChannel getChannelSubscriptionChannel() {
		if (this.channelSubscriptionChannel == null) {
			short channelID = (short) -2;
			this.channelSubscriptionChannel = createAdminChannel(channelID, this.channelSubscriptionChannelPriority);
		}
		return this.channelSubscriptionChannel;
	}

	/**
	 * checks if the given channel ID is already in use by another channel and
	 * throws a runtime exception if so. otherwise adds channel ID to list of
	 * used IDs for future checks
	 * 
	 * @param channelID
	 */
	private void checkAndStoreChannelID(short channelID) {
		synchronized (CHANNEL_ID_LOCK) {
			if (this.usedChannelIDs.contains(channelID)) {
				throw new RuntimeException("ID for channel already in use by another channel!");
			}
			storeChannelID(channelID);
		}
	}

	private synchronized void storeChannelID(short channelID) {
		this.usedChannelIDs.add(channelID);
	}

}
