package eu.hgross.blaubot.core.statemachine.events;

import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class StartStateMachineEvent extends AbstractBlaubotStateMachineEvent {
	public StartStateMachineEvent(IBlaubotState currentState) {
		setConnectionStateMachineState(currentState);
	}

	public String toString() {
		return "StartStateMachineEvent";
	}
}
