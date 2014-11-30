package de.hsrm.blaubot.core.statemachine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.acceptor.BlaubotConnectionManager;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import de.hsrm.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.events.AbstractTimeoutStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.events.AdminMessageStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.events.ConnectionClosedStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.events.ConnectionEstablishedStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.events.StartStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.events.StopStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.states.FreeState;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.core.statemachine.states.StoppedState;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;
import de.hsrm.blaubot.message.admin.AdminMessageFactory;
import de.hsrm.blaubot.protocol.IMessageListener;
import de.hsrm.blaubot.protocol.client.channel.IChannel;
import de.hsrm.blaubot.util.Log;

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
	private final List<IBlaubotDiscoveryEventListener> discoveryEventListeners; // proxy listeners
	private final List<IBlaubotBeaconInterface> beacons;
	private final List<IBlaubotConnectionAcceptor> acceptors;
	private final List<IBlaubotAdapter> adapters;
	private final BlaubotBeaconService beaconService;
	
	private final BlockingQueue<AbstractBlaubotStateMachineEvent> stateMachineEventQueue;
	private final StateMachineSession stateMachineSession;
	private IChannel adminBroadcastChannel;
//	private IChannel ownDeviceChannel;
	private StateMachineEventDispatcher stateMachineEventDispatcher;
	protected IBlaubotState currentState;
	
	public ConnectionStateMachine(final BlaubotConnectionManager connectionManager, final List<IBlaubotAdapter> adapters, final Blaubot blaubot) {
		this.blaubot = blaubot;
		this.adapters = adapters;
		this.stateMachineEventQueue = new LinkedBlockingQueue<AbstractBlaubotStateMachineEvent>();
		this.beacons = BlaubotAdapterHelper.getBeaconInterfaces(adapters);
		this.acceptors = BlaubotAdapterHelper.getConnectionAcceptors(adapters);
		
		this.beaconService = new BlaubotBeaconService(beacons);
		this.connectionStateMachineListeners = new CopyOnWriteArrayList<IBlaubotConnectionStateMachineListener>();
		this.discoveryEventListeners = new CopyOnWriteArrayList<IBlaubotDiscoveryEventListener>();
		for (IBlaubotBeaconInterface beaconInterface : beacons) {
			// @TODO: move setDiscoveryEventListener to beaconService, remove the onDeviceDiscovered method from the beaconinterface
			beaconInterface.setDiscoveryEventListener(discoveryEventListener);
		}

		createAdminBroadcastChannel();
		connectionManager.addConnectionListener(connectionListener);
		this.stateMachineSession = new StateMachineSession(this);
		this.currentState = new StoppedState();
	}

	/**
	 * Creates a new admin broadcast channel.
	 */
	private void createAdminBroadcastChannel() {
		// register to admin own devices channels and broadcast channel
		adminBroadcastChannel = this.blaubot.getProtocolManager().getChannelFactory().getConnectionLayerBroadcastChannel();
		adminBroadcastChannel.subscribe(adminMessageChannelListener);
	}


	private Object eventDispatcherLock = new Object();
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
	
	public void startStateMachine() {
		pushStateMachineEvent(new StartStateMachineEvent(currentState));
	}
	
	public void _startStateMachine() {
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
	
	public void stopStateMachine() {
		pushStateMachineEvent(new StopStateMachineEvent(currentState));
	}
	
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
		
		if (!(newState instanceof StoppedState)) {
			for (IBlaubotBeaconInterface beacon : beacons) {
				beacon.onConnectionStateMachineStateChanged(newState);
			}
		}
		currentState.handleState(stateMachineSession);

		// inform the beacon service
		beaconService.onStateChanged(currentState);
		
		// inform the listeners
		for (IBlaubotConnectionStateMachineListener connectionStateMachineListener : this.connectionStateMachineListeners) {
			if (sendStarted)
				connectionStateMachineListener.onStateMachineStarted();
			if (sendStopped)
				connectionStateMachineListener.onStateMachineStopped();
			connectionStateMachineListener.onStateChanged(oldState, newState);
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


	public void addConnectionStateMachineListener(IBlaubotConnectionStateMachineListener connectionStateMachineListener) {
		this.connectionStateMachineListeners.add(connectionStateMachineListener);
	}

	public void removeConnectionStateMachineListener(IBlaubotConnectionStateMachineListener connectionStateMachineListener) {
		this.connectionStateMachineListeners.remove(connectionStateMachineListener);
	}

	public List<IBlaubotConnectionAcceptor> getConnectionAcceptors() {
		return acceptors;
	}

	protected List<IBlaubotBeaconInterface> getBeacons() {
		return beacons;
	}

	protected IChannel getAdminBroadcastChannel() {
		return adminBroadcastChannel;
	}
	
	public BlaubotBeaconService getBeaconService() {
		return beaconService;
	}

	private IBlaubotDiscoveryEventListener discoveryEventListener = new IBlaubotDiscoveryEventListener() {
		@Override
		public void onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "[Current state: " + currentState + "] Got discovery Event " + discoveryEvent);
			}
			// inject the currentState
			discoveryEvent.setState(currentState);
			pushStateMachineEvent(discoveryEvent);
			
			// We forward the discovery events of all beacons to the registered listeners.
			// TODO: maybe introduce a dedicated interface for discovery events to additionally pass the source beacon object with it
			// inform the listeners
			for(IBlaubotDiscoveryEventListener listener : discoveryEventListeners) {
				listener.onDeviceDiscoveryEvent(discoveryEvent);
			}
		}
	};

	private IBlaubotConnectionManagerListener connectionListener = new IBlaubotConnectionManagerListener() {
		@Override
		public void onConnectionClosed(IBlaubotConnection connection) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "[Current state: " + currentState + "] Connection down: " + connection);
			}
			ConnectionClosedStateMachineEvent event = new ConnectionClosedStateMachineEvent(connection);
			event.setState(currentState);
			pushStateMachineEvent(event);
		}

		@Override
		public void onConnectionEstablished(IBlaubotConnection connection) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "[Current state: " + currentState + "] Got new connection: " + connection);
			}
			ConnectionEstablishedStateMachineEvent event = new ConnectionEstablishedStateMachineEvent(connection);
			event.setState(currentState);
			pushStateMachineEvent(event);
		}
	};

	private IMessageListener adminMessageChannelListener = new IMessageListener() {
		@Override
		public void onMessage(BlaubotMessage message) {
			AbstractAdminMessage adminMessage = AdminMessageFactory.createAdminMessageFromRawMessage(message);
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "[Current state: " + currentState + "] Got admin message: " + adminMessage);
			}
			pushStateMachineEvent(new AdminMessageStateMachineEvent(currentState, adminMessage));
		}
	};
	

	/**
	 * Gathers the {@link IBlaubotDevice} instances representing our own device from the beacons and acceptors and
	 * returns them as a set.
	 * 
	 * @return a set of {@link IBlaubotDevice} instances representing our own device
	 */
	public Set<IBlaubotDevice> getOwnDevicesSet() {
		HashSet<IBlaubotDevice> devices = new HashSet<IBlaubotDevice>();
		for(IBlaubotAdapter adapter : adapters) {
			devices.add(adapter.getOwnDevice());
		}
		return devices;
	}
	

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
		
		private void handleState(IBlaubotState state) {
			changeState(state);
		}
		
		@Override
		public void run() {
			while(!isInterrupted() && Thread.currentThread() == stateMachineEventDispatcher) {
				try {
					AbstractBlaubotStateMachineEvent event = stateMachineEventQueue.take();
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "[curState: "+ currentState +"] CSM EventQueue took: " + event);
					}
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
						if(!getOwnDevicesSet().contains(((AbstractBlaubotDeviceDiscoveryEvent) event).getRemoteDevice())) {
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
				} catch (InterruptedException e) {
					break;
				}
			}
		}
		
	}
	
	public void pushStateMachineEvent(AbstractBlaubotStateMachineEvent stateMachineEvent) {
		try {
			stateMachineEventQueue.put(stateMachineEvent);
		} catch (InterruptedException e) {
			// ignore
		}
	}

	public StateMachineSession getStateMachineSession() {
		return stateMachineSession;
	}
	
	/**
	 * Adds an {@link IBlaubotDiscoveryEventListener} to the state machine.
	 * The listeners are called for each discovery event of any {@link IBlaubotBeaconInterface}.
	 * @param discoveryEventListener
	 */
	public void addDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
		this.discoveryEventListeners.add(discoveryEventListener);
	}

	/**
	 * Removes a listener
	 * @param discoveryEventListener
	 */
	public void removeDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
		this.discoveryEventListeners.remove(discoveryEventListener);
	}
}
