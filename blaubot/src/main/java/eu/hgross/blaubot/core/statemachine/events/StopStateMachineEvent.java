package eu.hgross.blaubot.core.statemachine.events;

import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;

/**
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class StopStateMachineEvent extends AbstractBlaubotStateMachineEvent {

	public StopStateMachineEvent(IBlaubotState currentState) {
		setConnectionStateMachineState(currentState);
	}

	public String toString() {
		return "StopStateMachineEvent";
	}
}
