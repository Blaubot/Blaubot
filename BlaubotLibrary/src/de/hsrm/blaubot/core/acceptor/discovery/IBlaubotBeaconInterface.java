package de.hsrm.blaubot.core.acceptor.discovery;

import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.statemachine.ConnectionStateMachine;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

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
 * @TODO: remove onDeviceDiscovered (see {@link ConnectionStateMachine} TODO)
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotBeaconInterface extends IBlaubotConnectionAcceptor {
	/**
	 * Sets the discovery event listener which gets called whenever another remote device's 
	 * state was discovered by this beacon or the {@link BlaubotBeaconService}.
	 * @param discoveryEventListener
	 */
	public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener);
	
	/**
	 * Called when the {@link ConnectionStateMachine}'s state has changed.
	 * @param state
	 */
	public void onConnectionStateMachineStateChanged(IBlaubotState state);
	
	/**
	 * Called from the {@link BlaubotBeaconService} if it got information from another remote device.
	 * 
	 * @TODO: check out if we can move this logic entirely to the {@link BlaubotBeaconService}
	 * 
	 * @param discoveryEvent the event containing the new remote device and it's last known state
	 */
	public void onDeviceDiscovered(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent);
	
	/**
	 * Enables and disables the discovery of other (remote) blaubot devices.
	 * @param active if set to true, the discovery (if running) will be active - inactive otherwise
	 */
	public void setDiscoveryActivated(boolean active);
}
