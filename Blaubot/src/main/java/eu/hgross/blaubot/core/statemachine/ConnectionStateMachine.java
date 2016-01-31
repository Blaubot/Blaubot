package eu.hgross.blaubot.core.statemachine;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.RelayAdminMessage;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotConnectionManager;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ServerConnectionManager;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.events.AbstractTimeoutStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.events.AdminMessageStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.events.ConnectionClosedStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.events.ConnectionEstablishedStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.events.StartStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.events.StopStateMachineEvent;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.StoppedState;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.util.Log;

/**
 * Statemachine for the network creation. Simply delegates the events from the {@link BlaubotConnectionManager} and the
 * {@link AbstractBlaubotDeviceDiscoveryEvent}s from the beacons to the current state. The state will handle the event
 * based state changes by themselves (calling nextState).
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class ConnectionStateMachine {
	private static final String LOG_TAG = "ConnectionStateMachine";
	protected final Blaubot blaubot;
	private final List<IBlaubotConnectionStateMachineListener> connectionStateMachineListeners;
	private final List<IBlaubotBeacon> beacons;
	private final List<IBlaubotConnectionAcceptor> acceptors;
	private final List<IBlaubotAdapter> adapters;
	private final BlaubotBeaconService beaconService;
	
	private final BlockingQueue<AbstractBlaubotStateMachineEvent> stateMachineEventQueue;
	private final StateMachineSession stateMachineSession;
	private StateMachineEventDispatcher stateMachineEventDispatcher;
	protected IBlaubotState currentState;

    /**
     * Create the connection state machine for a blaubot instance.
     *
     * @param ownDevice
     * @param connectionManager the connection manager managing the blaubot acceptors
     * @param adapters the adapters
     * @param blaubot the blaubot instance
     */
	public ConnectionStateMachine(IBlaubotDevice ownDevice, final BlaubotConnectionManager connectionManager, final List<IBlaubotAdapter> adapters, List<IBlaubotBeacon> beacons, final Blaubot blaubot, ServerConnectionManager serverConnectionManager) {
		this.blaubot = blaubot;
		this.adapters = adapters;
		this.stateMachineEventQueue = new LinkedBlockingQueue<AbstractBlaubotStateMachineEvent>();
		this.beacons = beacons;
		this.acceptors = BlaubotAdapterHelper.getConnectionAcceptors(adapters);
		
		this.beaconService = new BlaubotBeaconService(ownDevice, beacons, acceptors, this);
		this.connectionStateMachineListeners = new CopyOnWriteArrayList<>();


        // connect to admin messages
        this.blaubot.getChannelManager().addAdminMessageListener(adminMessageChannelListener);

		connectionManager.addConnectionListener(connectionListener);
		this.stateMachineSession = new StateMachineSession(this, ownDevice, serverConnectionManager);
		this.currentState = new StoppedState();
	}


	private final Object eventDispatcherLock = new Object();

    /**
     * Starts the CSM's event dispatcher. Mandatory for the CSM to do anything.
     */
	public void startEventDispatcher() {
		synchronized (eventDispatcherLock) {
			if(isEventDispatcherRunning()) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "EventDispatcher already running - ignoring startEventDispatcher()");
				}
				return;
			}
			stateMachineEventDispatcher = new StateMachineEventDispatcher();
			stateMachineEventDispatcher.start();
		}
	}
	
	public void stopEventDispatcher() {
		synchronized (eventDispatcherLock) {
			if(stateMachineEventDispatcher != null) {
				stateMachineEventDispatcher.interrupt();
				stateMachineEventDispatcher = null;
			}
		}
	}

    /**
     * Starts the state machine by pushing a start event to the CSM's event queue.
     */
	public void startStateMachine() {
		pushStateMachineEvent(new StartStateMachineEvent(currentState));
	}

    /**
     * Actually handles the start of the CSM
     */
	private void _startStateMachine() {
		if (!(currentState instanceof StoppedState)) {
			if(Log.logWarningMessages()) {
				Log.w(LOG_TAG, "ConnectionStateMachine already started. Ignoring _startStateMachine()");
			}
			return; // already started
		}
		StoppedState stoppedState = (StoppedState) this.currentState;
		stoppedState.handleState(this.stateMachineSession);
		changeState(new FreeState());
	}

    /**
     * Requests a stop of the CSM by pushing an event to the CSM's event queue.
     */
	public void stopStateMachine() {
		pushStateMachineEvent(new StopStateMachineEvent(currentState));
	}

    /**
     * Actually handles the stop of the state machine
     */
	private void _stopStateMachine() {
		this.changeState(new StoppedState());
	}

	/**
	 * changes state
	 *  
	 * @param newState
	 */
	private void changeState(IBlaubotState newState) {
		if(newState == currentState)
			return; // do nothing if same state
        IBlaubotState oldState = currentState;
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "[Current state: " + currentState + "] Changing to state " + newState);
		}
		assertStateChange(currentState, newState);
		boolean sendStarted = false;
		boolean sendStopped = false;
		if (currentState instanceof StoppedState &&  !(newState instanceof StoppedState)) {
			sendStarted = true;
		} else if (newState instanceof StoppedState && !(currentState instanceof StoppedState)) {
			sendStopped = true;
		}
		currentState = newState;

        // let the beacons signal the new state, if not a StoppedState, which would make no sense at all
        // inform the beacon service
        beaconService.onStateChanged(currentState);
        if (!(newState instanceof StoppedState)) {
			for (IBlaubotBeacon beacon : beacons) {
				beacon.onConnectionStateMachineStateChanged(newState);
			}
		}

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Handling state " + currentState.getClass().getSimpleName() + " ...");
        }
		currentState.handleState(stateMachineSession);
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "State " + currentState.getClass().getSimpleName() + " handled.");
        }

		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Notifying ConnectionStateMachineListeners onStateChanged() ...");
		}
		// inform the listeners
		for (IBlaubotConnectionStateMachineListener connectionStateMachineListener : this.connectionStateMachineListeners) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Notifying listener " + connectionStateMachineListener);
			}
			if (sendStarted)
				connectionStateMachineListener.onStateMachineStarted();
			if (sendStopped)
				connectionStateMachineListener.onStateMachineStopped();
			connectionStateMachineListener.onStateChanged(oldState, newState);
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Done notifying listener " + connectionStateMachineListener);
			}
		}
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Done notifying onStateChanged() ...");
		}
	}
	
	/**
	 * @param currentState
	 * @param nextState
	 */
	private static void assertStateChange(IBlaubotState currentState, IBlaubotState nextState) {
		State cur = State.getStateByStatemachineClass(currentState.getClass());
		State to = State.getStateByStatemachineClass(nextState.getClass());
		if(!cur.isStateChangeAllowed(to)) {
			throw new IllegalStateException("A state change from " + cur + " to " + to + " is not allowed.");
		}
	}

    /**
     * Registers a listener to get informed about state changes of the state machine
     * @param connectionStateMachineListener the listener to add
     */
	public void addConnectionStateMachineListener(IBlaubotConnectionStateMachineListener connectionStateMachineListener) {
		this.connectionStateMachineListeners.add(connectionStateMachineListener);
	}

    /**
     * Removes a previously added listener
     * @param connectionStateMachineListener the listener to remove
     */
    public void removeConnectionStateMachineListener(IBlaubotConnectionStateMachineListener connectionStateMachineListener) {
		this.connectionStateMachineListeners.remove(connectionStateMachineListener);
	}

	public List<IBlaubotConnectionAcceptor> getConnectionAcceptors() {
		return acceptors;
	}

	public BlaubotBeaconService getBeaconService() {
		return beaconService;
	}


	private IBlaubotConnectionManagerListener connectionListener = new IBlaubotConnectionManagerListener() {
		@Override
		public void onConnectionClosed(IBlaubotConnection connection) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "[Current state: " + currentState + "] Connection down: " + connection + ". Pushing as CSM event to queue.");
			}
			ConnectionClosedStateMachineEvent event = new ConnectionClosedStateMachineEvent(connection);
			event.setConnectionStateMachineState(currentState);
			pushStateMachineEvent(event);
		}

		@Override
		public void onConnectionEstablished(IBlaubotConnection connection) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "[Current state: " + currentState + "] Got new connection: " + connection + ". Pushing as CSM event to queue.");
			}
			ConnectionEstablishedStateMachineEvent event = new ConnectionEstablishedStateMachineEvent(connection);
			event.setConnectionStateMachineState(currentState);
			pushStateMachineEvent(event);
		}
	};

	private IBlaubotAdminMessageListener adminMessageChannelListener = new IBlaubotAdminMessageListener() {
		@Override
		public void onAdminMessage(AbstractAdminMessage adminMessage) {
			if (adminMessage instanceof RelayAdminMessage) {
				// don't process relay messages
				return;
			}
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "[Current state: " + currentState + "] Got admin message: " + adminMessage + ". Pushing as CSM event to queue.");
			}
			pushStateMachineEvent(new AdminMessageStateMachineEvent(currentState, adminMessage));
		}
	};
	

	public IBlaubotState getCurrentState() {
		return this.currentState;
	}
	
	public boolean isStateMachineStarted() {
		return !(currentState instanceof StoppedState);
	}
	
	private boolean isEventDispatcherRunning() {
		synchronized (eventDispatcherLock) {
			return this.stateMachineEventDispatcher != null && this.stateMachineEventDispatcher.isAlive();
		}
	}

	/**
	 * Dispatches events from the eventQueue to the current state. (UI-Thread alike)
	 *
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	class StateMachineEventDispatcher extends Thread {
		private static final String LOG_TAG = "StateMachineEventDispatcher";
		/**
		 * Max time the processing of an event may take. If it takes longer,
		 * an exception will be thrown.
		 */
		private static final int MAX_EVENT_PROCESSING_TIME = 60000; // ms
		private Timer processingTimeoutTimer;

		public StateMachineEventDispatcher() {
			setName("csm-event-dispatcher");
		}
		private void handleState(IBlaubotState state) {
			changeState(state);
		}

		/**
		 * Starts a timer that will log warnings, if the ConnectionStateMachine takes too long 
		 * to process an event.
         * @param event the event that took too long to be processed
		 */
		private void startTimer(final AbstractBlaubotStateMachineEvent event) {
			processingTimeoutTimer = new Timer();
			final TimerTask timerTask = new TimerTask() {
				@Override
				public void run() {
                    final String message = " [curState: " + currentState + "] The processing of " + event + " took longer than " + MAX_EVENT_PROCESSING_TIME + " ms";
                    if (Log.logWarningMessages()) {
                        Log.e(LOG_TAG, message);
                    }
//                    throw new RuntimeException(new TimeoutException(message));
				}
			};
			processingTimeoutTimer.schedule(timerTask, MAX_EVENT_PROCESSING_TIME);
		}

		/**
		 * cancels the timer
		 */
		private void cancelTimer() {
			if (processingTimeoutTimer != null) {
				processingTimeoutTimer.cancel();
				processingTimeoutTimer = null;
			}
		}

		@Override
		public void run() {
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "StateMachineEventDispatcher started.");
            }
			while(!isInterrupted() && Thread.currentThread() == stateMachineEventDispatcher) {
				try {
					AbstractBlaubotStateMachineEvent event = stateMachineEventQueue.take();
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "[curState: "+ currentState +"] CSM EventQueue (size=" + stateMachineEventQueue.size() + ") took: " + event);
					}
                    // start the timeout timer
                    startTimer(event);
                    
                    // we measure the processing time
                    long startTime = System.currentTimeMillis();
					if(event instanceof AdminMessageStateMachineEvent) {
						AbstractAdminMessage aam = ((AdminMessageStateMachineEvent)event).getAdminMessage();
						IBlaubotState state = currentState.onAdminMessage(aam);
						handleState(state);
					} else if(event instanceof ConnectionClosedStateMachineEvent) {
						IBlaubotState state = currentState.onConnectionClosed(((ConnectionClosedStateMachineEvent) event).getConnection());
						handleState(state);
					} else if(event instanceof ConnectionEstablishedStateMachineEvent) {
						IBlaubotState state = currentState.onConnectionEstablished(((ConnectionEstablishedStateMachineEvent) event).getConnection());
						handleState(state);
					} else if(event instanceof AbstractBlaubotDeviceDiscoveryEvent) {
						// filter discovery events that discover ourselves
						if(!stateMachineSession.getOwnDevice().getUniqueDeviceID().equals(((AbstractBlaubotDeviceDiscoveryEvent) event).getRemoteDevice().getUniqueDeviceID())) {
							IBlaubotState state = currentState.onDeviceDiscoveryEvent((AbstractBlaubotDeviceDiscoveryEvent) event);
							handleState(state);
						}
					} else if (event instanceof AbstractTimeoutStateMachineEvent) {
						IBlaubotState state = currentState.onTimeoutEvent((AbstractTimeoutStateMachineEvent) event);
						handleState(state);
					} else if(event instanceof StopStateMachineEvent) {
						_stopStateMachine();
					} else if(event instanceof StartStateMachineEvent) {
						_startStateMachine();
					} else {
						throw new RuntimeException("Unknown event in event queue!");
					}
                    // stop timeout timer
                    cancelTimer();
                    
                    if(Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Event processing took " + (System.currentTimeMillis() - startTime) + " ms");
                    }
				} catch (InterruptedException e) {
					break;
				}
			}
            cancelTimer();
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "StateMachineEventDispatcher stopped.");
            }
		}

	}

    /**
     * Pushes a AbstractBlaubotStateMachineEvent to the StateMachine's event queue.
     * @param stateMachineEvent the event
     */
	public void pushStateMachineEvent(AbstractBlaubotStateMachineEvent stateMachineEvent) {
		try {
			stateMachineEventQueue.put(stateMachineEvent);
		} catch (InterruptedException e) {
			// ignore
		}
	}
}
