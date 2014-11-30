package de.hsrm.blaubot.core.acceptor.discovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.acceptor.BlaubotConnectionManager;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import de.hsrm.blaubot.util.Log;

/**
 * This service handles the beacons on all interfaces. The beacon implementations simply hands the accepted connections
 * to this object and the actual conversation is generalized at this object's level.
 * 
 * After receiving a {@link IBlaubotConnection} from a {@link IBlaubotBeaconInterface} the BeaconService exchanges {@link State}s
 * with the remote peer.
 * 
 * The remote beacon implementation will most likely use the {@link ExchangeStatesTask} to exchange their state with the
 * {@link BeaconConnectionHandler} of this objects.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotBeaconService {
	private static final String LOG_TAG = "BlaubotBeaconService";
	private List<IBlaubotBeaconInterface> blaubotBeacons;
	private ExecutorService executorService;
	private BlaubotConnectionManager beaconConnectionManager;
	private BeaconMessage currentBeaconMessage; // maintained through onStateChanged(..) calls from the StateMachine

	public BlaubotBeaconService(List<IBlaubotBeaconInterface> blaubotBeacons) {
		this.currentBeaconMessage = new BeaconMessage(State.Stopped);
		this.blaubotBeacons = blaubotBeacons;
		this.executorService = Executors.newCachedThreadPool();
		this.beaconConnectionManager = new BlaubotConnectionManager(new ArrayList<IBlaubotConnectionAcceptor>(blaubotBeacons), new ArrayList<IBlaubotConnector>());
		this.beaconConnectionManager.addConnectionListener(new IBlaubotConnectionManagerListener() {
			@Override
			public void onConnectionEstablished(IBlaubotConnection connection) {
				handleBeaconConnection(connection);
			}

			@Override
			public void onConnectionClosed(IBlaubotConnection connection) {
			}
		});
	}

	private void handleBeaconConnection(IBlaubotConnection beaconConnection) {
		BeaconConnectionHandler task = new BeaconConnectionHandler(beaconConnection);
		this.executorService.execute(task);
	}

	public void startBeaconInterfaces() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting beacon interfaces ... ");
		}
		for (IBlaubotBeaconInterface beaconInterface : this.blaubotBeacons) {
			if (beaconInterface.isStarted()) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Beacon " + beaconInterface + " is already started - skipping.");
				}
				continue;
			}
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Starting beacon interface " + beaconInterface + " ... ");
			}
			beaconInterface.startListening();
		}
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Beacons started.");
		}
	}

	public void stopBeaconInterfaces() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Stopping beacon interfaces - going through all beacons ...");
		}
		for (IBlaubotBeaconInterface beaconInterface : this.blaubotBeacons) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "\tStopping beacon interface: " + beaconInterface);
			}
			beaconInterface.stopListening();
		}
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Beacon interfaces stopped.");
		}
	}

	/**
	 * A Task that handles the conversation with beacon clients.
	 * 
	 * @author Henning Gross <mail.to@henning-gross.de>
	 * 
	 */
	private class BeaconConnectionHandler implements Runnable {
		private static final String LOG_TAG = "BeaconConnectionHandler";
		private IBlaubotConnection connection;

		public BeaconConnectionHandler(IBlaubotConnection connection) {
			this.connection = connection;
		}

		@Override
		public void run() {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Starting to handle beacon connection " + connection);
			}
			/*
			 * TODO: add this to documentation: 
			 * The beacon message protocol is as follows: 
			 *  1. The connecting side retrieves our state.
			 *  2. After that it sends us it's state which we will retrieve.
			 *  3. The connection closes
			 */
			
			// Send them our state
			BeaconMessage ourMessage = currentBeaconMessage;
			try {
				connection.write(ourMessage.toBytes());
			} catch (IOException e) {
				if(Log.logErrorMessages()) {
					Log.e(LOG_TAG, "Failed to send message to connected beacon client. Closing connection");
				}
				connection.disconnect();
				return;
			}
			// Get their state
			BeaconMessage theirMessage = BeaconMessage.fromBlaubotConnection(connection);
			connection.disconnect();
			
			// propagate events
			if(theirMessage != null) {
				for(IBlaubotBeaconInterface beacon: blaubotBeacons) {
					
					IBlaubotDevice remoteDevice = connection.getRemoteDevice();
					State theirState = theirMessage.getCurrentState();
					AbstractBlaubotDeviceDiscoveryEvent discoveryEvent = theirState.createDiscoveryEventForDevice(remoteDevice);
					beacon.onDeviceDiscovered(discoveryEvent);

					if(theirMessage.getCurrentState() == State.Peasant || theirMessage.getCurrentState() == State.Prince) {
						// if they are peasant or prince they should have a king and sent us his uniqueDeviceId
						// generate another discovery event
						String kingDeviceUniqueId = theirMessage.getKingDeviceUniqueId();
						IBlaubotDevice kingDevice = remoteDevice.getAdapter().getConnector().createRemoteDevice(kingDeviceUniqueId);
						if(kingDevice == null) {
							if(Log.logWarningMessages()) {
								Log.w(LOG_TAG, "Connector was not able to create a IBlaubotDevice for the uniqueId " + kingDeviceUniqueId);
							}
							continue; // not able to connect to this king
						}
						AbstractBlaubotDeviceDiscoveryEvent kingDiscoveryEvent = State.King.createDiscoveryEventForDevice(kingDevice);
						beacon.onDeviceDiscovered(kingDiscoveryEvent);
					}
				}
			}
			connection.disconnect();
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Done handling beacon connection " + connection);
			}
		}

	}

	/**
	 * Informs the BlaubotBeaconService that the blaubot connection state has changed.
	 * 
	 * @param newState
	 */
	public void onStateChanged(IBlaubotState newState) {
		// TODO: the listener wiring of the beacons to the state machine should be refactored to this place to decouple
		// them a little.
		// build the beacon message
		State state = State.getStateByStatemachineClass(newState.getClass());
		if (newState instanceof IBlaubotSubordinatedState) { // prince or peasant state
			currentBeaconMessage = new BeaconMessage(state, ((IBlaubotSubordinatedState) newState).getKingUniqueId());
		} else {
			currentBeaconMessage = new BeaconMessage(state);
		}
		
	}

	public List<IBlaubotBeaconInterface> getBeacons() {
		return blaubotBeacons;
	}

}
