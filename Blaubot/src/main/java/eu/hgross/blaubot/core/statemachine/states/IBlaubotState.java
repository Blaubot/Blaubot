package eu.hgross.blaubot.core.statemachine.states;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.ConnectionStateMachine;
import eu.hgross.blaubot.core.statemachine.StateMachineSession;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.events.AbstractTimeoutStateMachineEvent;
import eu.hgross.blaubot.admin.AbstractAdminMessage;

/**
 * A state representing one state of the {@link ConnectionStateMachine}.
 * 
 * A state may do it's setup in the handleState() method and wait for
 * events received from it's on...() methods.
 * These methods are partly equivalent to the {@link IBlaubotConnectionManagerListener} and 
 * {@link IBlaubotDiscoveryEventListener} methods.
 * 
 * After each call of one of the on...() methods, a {@link IBlaubotState} has
 * to be returned. If the {@link ConnectionStateMachine} should stay in
 * the current state (this), simply return this - the state will not be dismissed
 * then.
 * 
 * If a state is dismissed by the {@link ConnectionStateMachine}, the onDismiss()
 * method will be called to allow a state to clean up.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotState {
	/**
	 * Gets called if this state was set as currentState by the {@link ConnectionStateMachine}.
	 * 
	 * @param stateMachineSession a session object with access to the relevant {@link Blaubot} components.
	 */
	public void handleState(StateMachineSession stateMachineSession);
	
	/**
	 * Gets called when an {@link AbstractAdminMessage} was received.
	 *
	 * @param adminMessage
	 * @return
	 */
	public IBlaubotState onAdminMessage(AbstractAdminMessage adminMessage);
	
	/**
	 * Gets called if a {@link eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon} discovered a {@link IBlaubotDevice}.
	 * 
	 * Note:
	 * For each discoverable {@link IBlaubotState} a corresponding {@link AbstractBlaubotDeviceDiscoveryEvent}
	 * subclass can be found in the de.hsrm.blaubot.statemachine.events package.
	 * 
	 * @param discoveryEvent the discovery event.
	 * @return
	 */
	public IBlaubotState onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent);
	
	/**
	 * Gets called if a new connection from a remote {@link IBlaubotDevice} was established.
	 * 
	 * @param connection
	 * @return
	 */
	public IBlaubotState onConnectionEstablished(IBlaubotConnection connection);
	
	/**
	 * Gets called if a previously established connection from a remote {@link IBlaubotDevice}
	 * was closed due to an exception or intentionally.
	 * 
	 * @param connection
	 * @return
	 */
	public IBlaubotState onConnectionClosed(IBlaubotConnection connection);
	
	/**
	 * This is a helper for Timeout-Events needed for some {@link IBlaubotState}s.
	 * A {@link IBlaubotState} can subclass the {@link AbstractTimeoutStateMachineEvent}
	 * and push it to the {@link ConnectionStateMachine}.
	 * 
	 * A timeout event contains the state that issued the timeout so a {@link IBlaubotState}
	 * can check if the timeout event belongs to himself or ignore it when this is not the 
	 * case.
	 * 
	 * @param timeoutEvent
	 * @return
	 */
	public IBlaubotState onTimeoutEvent(AbstractTimeoutStateMachineEvent timeoutEvent);
}
