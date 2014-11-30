package de.hsrm.blaubot.protocol.eventdispatcher;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import de.hsrm.blaubot.protocol.IProtocolLifecycle;

/**
 * used to collect and dispatch (possibly concurrent) incoming events by adding
 * them to an event queue and processing them sequentially.
 * 
 * @author manuelpras
 * 
 */
public class ProtocolEventDispatcher implements IProtocolLifecycle {

	private final LinkedBlockingQueue<ProtocolEvent> eventQueue;
	private final AtomicBoolean isActivated;
	private final CopyOnWriteArraySet<ProtocolEventListener> listeners;

	public ProtocolEventDispatcher(LinkedBlockingQueue<ProtocolEvent> eventQueue) {
		this.eventQueue = eventQueue;
		this.isActivated = new AtomicBoolean(false);
		this.listeners = new CopyOnWriteArraySet<ProtocolEventListener>();
	}

	/**
	 * starts consuming events from the given event queue if not already
	 * started.
	 */
	@Override
	public synchronized void activate() {
		// ignore call if already activated
		if (this.isActivated.get()) {
			return;
		}
		this.isActivated.set(true);
		Executors.newSingleThreadExecutor().submit(command);
	}

	private final Runnable command = new Runnable() {

		@Override
		public void run() {
			while (isActivated.get() && !Thread.currentThread().isInterrupted()) {
				ProtocolEvent event = null;
				try {
					event = eventQueue.take();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				notifyListeners(event);
			}
		}
	};

	private void notifyListeners(ProtocolEvent event) {
		for (ProtocolEventListener listener : this.listeners) {
			listener.onProtocolEvent(event);
		}
	}

	public void addListener(ProtocolEventListener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(ProtocolEventListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * stops consuming events from given event queue.
	 */
	@Override
	public synchronized void deactivate() {
		this.isActivated.set(false);
	}

}
