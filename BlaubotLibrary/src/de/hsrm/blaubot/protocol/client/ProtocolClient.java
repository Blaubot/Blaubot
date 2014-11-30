package de.hsrm.blaubot.protocol.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.BlaubotMessagePriorityComparator;
import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.protocol.IMessageListener;
import de.hsrm.blaubot.protocol.IProtocolLifecycle;
import de.hsrm.blaubot.protocol.IStreamListener;
import de.hsrm.blaubot.protocol.ProtocolContext;
import de.hsrm.blaubot.protocol.QueueConsumer;
import de.hsrm.blaubot.protocol.StreamManager;
import de.hsrm.blaubot.protocol.client.channel.Channel;
import de.hsrm.blaubot.protocol.client.channel.ChannelFactory;
import de.hsrm.blaubot.protocol.client.channel.IChannel;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolActivateEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolAddConnectionEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolConnectionClosedEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolDeactivateEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEventDispatcher;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEventListener;
import de.hsrm.blaubot.protocol.handshake.IProtocolHandshakeListener;
import de.hsrm.blaubot.protocol.handshake.ProtocolHandshakeClientTask;
import de.hsrm.blaubot.util.Log;

/**
 * Sends buffered messages to the ProtocolMaster which then multiplexes the
 * messages to the desired target clients
 * 
 * @author manuelpras
 * 
 */
public class ProtocolClient implements IProtocolLifecycle, ProtocolEventListener, IStreamListener {

	protected static final String TAG = "ProtocolClient";
	private volatile IBlaubotConnection connection;
	private QueueConsumer queueConsumer;
	private final ProtocolContext protocolContext;
	private final PriorityBlockingQueue<BlaubotMessage> queuedMessages;
	private final ProtocolEventDispatcher eventDispatcher;
	private final LinkedBlockingQueue<ProtocolEvent> eventQueue;
	private final ConcurrentHashMap<Short, IChannel> listeningChannels;

	public ProtocolClient(ProtocolContext protocolContext) {
		// create and activate event dispatcher right here before anything else
		// happens (otherwise we would miss events like start() or stop())
		this.eventQueue = new LinkedBlockingQueue<ProtocolEvent>();
		this.eventDispatcher = new ProtocolEventDispatcher(eventQueue);
		this.eventDispatcher.addListener(this);
		this.eventDispatcher.activate();

		// init all lists and maps
		this.listeningChannels = new ConcurrentHashMap<Short, IChannel>();

		this.protocolContext = protocolContext;
		// add comparator for message queue
		this.queuedMessages = new PriorityBlockingQueue<BlaubotMessage>(50, new BlaubotMessagePriorityComparator());
	}

	/**
	 * adds the given message to the main message queue. application layer
	 * usually uses the send- or broadcast-methods for sending purposes
	 * 
	 * @param message
	 * @param channel_ID
	 * @param messageSentCallback
	 */
	public void queueMessage(BlaubotMessage message) {
		// add multiplex flag for master
		MessageType messageType = message.getMessageType();
		message.setMessageType(messageType.Multiplex());
		String ownUniqueDeviceID = this.protocolContext.getOwnUniqueDeviceID();
		try {
			// device id hasnt been set yet: get it from context
			short ownDeviceId = this.protocolContext.getShortDeviceId(ownUniqueDeviceID).get(1000, TimeUnit.MILLISECONDS);
			message.setSourceDeviceId(ownDeviceId);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
		// add message to queue
		this.queuedMessages.add(message);
	}

	/**
	 * broadcasts the given message to all clients (including this client
	 * itself) in the network
	 * 
	 * @param message
	 */
	public void broadcastMessage(BlaubotMessage message) {
		// add broadcast type
		MessageType type = message.getMessageType();
		message.setMessageType(type.Broadcast());
		queueMessage(message);
	}

	/**
	 * sends the given message to the client specified by the device id in the
	 * message header
	 * 
	 * @param message
	 */
	public void sendMessage(BlaubotMessage message) {
		// add nonbroadcast type
		MessageType type = message.getMessageType();
		message.setMessageType(type.NoBroadcast());
		queueMessage(message);
	}

	@Override
	public void activate() {
		addProtocolEvent(new ProtocolActivateEvent());
	}

	private void doActivate() {
		if (this.queueConsumer != null) {
			this.queueConsumer.activate();
		}
	}

	@Override
	public void deactivate() {
		addProtocolEvent(new ProtocolDeactivateEvent());
	}

	private void doDeactivate() {
		if (this.queueConsumer != null) {
			this.queueConsumer.deactivate();
		}
	}

	/**
	 * executes the handshake with the given {@link IBlaubotConnection} in order
	 * to retrieve the own device and channel id. after the handshake is done,
	 * the method will start the {@link QueueConsumer} and subscribes to its own
	 * device channel (used by the admin layer)
	 * 
	 * @param connection
	 */
	private void doHandshake(final IBlaubotConnection connection) {
		/*
		 * process: do handshake with master -> as soon as short device id comes
		 * in put it to bidirectional mapping in context -> create corresponding
		 * channel id -> create reading stream manager for connection
		 */
		IProtocolHandshakeListener handshakeListener = new IProtocolHandshakeListener() {

			@Override
			public void onSuccess(short ownShortDeviceId) {
				ProtocolClient protocolClient = ProtocolClient.this;
				String ownUniqueDeviceID = protocolClient.protocolContext.getOwnUniqueDeviceID();
				// put received id in mappings
				protocolClient.protocolContext.putShortDeviceIdToUnqiueDeviceId(ownShortDeviceId, ownUniqueDeviceID);
				protocolClient.protocolContext.createChannelId(ownUniqueDeviceID, ownShortDeviceId);

				// create stream manager in order to receive messages from
				// master
				StreamManager streamManager = new StreamManager(connection);
				streamManager.addListener(protocolClient);
				streamManager.start();

				// remove current queue consumer before adding new connection
				protocolClient.doDeactivate();
				// then update reference and start new one
				protocolClient.connection = connection;
				protocolClient.queueConsumer = new QueueConsumer(connection, protocolClient.protocolContext, protocolClient.queuedMessages);
				protocolClient.doActivate();

				subscribeToOwnDeviceChannel();
			}

			@Override
			public void onFailure(IBlaubotConnection connection) {
				if (Log.logErrorMessages()) {
					Log.e(TAG, "The ProtocolHandshake failed due to a timeout. Closing the connection.");
				}
				connection.disconnect();
			}
		};
		ProtocolHandshakeClientTask handshakeTask = new ProtocolHandshakeClientTask(connection, handshakeListener);
		handshakeTask.execute();
		if (Log.logDebugMessages()) {
			Log.d(TAG, "executed handshake task on client == " + this.protocolContext.getOwnUniqueDeviceID());
		}
	}

	/**
	 * subscribe to to own device channel for admin message transfer (e.g. used
	 * by connection layer in order to send messages to specific devices)
	 */
	private void subscribeToOwnDeviceChannel() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				IMessageListener listener = new IMessageListener() {

					@Override
					public void onMessage(BlaubotMessage message) {
						if (Log.logDebugMessages()) {
							String ownUniqueDeviceID = protocolContext.getOwnUniqueDeviceID();
							Log.d(TAG, String.format("Got Admin Device Channel message on device == %s", ownUniqueDeviceID));
						}
					}
				};

				subscribeToOwnAdminDeviceChannel(listener);
			}
		}).start();
	}

	/**
	 * subscribes to own admin device channel as soon as the corresponding
	 * channel ID was received from the master
	 * 
	 * @param listener
	 */
	private void subscribeToOwnAdminDeviceChannel(final IMessageListener listener) {
		ChannelFactory channelFactory = this.protocolContext.getChannelFactory();
		Channel channel = channelFactory.getOwnDeviceAdminChannel();
		// add subscription as soon as the channel is available
		channel.subscribe(listener);
		if (Log.logDebugMessages())
			Log.d(TAG, "Subscribed to own device admin channel");
	}

	@Override
	public void onProtocolEvent(ProtocolEvent event) {
		if (Log.logDebugMessages()) {
			Log.d(TAG, "got new protocol event: " + event.getEventType().toString());
		}
		switch (event.getEventType()) {
		case ADD_CONNECTION:
			onAddConnectionEvent((ProtocolAddConnectionEvent) event);
			break;
		case CONNECTION_CLOSED:
			onConnectionClosedEvent((ProtocolConnectionClosedEvent) event);
			break;
		case ACTIVATE:
			onActivateEvent((ProtocolActivateEvent) event);
			break;
		case DEACTIVATE:
			onDeactivateEvent((ProtocolDeactivateEvent) event);
			break;
		case SET_MASTER:
			break;
		}
	}

	private void onDeactivateEvent(ProtocolDeactivateEvent event) {
		doDeactivate();
	}

	private void onActivateEvent(ProtocolActivateEvent event) {
		doActivate();
	}

	private void onConnectionClosedEvent(ProtocolConnectionClosedEvent event) {
		this.queueConsumer.deactivate();
	}

	private void onAddConnectionEvent(ProtocolAddConnectionEvent event) {
		IBlaubotConnection connection = event.getConnection();
		if (connection.equals(this.connection)) {
			throw new RuntimeException("connection already existing for device ID == " + connection.getRemoteDevice().getUniqueDeviceID());
		}

		doHandshake(connection);
	}

	private void addProtocolEvent(ProtocolEvent event) {
		this.eventQueue.add(event);
	}

	private final ExecutorService messageExecutor = Executors.newFixedThreadPool(5);

	@Override
	public void onMessage(final BlaubotMessage message) {
		this.messageExecutor.execute(new Runnable() {

			@Override
			public void run() {
				notifyChannelListeners(message);
			}
		});
	}

	/**
	 * notify listeners of the given channel (ID) if any
	 * 
	 * @param message
	 */
	private void notifyChannelListeners(final BlaubotMessage message) {
		final IChannel channel = this.listeningChannels.get(message.getChannelId());
		// ignore if no local channels registered listeners for this ID
		if (channel == null) {
			return;
		}
		channel.onMessage(message);
	}

	@Override
	public void onWrongProtocolVersion(short expectedVersion, short actualVersion) {
		if (Log.logWarningMessages()) {
			Log.w(TAG, "wrong protocol version");
		}
	}

	public void addListeningChannel(IChannel channel) {
		short id = channel.getConfig().getId();
		this.listeningChannels.putIfAbsent(id, channel);
	}

	public void removeListeningChannel(IChannel channel) {
		short id = channel.getConfig().getId();
		this.listeningChannels.remove(id);
	}

	/**
	 * adds the given {@link IBlaubotConnection} to this {@link ProtocolClient}
	 * instance. will be used for sending and retrieving {@link BlaubotMessage}s
	 * 
	 * @param connection
	 */
	public void addConnection(IBlaubotConnection connection) {
		addProtocolEvent(new ProtocolAddConnectionEvent(connection));
	}

	/**
	 * closes the given {@link IBlaubotConnection} and releases all resources
	 * which rely on this connection
	 * 
	 * @param connection
	 */
	public void closeConnection(IBlaubotConnection connection) {
		new ProtocolConnectionClosedEvent(connection);
	}

}
