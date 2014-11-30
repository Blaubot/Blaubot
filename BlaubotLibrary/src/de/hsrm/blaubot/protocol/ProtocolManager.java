package de.hsrm.blaubot.protocol;

import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionListener;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.core.statemachine.states.KingState;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.message.MessageTypeFactory;
import de.hsrm.blaubot.mock.BlaubotConnectionQueueMock;
import de.hsrm.blaubot.mock.BlaubotDeviceMock;
import de.hsrm.blaubot.protocol.client.ProtocolClient;
import de.hsrm.blaubot.protocol.client.channel.BroadcastChannel;
import de.hsrm.blaubot.protocol.client.channel.Channel;
import de.hsrm.blaubot.protocol.client.channel.ChannelFactory;
import de.hsrm.blaubot.protocol.master.ProtocolMaster;
import de.hsrm.blaubot.util.Log;

/**
 * Manages protocol stuff like sending {@link BlaubotMessage}s to certain
 * {@link IBlaubotDevice}s or broadcasting messages if is set to isMaster ==
 * true.
 * 
 * @author manuelpras
 * 
 */
public class ProtocolManager implements IProtocolManager {

	private static String TAG = "ProtocolManager";
	public static final byte PROTOCOL_VERSION = 1;

	private ProtocolMaster protocolMaster = null;
	private final ChannelFactory channelFactory;
	private final ProtocolClient protocolClient;
	private final BroadcastChannel adminBroadcastChannel;
	private final ProtocolContext protocolContext;
	private final CopyOnWriteArraySet<JoinListener> joinListeners;
	private final AtomicBoolean isJoined;
	private BroadcastChannel channelSubscriptionChannel;

	public ProtocolManager(ProtocolContext context) {
		this.protocolContext = context;
		this.protocolClient = new ProtocolClient(context);

		// create unique factory for this app / instance
		this.channelFactory = new ChannelFactory(this, protocolClient);
		context.setChannelFactory(this.channelFactory);
		// get broadcast channel for sending admin messages
		this.adminBroadcastChannel = this.channelFactory.getAdminBroadcastChannel();
		this.channelSubscriptionChannel = channelFactory.getChannelSubscriptionChannel();
		// listen for incoming messages on this channel
		this.channelSubscriptionChannel.addListener(subscriptionChannelListener);
		this.joinListeners = new CopyOnWriteArraySet<JoinListener>();
		this.isJoined = new AtomicBoolean(false);
	}

	/**
	 * 
	 * @return the {@link ProtocolContext} of this instance
	 */
	public ProtocolContext getContext() {
		return protocolContext;
	}

	@Override
	public void onChannelSubscription(final Channel channel) {
		FutureTask<Short> future = this.protocolContext.getShortDeviceId(getOwnUniqueDeviceID());
		short channelId = channel.getConfig().getId();
		try {
			Short ownShortDeviceId = future.get(1, TimeUnit.SECONDS);
			broadcastSubscription(ownShortDeviceId, channelId);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onChannelUnsubscription(final Channel channel) {
		FutureTask<Short> future = this.protocolContext.getShortDeviceId(getOwnUniqueDeviceID());
		try {
			Short ownShortDeviceId = future.get(1, TimeUnit.SECONDS);
			short channelId = channel.getConfig().getId();
			broadcastUnsubscription(ownShortDeviceId, channelId);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
	}

	@Override
	public ChannelFactory getChannelFactory() {
		return channelFactory;
	}

	// ================================================================================
	// Own Listener Methods
	// ================================================================================

	/**
	 * 
	 * @return the unique device id of this instance, usually corresponding to
	 *         the ip address of the network or something similar
	 */
	private String getOwnUniqueDeviceID() {
		return this.protocolContext.getOwnUniqueDeviceID();
	}

	/**
	 * processes retrieved device ids by putting them to the {@link ProtocolContext}
	 * @param message {@link BlaubotMessage} containing the device id info
	 */
	private void onNewProtocolDeviceID(BlaubotMessage message) {
		// aufbau: 2 byte protocolDeviceID, n byte receivedUniqueDeviceId
		byte[] payload = message.getPayload();
		ByteBuffer byteBuffer = ByteBuffer.wrap(payload);
		short shortDeviceId = byteBuffer.getShort();
		int length = payload.length - 2;
		byte[] dst = new byte[length];
		byteBuffer.get(dst);
		String uniqueDeviceId = new String(dst, BlaubotConstants.STRING_CHARSET);
		if (Log.logDebugMessages()) {
			Log.d(TAG, "Got new Device ID: " + shortDeviceId + " (uniqueDeviceId is " + uniqueDeviceId + ")");
		}
		this.protocolContext.putShortDeviceIdToUnqiueDeviceId(shortDeviceId, uniqueDeviceId);
	}

	/**
	 * processes retrieved channel ids by putting them to the {@link ProtocolContext}
	 * @param message {@link BlaubotMessage} containing the channel id info
	 */
	private void onNewChannelID(BlaubotMessage message) {
		// aufbau: 2 byte channelID, n byte receivedUniqueDeviceId
		byte[] payload = message.getPayload();
		ByteBuffer byteBuffer = ByteBuffer.wrap(payload);
		short channelId = byteBuffer.getShort();
		if (Log.logDebugMessages())
			Log.d(TAG, "Got new Channel ID: " + channelId);
		int length = payload.length - 2;
		byte[] dst = new byte[length];
		byteBuffer.get(dst);
		String receivedUniqueDeviceId = new String(dst, BlaubotConstants.STRING_CHARSET);
		this.protocolContext.putDeviceChannelIdIfAbsent(receivedUniqueDeviceId, channelId);
	}

	/**
	 * listens to incoming messages via the admin broadcast channel
	 */
	private IMessageListener adminBroadcastChannelListener = new IMessageListener() {

		@Override
		public void onMessage(BlaubotMessage message) {
			if (Log.logDebugMessages())
				Log.d(TAG, "Got adminBroadcastChannel message on device == " + getOwnUniqueDeviceID());

			MessageType type = message.getMessageType();
			if (MessageTypeFactory.isBroadcastDeviceIDMessageType(type)) {
				onNewProtocolDeviceID(message);
			} else if (MessageTypeFactory.isBroadcastChannelIDMessageType(type)) {
				onNewChannelID(message);
			}
		}
	};

	@Override
	public void activate() {
		this.protocolClient.activate();
		if (this.protocolMaster != null) {
			this.protocolMaster.activate();
		}
		// (re)activate all existing channels
		this.channelFactory.activateAllChannels();

		// finally subscribe to admin broadcast channel
		this.adminBroadcastChannel.subscribe(this.adminBroadcastChannelListener);
	}

	@Override
	public void deactivate() {
		this.protocolClient.deactivate();
		if (this.protocolMaster != null) {
			this.protocolMaster.deactivate();
		}
		// deactivate all existing channels
		this.channelFactory.deactivateAllChannels();
	}

	private final AtomicBoolean currentlyMaster = new AtomicBoolean(false);

	@Override
	public void setMaster(boolean isMaster) {
		boolean wasInitialized = protocolMaster != null;
		boolean hasStateChanged = currentlyMaster.get() != isMaster;
		hasStateChanged = wasInitialized ? hasStateChanged : true;
		if (!hasStateChanged) {
			return;
		}
		if (hasStateChanged && wasInitialized) {
			// only shutdown master if the state really changed and a
			// protocolMaster was intitially set
			this.protocolMaster.deactivate();
		}
		if (isMaster) {
			if (Log.logDebugMessages())
				Log.d(TAG, "I am Master now");
			this.protocolMaster = new ProtocolMaster(this);
			this.protocolMaster.activate();
			// connect master to own client
			BlaubotDeviceMock masterDevice = new BlaubotDeviceMock(getOwnUniqueDeviceID());
			final BlaubotConnectionQueueMock connectionToMaster = new BlaubotConnectionQueueMock(masterDevice);
			this.protocolClient.addConnection(connectionToMaster);

			// create "fake" connection to own client and add it
			BlaubotDeviceMock clientDevice = new BlaubotDeviceMock(getOwnUniqueDeviceID());
			BlaubotConnectionQueueMock connectionToClient = connectionToMaster.getOtherEndpointConnection(clientDevice);
			this.protocolMaster.addConnection(connectionToClient);
		} else {
			// remove old master if in client mode
			if (this.protocolMaster != null) {
				this.protocolMaster.deactivate();
				this.protocolMaster = null;
			}
		}
		currentlyMaster.set(isMaster);
	}

	@Override
	public void addConnection(IBlaubotConnection connection) {
		// add listener in case the connection gets closed
		connection.addConnectionListener(new IBlaubotConnectionListener() {

			@Override
			public void onConnectionClosed(IBlaubotConnection connection) {
				if (currentlyMaster.get()) {
					protocolMaster.closeConnection(connection);
				} else {
					protocolClient.closeConnection(connection);
				}
				connection.removeConnectionListener(this);
			}
		});

		final String uniqueDeviceID = connection.getRemoteDevice().getUniqueDeviceID();
		if (Log.logDebugMessages()) {
			Log.d(TAG, "Got new Connection from device == " + uniqueDeviceID);
		}

		if (currentlyMaster.get()) {
			protocolMaster.addConnection(connection);
		} else {
			protocolClient.addConnection(connection);
		}
		// notify listeners if app wasnt joined before
		if (!this.isJoined.getAndSet(true)) {
			for (JoinListener listener : this.joinListeners) {
				listener.onDisjointed();
			}
		}
	}

	// ================================================================================
	// Channel Subscriptions
	// ================================================================================

	private static final short SUBSCRIBE_TYPE = 0;
	private static final short UNSUBSCRIBE_TYPE = 1;

	/**
	 * listens for incoming (un)subscriptions
	 */
	private IMessageListener subscriptionChannelListener = new IMessageListener() {

		@Override
		public void onMessage(BlaubotMessage message) {
			// get channel ID, device ID and type (subscribe or unsubscribe?)
			// from payload
			byte[] payload = message.getPayload();
			ByteBuffer buffer = ByteBuffer.wrap(payload).order(StreamManager.MY_BYTE_ORDER);
			short channelID = buffer.getShort();
			short deviceID = buffer.getShort();
			short type = buffer.getShort();
			processSubscriptionMessage(deviceID, channelID, type);
		}

	};

	/**
	 * process incoming (un)subscription message
	 * 
	 * @param sourceProtocolDeviceID
	 * @param channelID
	 * @param type
	 */
	private void processSubscriptionMessage(short sourceProtocolDeviceID, short channelID, short type) {
		if (type == SUBSCRIBE_TYPE) {
			if (Log.logDebugMessages())
				Log.d(TAG, String.format("Got Subscription for channel ID == %d from device ID == %d (%s)", channelID, sourceProtocolDeviceID, this.protocolContext.getOwnUniqueDeviceID()));
			// add subscription info to protocol context
			this.protocolContext.addChannelSubscription(channelID, sourceProtocolDeviceID);
		} else if (type == UNSUBSCRIBE_TYPE) {
			if (Log.logDebugMessages())
				Log.d(TAG, String.format("Got Unsubscription for channel ID == %d from device ID == %d", channelID, sourceProtocolDeviceID));
			// remove subscription from protocol context
			this.protocolContext.removeChannelSubscription(channelID, sourceProtocolDeviceID);
		} else {
			throw new RuntimeException("type unknown for (un)subscription");
		}
	}

	/**
	 * will send the subscription info via the channelSubscriptionChannel
	 * 
	 * @param channel
	 */
	public void broadcastSubscription(short shortDeviceID, short channelID) {
		sendSubscriptionInfo(shortDeviceID, channelID, SUBSCRIBE_TYPE);
	}

	/**
	 * will send the unsubscription info via the channelSubscriptionChannel
	 * 
	 * @param channel
	 */
	public void broadcastUnsubscription(short shortDeviceID, short channelID) {
		sendSubscriptionInfo(shortDeviceID, channelID, UNSUBSCRIBE_TYPE);
	}

	/**
	 * sends the given (un)subscription info for the given channel via the
	 * channelSubscriptionChannel
	 * 
	 * @param channel
	 * @param type
	 */
	private void sendSubscriptionInfo(final short shortDeviceID, final short channelID, final short type) {
		final int payloadSize = 6;
		ByteBuffer buffer = ByteBuffer.allocate(payloadSize).order(StreamManager.MY_BYTE_ORDER);
		buffer.putShort(channelID);
		buffer.putShort(shortDeviceID);
		buffer.putShort(type);

		byte[] bytes = new byte[payloadSize];
		buffer.clear();
		buffer.get(bytes);

		channelSubscriptionChannel.post(bytes);
	}

	public void addJoinListener(JoinListener listener) {
		this.joinListeners.add(listener);
	}

	public void removeJoinListener(JoinListener listener) {
		this.joinListeners.remove(listener);
	}

	@Override
	public void reset() {
		// notify all listeners and allow them to tidy up
		for (JoinListener listener : this.joinListeners) {
			listener.onDisjointed();
		}
		// afterwards deactivate all channels except the admin broadcast channel
		// and reset the context
		this.channelFactory.deactivateAllChannels();
		this.protocolContext.reset();
		this.isJoined.set(false);
	}

}
