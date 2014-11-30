package de.hsrm.blaubot.core.acceptor.discovery;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import de.hsrm.blaubot.util.Log;

/**
 * A task that utilizes a given (and connected) {@link IBlaubotConnection} to exchange the given {@link State} with the
 * remote endpoint.
 * 
 * If the exchange is successfull, the {@link IBlaubotDiscoveryEventListener} is informed about the discovered state of
 * the remote endpoint.
 * 
 * After the execution the connection is closed.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class ExchangeStatesTask implements Runnable {
	private static final ExecutorService executorService = Executors.newCachedThreadPool();
	private static final String LOG_TAG = "ExchangeStatesTask";
	protected volatile IBlaubotDiscoveryEventListener eventListener;
	protected IBlaubotState ourState; 
	protected IBlaubotConnection connection;
	protected String kingDeviceUniqueId;

	public ExchangeStatesTask(IBlaubotConnection connection, IBlaubotState ourState, IBlaubotDiscoveryEventListener eventListener) {
		setUp(connection, ourState, eventListener);
		// assert 
		if(this.ourState instanceof IBlaubotSubordinatedState) {
			this.kingDeviceUniqueId = ((IBlaubotSubordinatedState)ourState).getKingUniqueId();
		}
	}
	
	private void setUp(IBlaubotConnection connection, IBlaubotState ourState, IBlaubotDiscoveryEventListener eventListener) {
		this.ourState = ourState;
		this.eventListener = eventListener;
		this.connection = connection;
	}
	
	private State getState() {
		if(ourState == null) {
			if(Log.logWarningMessages()) {
				Log.w(LOG_TAG, "ourState for the StateExchange is null! Assuming we are in StoppedState.");
			}
			return State.Stopped;
		}
		return State.getStateByStatemachineClass(ourState.getClass());
	}

	@Override
	public void run() {
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Connected to " + connection.getRemoteDevice() + "'s beacon.");
			Log.d(LOG_TAG, "Starting state exchange.");
		}
		BeaconMessage beaconMessage = null;
		beaconMessage = BeaconMessage.fromBlaubotConnection(connection);
		if (beaconMessage == null) {
			if(Log.logWarningMessages()) {
				Log.w(LOG_TAG, "Something went wrong reading the beacon message from the connection!");
			}
			connection.disconnect();
			return;
		}
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Successfully retrieved state from " + connection.getRemoteDevice() + "'s beacon: " + beaconMessage);
		}
		if (ourState != null) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Sending our state to " + connection.getRemoteDevice() + "'s beacon");
			}
			BeaconMessage ourStateMessage;
			if(ourState instanceof IBlaubotSubordinatedState) {
				// if we have a king, we communicate the king's uniqueId
				ourStateMessage = new BeaconMessage(getState(), kingDeviceUniqueId);
			} else {
				ourStateMessage = new BeaconMessage(getState());
			}
			try {
				connection.write(ourStateMessage.toBytes());
			} catch (IOException e) {
				if(Log.logErrorMessages()) {
					Log.e(LOG_TAG, "Failed to send our state to beacon of " + connection.getRemoteDevice(), e);
				}
			}
		}
		connection.disconnect();

		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Got message from " + connection.getRemoteDevice() + "'s beacon: " + beaconMessage);
		}

		handleDiscoveredBlaubotDevice(connection.getRemoteDevice(), beaconMessage.getCurrentState());
	}

	private void handleDiscoveredBlaubotDevice(final IBlaubotDevice device, final State state) {
		if (eventListener != null) {
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					AbstractBlaubotDeviceDiscoveryEvent event = state.createDiscoveryEventForDevice(device);
					if (eventListener != null) {
						eventListener.onDeviceDiscoveryEvent(event);
					}
				}
			});
		}
	};
}