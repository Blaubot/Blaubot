package de.hsrm.blaubot.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import de.hsrm.blaubot.core.acceptor.BlaubotConnectionManager;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;
import de.hsrm.blaubot.core.statemachine.BlaubotAdapterHelper;
import de.hsrm.blaubot.core.statemachine.ConnectionStateMachine;
import de.hsrm.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotStateMachineEvent;
import de.hsrm.blaubot.core.statemachine.states.FreeState;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import de.hsrm.blaubot.core.statemachine.states.KingState;
import de.hsrm.blaubot.core.statemachine.states.PeasantState;
import de.hsrm.blaubot.core.statemachine.states.PeasantState.ConnectionAccomplishmentType;
import de.hsrm.blaubot.core.statemachine.states.StoppedState;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;
import de.hsrm.blaubot.message.admin.AdminMessageFactory;
import de.hsrm.blaubot.message.admin.CensusMessage;
import de.hsrm.blaubot.protocol.IMessageListener;
import de.hsrm.blaubot.protocol.IProtocolManager;
import de.hsrm.blaubot.protocol.ProtocolContext;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.client.channel.BroadcastChannel;
import de.hsrm.blaubot.protocol.client.channel.ChannelConfig;
import de.hsrm.blaubot.protocol.client.channel.ChannelConfigFactory;
import de.hsrm.blaubot.protocol.client.channel.IChannel;
import de.hsrm.blaubot.protocol.master.ProtocolMaster;
import de.hsrm.blaubot.util.Log;

/**
 * The top level Blaubot object. Consists of the
 * {@link BlaubotConnectionManager}, {@link ConnectionStateMachine},
 * {@link IProtocolManager} and a bunch of {@link IBlaubotAdapter}s.
 * 
 * The {@link BlaubotConnectionManager} takes {@link IBlaubotConnection}s that
 * are established by the {@link IBlaubotAdapter} implementations which receive
 * incoming connections ({@link IBlaubotConnectionAcceptor}) and create
 * connections ({@link IBlaubotConnector}).
 * 
 * The {@link ConnectionStateMachine} manages the {@link IBlaubotState}s by
 * receiving {@link AbstractBlaubotStateMachineEvent}s created from incoming
 * admin {@link BlaubotMessage}s (from {@link IBlaubotConnection}s),
 * {@link AbstractBlaubotDeviceDiscoveryEvent} (from
 * {@link IBlaubotBeaconInterface}s) as well as new or closed connections (via
 * {@link IBlaubotConnectionManagerListener}s and their events).
 * 
 * Based on the {@link ConnectionStateMachine}s state changes, the
 * {@link IProtocolManager} will be set up to act as a Master or a Slave, reset
 * it's context or stop/start it's services.
 * 
 * <h1>USAGE:</h1> To create {@link IChannel}s for communication a user can use
 * the {@link Blaubot#createChannel(short)} and
 * {@link Blaubot#createChannel(short, ChannelConfig)} methods. Developers
 * should attach {@link ILifecycleListener}s to the {@link Blaubot} instance to
 * get high level information events about the currently formed {@link Blaubot}
 * network (Joins/Leaves of devices, established or broken networks, ...)
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class Blaubot {
	private static final String LOG_TAG = "Blaubot";
	// private final static int loggerInterval = 5000;

	private final ConcurrentHashMap<IBlaubotConnection, KeepAliveSender> keepAliveSenders;
	private final BlaubotConnectionManager connectionManager;
	private final ConnectionStateMachine connectionStateMachine;
	private final IProtocolManager protocolManager;
	private final List<IBlaubotAdapter> adapters;
	private final CopyOnWriteArrayList<ILifecycleListener> lifecycleListeners;
	private BroadcastChannel adminBroadcastChannel;
	private ProtocolContext context;

	/**
	 * @param adapters
	 *            a list of {@link IBlaubotAdapter} implementations to be used
	 *            by this {@link Blaubot} instance.
	 */
	public Blaubot(List<IBlaubotAdapter> adapters) {
		this.adapters = adapters;
		// Dependency injection
		for (IBlaubotAdapter adapter : adapters) {
			adapter.setBlaubot(this);
		}
		this.lifecycleListeners = new CopyOnWriteArrayList<ILifecycleListener>();
		this.keepAliveSenders = new ConcurrentHashMap<IBlaubotConnection, KeepAliveSender>();
		this.connectionManager = new BlaubotConnectionManager(BlaubotAdapterHelper.getConnectionAcceptors(adapters),
				BlaubotAdapterHelper.getConnectors(adapters));
		String ownUniqueDeviceID = getAdapters().get(0).getOwnDevice().getUniqueDeviceID();
		this.context = new ProtocolContext(ownUniqueDeviceID);
		ProtocolManager pm = new ProtocolManager(context);

		this.protocolManager = pm;
		this.connectionStateMachine = new ConnectionStateMachine(connectionManager, adapters, this);
		this.connectionStateMachine.addConnectionStateMachineListener(lifeCycleProtocolManager);
		// connect listeners
		this.connectionManager.addConnectionListener(new ConnectionManagerListener());
		this.connectionStateMachine.addConnectionStateMachineListener(new ConnectionStateMachineListener());
		this.adminBroadcastChannel = getProtocolManager().getChannelFactory().getConnectionLayerBroadcastChannel();
		this.adminBroadcastChannel.subscribe(lifeCycleEventDispatchingListener);
		this.connectionStateMachine.addConnectionStateMachineListener(lifeCycleEventDispatchingListener);

		addLifecycleListener(new ILifecycleListener() {
			@Override
			public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
				// System.out.println("onPrinceDeviceChanged(" + oldPrince +
				// ", " + newPrince + ")");
			}

			@Override
			public void onDisconnected() {
				// System.out.println("onDisconnected()");
			}

			@Override
			public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
				// System.out.println("onDeviceLeft(" + blaubotDevice + ")");
			}

			@Override
			public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
				// System.out.println("onDeviceJoined(" + blaubotDevice + ")");
			}

			@Override
			public void onConnected() {
				// System.out.println("onConnected()");
			}
		});

		// reset stats utility as soon as the actions starts in order to assert
		// proper init values
		// StatisticsUtil.reset();
	}

	/**
	 * Starts blaubot. If blaubot is already started, it will be RESTARTED.
	 * (stop, then start).
	 */
	public void startBlaubot() {
		// TODO: @mpras: there could be more than one blaubot instance on the
		// same jvm
		// StatisticsLogger statisticsLogger = new
		// StatisticsLogger(loggerInterval);
		// StatisticsLogger.currentThread = statisticsLogger;
		// statisticsLogger.start();
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting Blaubot, ProtocolManager and ConnectionStateMachine... ");
		}
		protocolManager.activate();
		connectionStateMachine.startEventDispatcher();
		connectionStateMachine.startStateMachine();
	}

	public void stopBlaubot() {
		if (Log.logDebugMessages())
			Log.d(LOG_TAG, "Stopping ConnectionStateMachine ... ");
		// StatisticsLogger.currentThread.interrupt();
		connectionStateMachine.stopStateMachine();
		// ProtocolManager will be stopped in callback to stop of
		// connectionStateMachine.stopEventDispatcher();
	}

	public BlaubotConnectionManager getConnectionManager() {
		return connectionManager;
	}

	public ConnectionStateMachine getConnectionStateMachine() {
		return connectionStateMachine;
	}

	public boolean isStarted() {
		return connectionStateMachine.isStateMachineStarted();
	}

	public IProtocolManager getProtocolManager() {
		return protocolManager;
	}

	public List<IBlaubotAdapter> getAdapters() {
		return adapters;
	}

	/**
	 * Creates a {@link IChannel}.
	 * 
	 * @param channelId
	 *            the unique id for this channel
	 * @param channelConfig
	 *            the channel specific configuration
	 * @return the ready to use {@link IChannel} (if connected)
	 */
	public IChannel createChannel(short channelId, ChannelConfig channelConfig) {
		return protocolManager.getChannelFactory().createUserChannel(channelConfig);
	}

	/**
	 * Crates an {@link IChannel} with the default {@link ChannelConfig}.
	 * 
	 * @param channelId
	 *            the unique id for this channel
	 * @return the ready to use {@link IChannel} (if connected)
	 */
	public IChannel createChannel(short channelId) {
		ChannelConfig defaultChannelConfig = ChannelConfigFactory.getNoLimitConfig().Id(channelId);
		return protocolManager.getChannelFactory().createUserChannel(defaultChannelConfig);
	}

	/**
	 * Adds an {@link ILifecycleListener} to this {@link Blaubot} instance.
	 * 
	 * @param lifecycleListener
	 *            the listener to add
	 */
	public void addLifecycleListener(ILifecycleListener lifecycleListener) {
		this.lifecycleListeners.add(lifecycleListener);
	}

	/**
	 * Removes an {@link ILifecycleListener} from this {@link Blaubot} instance.
	 * 
	 * @param lifecycleListener
	 *            the listener to remove
	 */
	public void removeLifecycleListener(ILifecycleListener lifecycleListener) {
		this.lifecycleListeners.remove(lifecycleListener);
	}

	@Override
	public String toString() {
		return "Blaubot [ownDevices=" + connectionStateMachine.getOwnDevicesSet() + "]";
	}

	/**
	 * This listener handles the creation, start and stop of
	 * {@link KeepAliveSender}s for all connected {@link IBlaubotDevice}s.
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 *
	 */
	class ConnectionManagerListener implements IBlaubotConnectionManagerListener {
		@Override
		public void onConnectionEstablished(IBlaubotConnection connection) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Got onConnectionEstablished from CONNECTIONMANAGER: " + connection);
				Log.d(LOG_TAG, "Current connections: " + connectionManager.getAllConnections());
				Log.d(LOG_TAG, "Connected devices: " + connectionManager.getConnectedDevices());
			}

			// send keep alive to all connected devices (king: to all peasants,
			// peasant: to king)
			KeepAliveSender keepAliveSender = new KeepAliveSender(connection.getRemoteDevice(), protocolManager);
			keepAliveSender.start();
			keepAliveSenders.put(connection, keepAliveSender);
		}

		@Override
		public void onConnectionClosed(IBlaubotConnection connection) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Got onConnectionClosed from CONNECTIONMANAGER: " + connection);
				Log.d(LOG_TAG, "Current connections: " + connectionManager.getAllConnections());
				Log.d(LOG_TAG, "Connected devices: " + connectionManager.getConnectedDevices());
			}

			// handle keep alive
			KeepAliveSender keepAliveSender = keepAliveSenders.get(connection);
			if (keepAliveSender != null) {
				keepAliveSender.stop();
				keepAliveSenders.remove(connection);
			}
		}
	}

	private LifeCycleEventDispatchingListener lifeCycleEventDispatchingListener = new LifeCycleEventDispatchingListener();

	/**
	 * Listens for {@link CensusMessage}s, calculates the diff (left or joined
	 * devices and prince changes) and communicates them through the
	 * {@link ILifecycleListener}s attached to this {@link Blaubot} instance.
	 */
	class LifeCycleEventDispatchingListener implements IMessageListener, IBlaubotConnectionStateMachineListener {
		public static final String LOG_TAG = "LifeCycleEventDispatchingListener";
		// maps the last census message for different networks by the king's
		// uniqueId (kingUniqueId -> lastCensusMessage)
		private Map<String, CensusMessage> lastCensusMessages = new HashMap<String, CensusMessage>();

		@Override
		public void onMessage(BlaubotMessage message) {
			AbstractAdminMessage adminMessage = AdminMessageFactory.createAdminMessageFromRawMessage(message);
			if (adminMessage instanceof CensusMessage) {
				CensusMessage cm = (CensusMessage) adminMessage;
				String currentNetworkKingUniqueId = cm.extractKingUniqueId();
				CensusMessage lastCensusMessage = lastCensusMessages.containsKey(currentNetworkKingUniqueId) ? lastCensusMessages
						.get(currentNetworkKingUniqueId) : new CensusMessage(new HashMap<String, State>());

				// create a set containing all new uniqueIds in the network
				Set<String> newUniqueIds = cm.getDeviceStates().keySet();
				newUniqueIds.removeAll(lastCensusMessage.getDeviceStates().keySet());

				// create a set containing all removed uniqueIds since the last
				// census message
				Set<String> missingUniqueIds = lastCensusMessage.getDeviceStates().keySet();
				missingUniqueIds.removeAll(cm.getDeviceStates().keySet());

				// check if the prince has changed
				String oldPrince = lastCensusMessage.extractPrinceUniqueId();
				String newPrince = cm.extractPrinceUniqueId();
				boolean princeChanged = (oldPrince == null && newPrince != null) || (newPrince == null && oldPrince != null)
						|| (!(oldPrince == null && newPrince == null) && !newPrince.equals(oldPrince));

				// cache created blaubot device objects
				Map<String, IBlaubotDevice> deviceCache = new HashMap<String, IBlaubotDevice>();
				// call the listeners for each joined/left device or prince
				// change
				for (ILifecycleListener listener : lifecycleListeners) {
					// joined devices
					for (String uniqueId : newUniqueIds) {
						IBlaubotDevice device = deviceCache.containsKey(uniqueId) ? deviceCache.get(uniqueId) : connectionManager
								.getBlaubotDeviceFromUniqueId(uniqueId);
						listener.onDeviceJoined(device);
					}
					// left devices
					for (String uniqueId : missingUniqueIds) {
						IBlaubotDevice device = deviceCache.containsKey(uniqueId) ? deviceCache.get(uniqueId) : connectionManager
								.getBlaubotDeviceFromUniqueId(uniqueId);
						listener.onDeviceLeft(device);
					}
					// prince
					if (princeChanged) {
						IBlaubotDevice oldPrinceD = null, newPrinceD = null;
						if (oldPrince != null) {
							oldPrinceD = deviceCache.containsKey(oldPrince) ? deviceCache.get(oldPrince) : connectionManager
									.getBlaubotDeviceFromUniqueId(oldPrince);
						}
						if (newPrince != null) {
							newPrinceD = deviceCache.containsKey(newPrince) ? deviceCache.get(newPrince) : connectionManager
									.getBlaubotDeviceFromUniqueId(newPrince);
						}
						listener.onPrinceDeviceChanged(oldPrinceD, newPrinceD);
					}
				}
				lastCensusMessages.put(currentNetworkKingUniqueId, cm);
			}
		}

		@Override
		public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
			if (newState instanceof PeasantState) {
				PeasantState ps = (PeasantState) newState;
				if (ps.getConnectionAccomplishmentType().equals(ConnectionAccomplishmentType.BOWED_DOWN)) {
					notifyDisconnectedFromNetwork(oldState);
				}
				for (ILifecycleListener listener : lifecycleListeners) {
					listener.onConnected();
					// the onDeviceJoined will be triggered from the
					// CensusMessage
					// TODO: the order of events: onConnected() and
					// onDeviceJoined() is not guaranteed at the moment! (the
					// messaging could be faster)
				}
			} else if (newState instanceof FreeState) {
				if (!(oldState instanceof StoppedState)) {
					// -- we disconnected from a network
					notifyDisconnectedFromNetwork(oldState);
				}
			} else if (newState instanceof KingState) {
				if (oldState instanceof FreeState) {
					// -- we changed to KingState from a FreeState (excludes the
					// case when we change to KingState from PrinceState)
					for (ILifecycleListener listener : lifecycleListeners) {
						listener.onConnected();
					}
				}
			}

		}

		/**
		 * Triggers onDeviceLeft(..) for all of the known devices from the old
		 * network (except the own devices) followed by a onDisconnected() on
		 * the {@link ILifecycleListener} added to this {@link Blaubot}
		 * instance.
		 * 
		 * @param oldState
		 *            must be a {@link IBlaubotSubordinatedState} or
		 *            {@link KingState}
		 */
		private void notifyDisconnectedFromNetwork(IBlaubotState oldState) {
			String oldKingUniqueId = null;
			if (oldState instanceof IBlaubotSubordinatedState) {
				oldKingUniqueId = ((IBlaubotSubordinatedState) oldState).getKingUniqueId();
			} else if (oldState instanceof KingState) {
				oldKingUniqueId = adapters.get(0).getOwnDevice().getUniqueDeviceID(); // TODO:
																						// multiple
																						// adapters
																						// not
																						// supported
																						// here!
																						// Define
																						// what
																						// to
																						// do
																						// in
																						// this
																						// case.
			} else {
				throw new IllegalStateException("oldState should be IBlaubotSubordinateState or KingState");
			}
			if (oldKingUniqueId == null) {
				throw new NullPointerException("Unique id of the former kingdom's king could not be determined.");
			}
			// -- we connected to another network due to a merge (bow down)
			// -- the uniqueId of our old king is known as well as the new
			// king's uniqueId
			// as of
			// https://scm.mi.hs-rm.de/trac/2014maprojekt/2014maprojekt01/ticket/22
			// we have to trigger onDeviceLeft for
			// each device of the former network (lastCensusMessage) followed by
			// onDisconnected()
			// then we have to call onConnected() and onDeviceJoined(device) for
			// each device of the new network

			// trigger onDeviceLeft for each of the former connected devices
			// except ourselves
			CensusMessage oldNetworksLastCensusMessage = lastCensusMessages.get(oldKingUniqueId);
			if (oldNetworksLastCensusMessage != null) {
				// create the set of IBlaubotDevices to trigger a onDeviceLeft
				// event for
				Set<IBlaubotDevice> leftDevices = new HashSet<IBlaubotDevice>();
				for (String deviceUniqueId : oldNetworksLastCensusMessage.getDeviceStates().keySet()) {
					// check if we can ignore this device becaus it is one of
					// our own uniqueIds
					boolean skip = false;
					for (IBlaubotAdapter adapter : adapters) {
						if (deviceUniqueId.equals(adapter.getOwnDevice().getUniqueDeviceID())) {
							skip = true;
						}
					}
					if (skip) {
						continue;
					}
					// if we can't skip, add a blaubotDevice instance
					IBlaubotDevice device = connectionManager.getBlaubotDeviceFromUniqueId(deviceUniqueId);
					leftDevices.add(device);
				}

				// finally trigger the onDeviceLeft events
				for (ILifecycleListener listener : lifecycleListeners) {
					for (IBlaubotDevice leftDevice : leftDevices) {
						listener.onDeviceLeft(leftDevice);
					}
				}

				// fire the onDisconnected
				for (ILifecycleListener listener : lifecycleListeners) {
					listener.onDisconnected();
				}

			} else {
				if (Log.logWarningMessages()) {
					Log.w(LOG_TAG, "Never got a CensusMessage from my old network (King was: " + oldKingUniqueId + ")");
				}
			}
		}

		@Override
		public void onStateMachineStopped() {
			// TODO Auto-generated method stub

		}

		@Override
		public void onStateMachineStarted() {
			// TODO Auto-generated method stub

		}
	};

	/**
	 * Just for logging purposes.
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	static class ConnectionStateMachineListener implements IBlaubotConnectionStateMachineListener {
		@Override
		public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "ConnectionStateMachineListener got onStateChange(). New state: " + newState);
			}
		}

		@Override
		public void onStateMachineStopped() {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "StateMachine stopped");
			}
		}

		@Override
		public void onStateMachineStarted() {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "StateMachine started");
			}
		}
	}

	/**
	 * Listens to the {@link ConnectionStateMachine} to set the
	 * {@link ProtocolManager}'s state.
	 */
	private IBlaubotConnectionStateMachineListener lifeCycleProtocolManager = new IBlaubotConnectionStateMachineListener() {
		private static final String LOG_TAG = "CsmToPmLifecycleConnector";

		@Override
		public void onStateMachineStopped() {
			// protocolManager must be put into a defined state and be stopped
			// after ConnectionStateMachineStop
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Setting PM CLIENT mode, resetting and deactivating (CSM stopped)");
			}
			protocolManager.setMaster(false);
			protocolManager.reset();
			protocolManager.deactivate();
		}

		@Override
		public void onStateMachineStarted() {
			// protocolManager must have been started before
			// ConnectionStateMachineStart (in BlaubotStart)
		}

		/**
		 * Sets up the {@link ProtocolManager} as master including the listener
		 * wiring to inform the protocol manager of new
		 * {@link IBlaubotConnection}s.
		 * 
		 * @param oldState
		 * @param newState
		 */
		private void onChangedToMaster(IBlaubotState oldState, IBlaubotState newState) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Setting PM to MASTER mode");
			}
			// set to master if new state instanceof KingState
			protocolManager.setMaster(true);

			// We are king and await incoming connections from peasants
			// We register a listener to the KingState to inform the
			// ProtocolManager about new Peasant connections
			KingState kingState = (KingState) newState;
			kingState.setPeasantConnectionsListener(new IBlaubotIncomingConnectionListener() {
				@Override
				public void onConnectionEstablished(IBlaubotConnection connection) {
					if (Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Got new connection as King -> ProtocolManager.addConnection()");
					}
					protocolManager.addConnection(connection);
				}
			});
		}

		@Override
		public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
			if (oldState == newState) {
				// do nothing if the state remains the same
				return;
			}

			if (oldState instanceof KingState) {
				// remove listeners from obsolete KingStates
				// should not be necessary but we like it to be on safe on this
				((KingState) oldState).setPeasantConnectionsListener(null);
			}

			boolean isNowInKingState = newState instanceof KingState;
			// iff state class has changed, set the master state and register
			// listeners
			if (stateClassChanged(oldState, newState) && isNowInKingState) {
				onChangedToMaster(oldState, newState);
				return;
			}
			// check if the new state is a subordinate state and handle it
			onChangedToClient(oldState, newState);
		}

		/**
		 * Sets up the {@link ProtocolManager} as client and adds the king's
		 * {@link IBlaubotConnection}.
		 * 
		 * @param oldState
		 * @param newState
		 */
		private void onChangedToClient(IBlaubotState oldState, IBlaubotState newState) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Setting PM to CLIENT mode");
			}
			protocolManager.setMaster(false);

			// Iff we are subordinate, we have exactly one connection to our
			// king
			// Check and inform protocol manager only, if the connection to the
			// king has changed
			if (newState instanceof IBlaubotSubordinatedState) {
				// -- we are peasant or prince
				IBlaubotConnection kingConnection = ((IBlaubotSubordinatedState) newState).getKingConnection();
				if (oldState instanceof PeasantState && newState instanceof PeasantState && newState != oldState) {
					PeasantState ps = (PeasantState) oldState;
					if (ps.getConnectionAccomplishmentType() == ConnectionAccomplishmentType.BOWED_DOWN) {
						Log.d(LOG_TAG, "We bowed down to a new king with this state change -> ProtocolManager.reset()");
						protocolManager.reset();
					}
				}
				if (oldState instanceof IBlaubotSubordinatedState) {
					IBlaubotConnection oldKingConnection = ((IBlaubotSubordinatedState) oldState).getKingConnection();
					if (oldKingConnection == kingConnection) {
						// -- the king connection has not changed
						// do not inform the protocol manager
						if (Log.logDebugMessages()) {
							Log.d(LOG_TAG,
									"The new state's kingConnection is the same as the old state's kingConnection. Not informing ProtocolManager.");
						}
						return;
					}
				}
				// -- the new state is a subordinate state and the
				// KingConnection has changed
				if (Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Adding king connection to ProtocolManager.");
				}
				protocolManager.addConnection(kingConnection);
			}
		}

		private boolean stateClassChanged(IBlaubotState oldState, IBlaubotState newState) {
			if (oldState == null) {
				return true;
			}
			return (!newState.getClass().equals(oldState.getClass()));
		}
	};

	/**
	 * call this method after joining a blaubot network if you want a quick
	 * response. otherwise the calling thread will have to wait until your
	 * {@link Blaubot} instance joined a network and receives its device id from
	 * the {@link ProtocolMaster}
	 * 
	 * @return device id of this {@link Blaubot} instance. identifies a
	 *         {@link IBlaubotDevice} in the blaubot network.
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @see FutureTask#get()
	 */
	public short getOwnDeviceId() throws InterruptedException, ExecutionException {
		String ownUniqueDeviceID = this.context.getOwnUniqueDeviceID();
		FutureTask<Short> ownDeviceId = this.context.getShortDeviceId(ownUniqueDeviceID);
		return ownDeviceId.get();
	}

	private UUID guid = UUID.randomUUID(); // TODO: implement proper equals and
											// hashcode

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((guid == null) ? 0 : guid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Blaubot other = (Blaubot) obj;
		if (guid == null) {
			if (other.guid != null)
				return false;
		} else if (!guid.equals(other.guid))
			return false;
		return true;
	}

}
