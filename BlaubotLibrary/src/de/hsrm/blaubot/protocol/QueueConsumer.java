package de.hsrm.blaubot.protocol;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.util.Log;

/**
 * Thread which consumes the QueuedMessages and sends them via the given
 * {@link IBlaubotConnection}
 * 
 * @author manuelpras
 * 
 */
public class QueueConsumer implements IProtocolLifecycle {

	final static int MESSAGEQUEUE_TIMEOUT = 500;

	private static final String TAG = "QueueConsumer";
	private final PriorityBlockingQueue<BlaubotMessage> queuedMessages;
	private volatile IBlaubotConnection connection;
	private ExecutorService executorPool;
	private ConsumerRunnable consumerRunnable = null;

	/**
	 * @param connection
	 *            connection which shall be used for sending the messages from
	 *            the given queue
	 * @param protocolContext
	 *            context of the protocol layer
	 * @param queuedMessages
	 *            queued {@link BlaubotMessage}s which shall be sent by this
	 *            instance via the given {@link IBlaubotConnection}
	 */
	public QueueConsumer(IBlaubotConnection connection, ProtocolContext protocolContext, PriorityBlockingQueue<BlaubotMessage> queuedMessages) {
		this.queuedMessages = queuedMessages;
		this.connection = connection;
		this.executorPool = Executors.newSingleThreadExecutor();
	}

	/**
	 * {@link Runnable} used for consuming {@link BlaubotMessage} from the given
	 * message queue. will instantly send new messages if not already busy.
	 * 
	 * @author manuelpras
	 *
	 */
	private class ConsumerRunnable implements Runnable {

		@Override
		public void run() {
			while (consumerRunnable == this && !Thread.currentThread().isInterrupted()) {
				BlaubotMessage queuedMessage;
				try {
					queuedMessage = queuedMessages.poll(MESSAGEQUEUE_TIMEOUT, TimeUnit.MILLISECONDS);
				} catch (InterruptedException ignore) {
					continue;
				}
				// no message in queue, continue polling
				if (queuedMessage == null) {
					continue;
				}
				try {
					byte[] byteArray = queuedMessage.toByteArray();
					connection.write(byteArray);
				} catch (IOException e) {
					if (Log.logWarningMessages()) {
						Log.w(TAG, "Catched IOException -> Message could not be sent, put back to queue: " + queuedMessage);
					}
					// put back to queue in order to retry later
					queuedMessages.add(queuedMessage);

					// Wait a little before retrying to mitigate the busy wait a
					// bit
					try {
						Thread.sleep(MESSAGEQUEUE_TIMEOUT);
					} catch (InterruptedException e1) {
					}
				}
			}
			if (Log.logDebugMessages()) {
				Log.d(TAG, "Stopped consuming");
			}
		}
	};

	@Override
	public void activate() {
		// create new runnable if not already existing
		if (this.consumerRunnable == null) {
			this.consumerRunnable = new ConsumerRunnable();
		} else {
			// if already existing and also running -> throw exception
			throw new RuntimeException("activate must not be called on running queue consumer");
		}
		if (Log.logDebugMessages()) {
			Log.d(TAG, "activating queue consumer");
		}
		this.executorPool.submit(this.consumerRunnable);
	}

	@Override
	public void deactivate() {
		if (this.consumerRunnable == null) {
			if (Log.logDebugMessages()) {
				Log.d(TAG, "deactivate ignored, no running queue consumer");
			}
			return;
		}
		if (Log.logDebugMessages()) {
			Log.d(TAG, "deactivating running queue consumer");
		}
		this.consumerRunnable = null;
	}

}