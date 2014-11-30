package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class StopStateMachineEvent extends AbstractBlaubotStateMachineEvent {

	public StopStateMachineEvent(IBlaubotState currentState) {
		setState(currentState);
	}

	public String toString() {
		return "StopStateMachineEvent";
	}
}
