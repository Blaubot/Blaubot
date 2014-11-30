package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class StartStateMachineEvent extends AbstractBlaubotStateMachineEvent {
	public StartStateMachineEvent(IBlaubotState currentState) {
		setState(currentState);
	}

	public String toString() {
		return "StartStateMachineEvent";
	}
}
