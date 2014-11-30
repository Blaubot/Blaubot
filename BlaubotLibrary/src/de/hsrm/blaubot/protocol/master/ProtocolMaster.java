package de.hsrm.blaubot.protocol.master;

import java.nio.ByteBuffer;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.BlaubotMessagePriorityComparator;
import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.message.MessageTypeFactory;
import de.hsrm.blaubot.protocol.IStreamListener;
import de.hsrm.blaubot.protocol.ProtocolContext;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.QueueConsumer;
import de.hsrm.blaubot.protocol.StreamManager;
import de.hsrm.blaubot.protocol.client.channel.BroadcastChannel;
import de.hsrm.blaubot.protocol.client.channel.ChannelFactory;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolActivateEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolAddConnectionEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolConnectionClosedEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolDeactivateEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEventDispatcher;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEventListener;
import de.hsrm.blaubot.protocol.handshake.IProtocolHandshakeListener;
import de.hsrm.blaubot.protocol.handshake.ProtocolHandshakeMasterTask;
import de.hsrm.blaubot.util.Log;

/**
 * Used for multiplexing incoming messages
 * 
 * @author manuelpras
 * 
 */
public class ProtocolMaster implements ProtocolEventListener, IStreamListener {

	private static final String TAG = "ProtocolMaster";
	public static final int HANDSHAKE_CALLBACK_TIMEOUT = 1000;
	/** contains the mapping of connection -> queue consumer */
	private final ConcurrentHashMap<IBlaubotConnection, QueueConsumer> connectionQueueConsumerMapping;
	/** contains the mapping of connection -> stream manager */
	private final ConcurrentHashMap<IBlaubotConnection, StreamManager> connectionStreamManagerMapping;
	private short freeDeviceID = 1;
	private ChannelFactory channelFactory;
	private BroadcastChannel adminBroadcastChannel;
	private ProtocolContext protocolContext;
	/**
	 * Lock for the QueueConsumer and StreamManager CRUD methods.
	 */
	private final Object targetDeviceLock = new Object();
	private final ConcurrentHashMap<String, PriorityBlockingQueue<BlaubotMessage>> messageQueueMap;
	private final ProtocolEventDispatcher eventDispatcher;
	private final LinkedBlockingQueue<ProtocolEvent> eventQueue;
	private final ProtocolManager protocolManager;

	public ProtocolMaster(ProtocolManager protocolManager) {
		// create and activate event dispatcher right here before anything else
		// happens (otherwise we would miss events like start() or stop())
		this.eventQueue = new LinkedBlockingQueue<ProtocolEvent>();
		this.eventDispatcher = new ProtocolEventDispatcher(eventQueue);
		this.eventDispatcher.addListener(this);
		this.eventDispatcher.activate();

		this.protocolManager = protocolManager;
		this.protocolContext = protocolManager.getContext();

		this.channelFactory = protocolContext.getChannelFactory();
		this.adminBroadcastChannel = this.channelFactory.getAdminBroadcastChannel();

		this.messageQueueMap = new ConcurrentHashMap<String, PriorityBlockingQueue<BlaubotMessage>>();
		this.connectionQueueConsumerMapping = new ConcurrentHashMap<IBlaubotConnection, QueueConsumer>();
		this.connectionStreamManagerMapping = new ConcurrentHashMap<IBlaubotConnection, StreamManager>();
	}

	/**
	 * forwards the given message to all clients in the blaubot network
	 * 
	 * @param message
	 */
	public void multiplexBroadcastMessage(BlaubotMessage message) {
		for (IBlaubotConnection connection : this.connectionQueueConsumerMapping.keySet()) {
			String targetDeviceUniqueID = connection.getRemoteDevice().getUniqueDeviceID();
			sendToDevice(targetDeviceUniqueID, message);
		}
	}

	/**
	 * forwards the given message to the client specified by the short device id
	 * in the header of the given message
	 * 
	 * @param message
	 */
	public void multiplexMessage(final BlaubotMessage message) {
		short channelId = message.getChannelId();
		Set<Short> subscriptions = this.protocolContext.getChannelSubscriptions(channelId);
		if (subscriptions == null || subscriptions.isEmpty()) {
			if (Log.logWarningMessages())
				Log.w(TAG, "No subscription for multiplexing on channel ID == " + channelId);
			return;
		}
		Short shortDeviceId = message.getSourceDeviceId();
		if (shortDeviceId == null) {
			throw new RuntimeException("short device ID was null!");
		}
		// forward message for every subscripted target device
		for (short targetShortDeviceId : subscriptions) {
			FutureTask<String> future = this.protocolContext.getUniqueDeviceId(targetShortDeviceId);
			try {
				String targetDeviceUniqueID = future.get(1000, TimeUnit.MILLISECONDS);
				sendToDevice(targetDeviceUniqueID, message);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (TimeoutException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * send the message to the device corresponding to the target's device
	 * unique id
	 * 
	 * @param targetDeviceUniqueID
	 * @param message
	 */
	private void sendToDevice(String targetDeviceUniqueID, BlaubotMessage message) {
		// update type in order to avoid loops
		MessageType type = message.getMessageType();
		message.setMessageType(type.NoMultiplex());
		PriorityBlockingQueue<BlaubotMessage> queuedMessages = getMessageQueue(targetDeviceUniqueID);
		// add msg to queue
		queuedMessages.add(message);
	}

	public void activate() {
		addProtocolEvent(new ProtocolActivateEvent());
	}

	private void doActivate() {
		synchronized (targetDeviceLock) {
			for (QueueConsumer queueConsumer : this.connectionQueueConsumerMapping.values()) {
				queueConsumer.activate();
			}
			this.eventDispatcher.activate();
		}
	}

	public void deactivate() {
		addProtocolEvent(new ProtocolDeactivateEvent());
	}

	private void doDeactivate() {
		synchronized (targetDeviceLock) {
			// release resource if not yet done
			for (IBlaubotConnection connection : this.connectionQueueConsumerMapping.keySet()) {
				removeConnection(connection);
			}
		}
		this.eventDispatcher.deactivate();
	}

	public void addConnection(IBlaubotConnection connection) {
		addProtocolEvent(new ProtocolAddConnectionEvent(connection));
	}

	private void doAddConnection(IBlaubotConnection connection) {
		String uniqueDeviceID = connection.getRemoteDevice().getUniqueDeviceID();
		if (uniqueDeviceID == null) {
			throw new NullPointerException("uniqueDeviceId was null but should not be null.");
		}
		synchronized (targetDeviceLock) {
			// create mapping if not yet existing
			PriorityBlockingQueue<BlaubotMessage> queuedMessages = getMessageQueue(uniqueDeviceID);
			QueueConsumer queueConsumer = new QueueConsumer(connection, this.protocolContext, queuedMessages);
			QueueConsumer oldQueueConsumer = this.connectionQueueConsumerMapping.putIfAbsent(connection, queueConsumer);
			if (oldQueueConsumer != null) {
				throw new RuntimeException("There was already a queueConsumer for this connection");
			}
			queueConsumer.activate();

			final StreamManager streamManager = new StreamManager(connection);
			StreamManager oldStreamManager = this.connectionStreamManagerMapping.putIfAbsent(connection, streamManager);
			if (oldStreamManager != null) {
				throw new RuntimeException("There was already a stream manager for this connection");
			}
			streamManager.addListener(this);
			streamManager.start();

			if (Log.logDebugMessages())
				Log.d(TAG, "Got " + this.connectionQueueConsumerMapping.size() + ". Connection");
		}
	}

	/**
	 * creates queue for given unique device id if not yet existing. returns
	 * either the existing or the new created queue
	 * 
	 * @param uniqueDeviceID
	 * @return
	 */
	private PriorityBlockingQueue<BlaubotMessage> getMessageQueue(String uniqueDeviceID) {
		PriorityBlockingQueue<BlaubotMessage> queue = new PriorityBlockingQueue<BlaubotMessage>(100, new BlaubotMessagePriorityComparator());
		this.messageQueueMap.putIfAbsent(uniqueDeviceID, queue);
		// get actual msg queue
		PriorityBlockingQueue<BlaubotMessage> queuedMessages = this.messageQueueMap.get(uniqueDeviceID);
		return queuedMessages;
	}

	/**
	 * stops the queue consumer and stream manager for the given connection and
	 * disconnects the corresponding connection to the remote device
	 * 
	 * @param connection
	 */
	private void removeConnection(IBlaubotConnection connection) {
		synchronized (targetDeviceLock) {
			// remove from map
			// note that the QueueConsumer could
			// already be removed by deactivate/activate
			QueueConsumer qc = this.connectionQueueConsumerMapping.remove(connection);
			if (qc != null) {
				qc.deactivate();
			}
			StreamManager sm = this.connectionStreamManagerMapping.remove(connection);
			if (sm != null) {
				sm.cancel();
			}
			connection.disconnect();
		}
		if (Log.logDebugMessages())
			Log.d(TAG, "removed connection to: " + connection.getRemoteDevice().getUniqueDeviceID());
	}

	private final Object CREATE_DEVICE_ID_LOCK = new Object();

	/**
	 * Creates a protocol device ID for the given unique device ID if this
	 * uniqueDevice id was formerly unknown. Otherwise returns the already
	 * assigned short device id for this uniqueDeviceId.
	 * 
	 * @param uniqueDeviceID
	 * @return corresponding protocol device id
	 * @throws NullPointerException
	 *             if uniqueDeviceID is null
	 */
	private Short createOrGetDeviceID(String uniqueDeviceID) {
		if (uniqueDeviceID == null) {
			throw new NullPointerException();
		}
		// create device id, start with 1
		synchronized (CREATE_DEVICE_ID_LOCK) {
			// create entry if not already existing
			short deviceID = freeDeviceID;
			boolean mappingAdded = this.protocolContext.putShortDeviceIdToUnqiueDeviceId(deviceID, uniqueDeviceID);

			if (mappingAdded) {
				freeDeviceID += 1;
				if (Log.logDebugMessages()) {
					Log.d(TAG, String.format("Created device id %d for unique device id %s", deviceID, uniqueDeviceID));
				}
				return deviceID;
			}

			FutureTask<Short> future = this.protocolContext.getShortDeviceId(uniqueDeviceID);
			try {
				return future.get(1000, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				e.printStackTrace();
				if (Log.logErrorMessages())
					Log.e(TAG, "catched exception in createOrGetDeviceID() : " + e.toString());
			}
			return null;
		}
	}

	/**
	 * broadcast all existing unique to short device ID mappings
	 */
	private void broadcastDeviceIDs() {
		final Set<Short> shortDeviceIds = this.protocolContext.getShortDeviceIds();
		for (final Short deviceID : shortDeviceIds) {
			FutureTask<String> future = this.protocolContext.getUniqueDeviceId(deviceID);
			try {
				String uniqueDeviceId = future.get(1000, TimeUnit.MILLISECONDS);
				// put device id and device's unique id in payload
				int capacity = 2 + uniqueDeviceId.length();
				byte[] payload = new byte[capacity];
				ByteBuffer buffer = ByteBuffer.allocate(capacity).order(StreamManager.MY_BYTE_ORDER);
				buffer.putShort(deviceID);
				buffer.put(uniqueDeviceId.getBytes(BlaubotConstants.STRING_CHARSET));
				buffer.clear();
				buffer.get(payload);

				MessageType messageType = MessageTypeFactory.createBroadcastDeviceIDMessageType();
				BlaubotMessage message = new BlaubotMessage();
				message.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
				message.setMessageType(messageType);
				message.setPayload(payload);

				adminBroadcastChannel.post(message);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (TimeoutException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * broadcast all existing short device ID to channel ID mappings
	 */
	private void broadcastChannelIDs() {
		Set<Entry<String, Short>> deviceChannelIDsEntrySet = this.protocolContext.getDeviceChannelIDsEntrySet();
		if (Log.logDebugMessages())
			Log.d(TAG, String.format("Broadcasting %d channel IDs", deviceChannelIDsEntrySet.size()));
		for (Entry<String, Short> entry : deviceChannelIDsEntrySet) {
			String uniqueDeviceID = entry.getKey();
			short channelID = entry.getValue();
			// put channel id and device's unique id in payload
			int capacity = 2 + uniqueDeviceID.length();
			byte[] payload = new byte[capacity];
			ByteBuffer buffer = ByteBuffer.allocate(capacity).order(StreamManager.MY_BYTE_ORDER).putShort(channelID).put(uniqueDeviceID.getBytes(BlaubotConstants.STRING_CHARSET));
			buffer.clear();
			buffer.get(payload);

			MessageType messageType = MessageTypeFactory.createBroadcastChannelIDMessageType();
			BlaubotMessage message = new BlaubotMessage();
			message.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
			message.setMessageType(messageType);
			message.setPayload(payload);

			adminBroadcastChannel.post(message);
		}
	}

	public ProtocolContext getProtocolContext() {
		return this.protocolContext;
	}

	private void broadcastProtocolContext(ProtocolManager protocolManager) {
		if (Log.logDebugMessages())
			Log.d(TAG, "broadcasting protocol context");
		broadcastDeviceIDs();
		broadcastChannelIDs();
		for (Entry<Short, CopyOnWriteArraySet<Short>> entry : this.protocolContext.getChannelSubscriptionsEntrySet()) {
			Short channelID = entry.getKey();
			for (short shortDeviceID : entry.getValue()) {
				protocolManager.broadcastSubscription(shortDeviceID, channelID);
			}
		}
	}

	private void doHandshake(final IBlaubotConnection connection) {
		IProtocolHandshakeListener handshakeListener = new IProtocolHandshakeListener() {

			@Override
			public void onSuccess(short shortDeviceId) {
				// add connection to master for sending bytes
				doAddConnection(connection);
				// broadcast all important info from protocol context
				broadcastProtocolContext(protocolManager);
			}

			@Override
			public void onFailure(IBlaubotConnection connection) {
				if (Log.logErrorMessages()) {
					Log.e(TAG, "The ProtocolHandshake failed due to a timeout. Closing the connection.");
				}
				connection.disconnect();
			}
		};

		String uniqueDeviceID = connection.getRemoteDevice().getUniqueDeviceID();
		short shortDeviceId = createOrGetDeviceID(uniqueDeviceID);
		ProtocolHandshakeMasterTask handshakeTask = new ProtocolHandshakeMasterTask(shortDeviceId, connection, handshakeListener);
		handshakeTask.execute();
		if (Log.logDebugMessages()) {
			Log.d(TAG, "executed handshake task on master");
		}
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
		case SET_MASTER:
			break;
		case ACTIVATE:
			onActivateEvent((ProtocolActivateEvent) event);
			break;
		case DEACTIVATE:
			onDeactivateEvent((ProtocolDeactivateEvent) event);
			break;
		default:
			throw new RuntimeException("Type unknown");
		}
	}

	private void onAddConnectionEvent(ProtocolAddConnectionEvent event) {
		doHandshake(event.getConnection());
	}

	private void onActivateEvent(ProtocolActivateEvent event) {
		doActivate();
	}

	private void onDeactivateEvent(ProtocolDeactivateEvent event) {
		doDeactivate();
	}

	private void onConnectionClosedEvent(ProtocolConnectionClosedEvent event) {
		IBlaubotConnection connection = event.getConnection();
		removeConnection(connection);
	}

	/**
	 * adds the given {@link ProtocolEvent} to the event queue which will be
	 * consumed by the {@link ProtocolEventDispatcher}
	 * 
	 * @param event
	 */
	private void addProtocolEvent(ProtocolEvent event) {
		this.eventQueue.add(event);
	}

	private final ExecutorService messageExecutor = Executors.newFixedThreadPool(5);

	@Override
	public void onMessage(final BlaubotMessage message) {
		messageExecutor.execute(new Runnable() {

			@Override
			public void run() {
				MessageType type = message.getMessageType();
				if (type.isBroadcastMessage()) {
					multiplexBroadcastMessage(message);
				} else {
					multiplexMessage(message);
				}
			}
		});
	}

	@Override
	public void onWrongProtocolVersion(short expectedVersion, short actualVersion) {
		if (Log.logWarningMessages()) {
			Log.w(TAG, "wrong protocol version");
		}
	}

	public void closeConnection(IBlaubotConnection connection) {
		addProtocolEvent(new ProtocolConnectionClosedEvent(connection));
	}

}
