package eu.hgross.blaubot.mock;

import java.util.ArrayList;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;

/**
 * Mock object for beacons.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotBeaconMock implements IBlaubotBeacon {

	private IBlaubotListeningStateListener stateListener;
	private IBlaubotIncomingConnectionListener acceptorListener;
	private IBlaubotDiscoveryEventListener discoveryEventListener;
	private IBlaubotState currentState;

	private boolean listening = false;
	private boolean discoveryActive = true;
    private IBlaubotBeaconStore beaconStore;
    private Blaubot blaubot;

    @Override
    public IBlaubotAdapter getAdapter() {
        return null;
    }

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
    public ConnectionMetaDataDTO getConnectionMetaData() {
        return null;
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
			AbstractBlaubotDeviceDiscoveryEvent discoveryEvent = deviceState.createDiscoveryEventForDevice(device, new ArrayList<ConnectionMetaDataDTO>());
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
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }
}
