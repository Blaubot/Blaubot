package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

public class PronouncedPrinceACKTimeoutStateMachineEvent extends AbstractTimeoutStateMachineEvent {
	public PronouncedPrinceACKTimeoutStateMachineEvent(IBlaubotState fromState) {
		super(fromState);
	}
}