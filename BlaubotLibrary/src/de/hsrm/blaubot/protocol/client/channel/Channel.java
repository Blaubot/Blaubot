package de.hsrm.blaubot.protocol.client.channel;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.protocol.IMessageListener;
import de.hsrm.blaubot.protocol.ProtocolEnums.MessageRateType;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.client.ProtocolClient;
import de.hsrm.blaubot.protocol.client.channel.messagepicker.FixedAggregatePicker;
import de.hsrm.blaubot.protocol.client.channel.messagepicker.FixedDiscardNewPicker;
import de.hsrm.blaubot.protocol.client.channel.messagepicker.FixedDiscardOldPicker;
import de.hsrm.blaubot.protocol.client.channel.messagepicker.MessagePickerStrategy;
import de.hsrm.blaubot.protocol.client.channel.messagepicker.NoLimitPicker;
import de.hsrm.blaubot.util.Log;

public class Channel implements IChannel {

	protected static final String TAG = "Channel";
	private final int QUEUE_TIMEOUT = 200;

	private ChannelConfig config;
	protected CopyOnWriteArrayList<IMessageListener> listeners;
	protected LinkedBlockingDeque<BlaubotMessage> queuedMessages;
	private MessageRateType messageRateType;
	private int timeout;
	private ExecutorService executor;
	private boolean isActivated;
	private Object activationLock;
	private MessagePickerStrategy messagePickerStrategy;
	private ProtocolClient protocolClient;
	private ProtocolManager protocolManager;

	protected Channel(ProtocolManager protocolManager, ProtocolClient protocolClient, ChannelConfig config) {
		this.protocolManager = protocolManager;
		this.config = config;
		this.protocolClient = protocolClient;

		this.protocolClient.addListeningChannel(this);
		// create message type depending on config
		this.listeners = new CopyOnWriteArrayList<IMessageListener>();
		this.queuedMessages = new LinkedBlockingDeque<BlaubotMessage>();

		int messageRate = config.getMessageRate();
		this.messageRateType = config.getMessageRateType();
		this.timeout = 0;
		if (messageRate > 0) {
			this.timeout = 1000 / messageRate;
		}
		this.isActivated = false;
		this.activationLock = new Object();
	}

	/**
	 * starts the {@link ExecutorService} which sends the messages buffered in
	 * the queue if not already started
	 */
	protected void activate() {
		// ensure just one activation happens
		synchronized (activationLock) {
			if (isActivated) {
				return;
			}
			isActivated = true;

			// shut down if already existing
			if (executor != null) {
				if (Log.logDebugMessages()) {
					Log.d(TAG, "shut down executor");
				}
				executor.shutdownNow();
			}

			// choose proper message picking strategy depending on message rate
			// type
			switch (this.messageRateType) {
			case NO_LIMIT:
				this.messagePickerStrategy = new NoLimitPicker();
				break;
			case FIXED_DISCARD_OLD:
				this.messagePickerStrategy = new FixedDiscardOldPicker();
				break;
			case FIXED_DISCARD_NEW:
				this.messagePickerStrategy = new FixedDiscardNewPicker();
				break;
			case FIXED_AGGREGATE:
				// TODO What does aggregate mean?, for now do NO_LIMIT
				this.messagePickerStrategy = new FixedAggregatePicker();
				break;
			}

			// choose proper thread executor depending on message rate type
			switch (this.messageRateType) {
			case NO_LIMIT:
				executor = Executors.newSingleThreadExecutor();
				executor.submit(sendMessages);
				break;
			default:
				ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
				executor = scheduledExecutor;
				scheduledExecutor.scheduleAtFixedRate(sendOneMessage, 0, timeout, TimeUnit.MILLISECONDS);
			}
		}
		if (Log.logDebugMessages()) {
			Log.d(TAG, String.format("Channel#%d activated", this.config.getId()));
		}
	}

	/**
	 * stops running {@link ExecutorService}
	 */
	public void deactivate() {
		// ensure just one deactivate
		synchronized (activationLock) {
			if (!isActivated) {
				return;
			}
			isActivated = false;
			executor.shutdown(); // dont ignore remaining messages
			executor = null;
			if (Log.logDebugMessages())
				Log.d(TAG, "deactivated channel with id == " + config.getId());
		}
	}

	/**
	 * sends the given payload via this {@link Channel} to all current
	 * subscribers
	 */
	public void post(byte[] payload) {
		BlaubotMessage message = new BlaubotMessage();
		message.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
		message.setPayload(payload);
		post(message);
	}

	/**
	 * Post message to all subscribed devices
	 * 
	 * @param message
	 * @param sentMessageCallback
	 */
	public void post(BlaubotMessage message) {
		short channelId = config.getId();
		synchronized (activationLock) {
			if (!isActivated) {
				if (channelId < 0) { // may happen
					Log.w(TAG, "Post on deactivated admin channel " + channelId);
				} else {
					Log.e(TAG, "Post on deactivated user channel" + channelId);
				}
			}
		}
		// create correspondig message type
		MessageType messageType = message.getMessageType() != null ? message.getMessageType() : new MessageType();
		if (message.getPayload() != null) {
			messageType.Payload();
		} else {
			messageType.NoPayload();
		}
		if (channelId < 0) {
			messageType.Admin();
		} else {
			messageType.Application();
		}
		message.setMessageType(messageType);

		message.setChannelId(channelId);

		// set priority of this channel to message in order to allow
		// corresponding ordering in outgoing message (priority) queue
		message.setPriority(this.config.getPriority());
		this.queuedMessages.add(message);
	}

	@Override
	public ChannelConfig getConfig() {
		return config;
	}

	@Override
	public void subscribe(IMessageListener listener) {
		this.protocolManager.onChannelSubscription(this);
		addListener(listener);
	}

	@Override
	public void unsubscribe(IMessageListener listener) {
		this.protocolManager.onChannelUnsubscription(this);
		removeListener(listener);
	}

	private final ExecutorService messageExecutor = Executors.newFixedThreadPool(5);

	@Override
	public void onMessage(final BlaubotMessage message) {
		for (final IMessageListener listener : listeners) {
			messageExecutor.execute(new Runnable() {

				@Override
				public void run() {
					listener.onMessage(message);
				}
			});
		}
	}

	private Runnable sendOneMessage = new Runnable() {
		@Override
		public void run() {
			try {
				BlaubotMessage message = messagePickerStrategy.pickMessage(queuedMessages, QUEUE_TIMEOUT);
				if (message == null) {
					return;
				}
				sendQueuedMessage(message);
			} catch (InterruptedException e) {
				if (Log.logDebugMessages())
					Log.d(TAG, "Consumer Thread has been interrupted, couldn't send message (msg is lost), channel == " + getConfig().getId());
			}
		}
	};

	/**
	 * sends the given queued message to all subscribers
	 * 
	 * @param queuedMessage
	 */
	protected void sendQueuedMessage(BlaubotMessage message) {
		this.protocolClient.sendMessage(message);
	}

	private Runnable sendMessages = new Runnable() {
		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				sendOneMessage.run();
			}
		}
	};

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((config == null) ? 0 : config.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Channel))
			return false;
		Channel other = (Channel) obj;
		if (config == null) {
			if (other.config != null)
				return false;
		} else if (!config.equals(other.config))
			return false;
		return true;
	}

	@Override
	public void addListener(IMessageListener listener) {
		// only add once
		this.listeners.addIfAbsent(listener);
	}

	@Override
	public void removeListener(IMessageListener listener) {
		this.listeners.remove(listener);
	}

}
