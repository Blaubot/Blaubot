package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.statemachine.ConnectionStateMachine;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

/**
 * Base class for all events regarding the {@link ConnectionStateMachine}.
 * {@link AbstractBlaubotStateMachineEvent}s are lead through the {@link ConnectionStateMachine}s 
 * event queue where the {@link #setState(IBlaubotState)} method will be
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
	public IBlaubotState getState() {
		return state;
	}
	
	/**
	 * Set the {@link IBlaubotState} the {@link ConnectionStateMachine} was in when the
	 * {@link AbstractBlaubotStateMachineEvent} occured.
	 * @param currentState
	 */
	public void setState(IBlaubotState currentState) {
		this.state = currentState;
	}
	
}
