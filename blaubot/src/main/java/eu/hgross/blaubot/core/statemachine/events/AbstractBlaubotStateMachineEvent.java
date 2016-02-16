package eu.hgross.blaubot.core.statemachine.events;

import eu.hgross.blaubot.core.statemachine.ConnectionStateMachine;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;

/**
 * Base class for all events regarding the {@link ConnectionStateMachine}.
 * {@link AbstractBlaubotStateMachineEvent}s are lead through the {@link ConnectionStateMachine}s 
 * event queue where the {@link #setConnectionStateMachineState(IBlaubotState)} method will be
 * used to inject the current state.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public abstract class AbstractBlaubotStateMachineEvent {
	private IBlaubotState state = null;

	/**
	 * The {@link IBlaubotState} the {@link ConnectionStateMachine} was in when the
	 * {@link AbstractBlaubotStateMachineEvent} occured.
	 * @return The {@link IBlaubotState} the {@link ConnectionStateMachine} was in when the {@link AbstractBlaubotStateMachineEvent} occured or null, if the event did not pass the {@link ConnectionStateMachine}s event queue
	 */
	public IBlaubotState getConnectionStateMachineState() {
		return state;
	}
	
	/**
	 * Set the {@link IBlaubotState} the {@link ConnectionStateMachine} was in when the
	 * {@link AbstractBlaubotStateMachineEvent} occured.
	 * @param currentState
	 */
	public void setConnectionStateMachineState(IBlaubotState currentState) {
		this.state = currentState;
	}
	
}
