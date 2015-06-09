package eu.hgross.blaubot.core.acceptor.discovery;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.statemachine.ConnectionStateMachine;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;

/**
 * Beacon to expose the blaubot instance's state to other blaubot instances and
 * discovering purposes.
 * 
 * Discovers nearby devices and informs registered listeners about events.
 * The discovery can be started and stopped separately by setting setDiscoveryActivated(...)
 * to true. The discovery SHOULD only be active, if the beacon is started.
 * 
 * An implementation MUST start it's own accept thread to handle incoming connections
 * to the beacon and inform registered {@link IBlaubotIncomingConnectionListener} 
 * about this new connections (see {@link IBlaubotConnectionAcceptor}).
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotBeacon extends IBlaubotConnectionAcceptor {
    /**
     * Dependency injecton of the blaubot instance the beacon is working for.
     * It is guaranteed that this is called before any start/stop calls.
     * @param blaubot the blaubot instance
     */
    public void setBlaubot(Blaubot blaubot);

    /**
     * Setter method for the dependency injection of a beacon store.
     * It is guaranteed to be called before the beacon is started for
     * the first time.
     *
     * @param beaconStore the beacon store implementation
     */
    public void setBeaconStore(IBlaubotBeaconStore beaconStore);

	/**
	 * Sets the discovery event listener which gets called whenever another remote device's 
	 * state was discovered by this beacon or the {@link BlaubotBeaconService}.
	 * @param discoveryEventListener the listener to be set
	 */
	public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener);
	
	/**
	 * Called when the {@link ConnectionStateMachine}'s state has changed.
     * An implementation has to expose this state as soon as possible to compatible beacons.
	 * @param state the new state of the ConnectionStateMachine
	 */
	public void onConnectionStateMachineStateChanged(IBlaubotState state);
	
	/**
	 * Enables and disables the discovery of other (remote) blaubot devices.
	 * @param active if set to true, the discovery (if running) will be active - inactive otherwise
	 */
	public void setDiscoveryActivated(boolean active);
}
