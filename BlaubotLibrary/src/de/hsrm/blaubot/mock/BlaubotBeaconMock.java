package de.hsrm.blaubot.mock;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotListeningStateListener;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

/**
 * Mock object for beacons.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotBeaconMock implements IBlaubotBeaconInterface {

	private IBlaubotListeningStateListener stateListener;
	private IBlaubotIncomingConnectionListener acceptorListener;
	private IBlaubotDiscoveryEventListener discoveryEventListener;
	private IBlaubotState currentState;

	private boolean listening = false;
	private boolean discoveryActive = true;
	
	@Override
	public void startListening() {
		if(isStarted())
			return;
		listening = true;
		if(stateListener != null)
			stateListener.onListeningStarted(this);
	}

	@Override
	public void stopListening() {
		listening = false;
		if(stateListener != null)
			stateListener.onListeningStopped(this);
	}

	@Override
	public boolean isStarted() {
		return listening;
	}

	@Override
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
		this.stateListener = stateListener;
	}

	@Override
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
		this.acceptorListener = acceptorListener;
	}

	@Override
	public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
		this.discoveryEventListener = discoveryEventListener;
	}

	@Override
	public void onConnectionStateMachineStateChanged(IBlaubotState state) {
		this.currentState = state;
	}

	@Override
	public void setDiscoveryActivated(boolean active) {
		this.discoveryActive  = active;
	}

	/**
	 * Mock method to emulate a discovered state for a given device.
	 * @param device the device
	 * @param deviceState the discovered state
	 */
	public void mockDiscovered(IBlaubotDevice device, State deviceState) {
		if(this.discoveryEventListener != null) {
			AbstractBlaubotDeviceDiscoveryEvent discoveryEvent = deviceState.createDiscoveryEventForDevice(device);
			this.discoveryEventListener.onDeviceDiscoveryEvent(discoveryEvent);
		}
	}
	
	/**
	 * Mocks a new incoming connection to this beacon.
	 * @param connection the connection
	 */
	public void mockNewConnection(IBlaubotConnection connection) {
		if(acceptorListener != null) {
			acceptorListener.onConnectionEstablished(connection);
		}
	}

	@Override
	public void onDeviceDiscovered(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
		if(discoveryEventListener != null) {
			discoveryEventListener.onDeviceDiscoveryEvent(discoveryEvent);
		}
	}
}
