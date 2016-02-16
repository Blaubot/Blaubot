package eu.hgross.blaubot.core.statemachine;

import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.StoppedState;


/**
 * Listener to watch for the connection state machine's state changes.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotConnectionStateMachineListener {
	/**
	 * Gets called when the {@link ConnectionStateMachine}'s state changed to state.
	 * @param oldState the former state (can be null)
	 * @param newState the new state
	 */
	public void onStateChanged(IBlaubotState oldState, IBlaubotState newState);
	
	/**
	 * Gets called when the {@link ConnectionStateMachine} stopped.
	 * This means a state change to {@link StoppedState} and is a shorthand for
	 * public void onStateChange(IBlaubotState state) {
	 *     if(state instanceof StoppedState) {
	 *         // code
	 *     }
	 * }
	 */
	public void onStateMachineStopped();
	
	/**
	 * Gets called when the {@link ConnectionStateMachine} changes it's {@link IBlaubotState}
	 * from the {@link StoppedState} to another {@link IBlaubotState}.
	 */
	public void onStateMachineStarted();
	
}
