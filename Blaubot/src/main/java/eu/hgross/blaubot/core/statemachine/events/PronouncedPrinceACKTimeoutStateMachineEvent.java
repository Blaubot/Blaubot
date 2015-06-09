package eu.hgross.blaubot.core.statemachine.events;

import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;

public class PronouncedPrinceACKTimeoutStateMachineEvent extends AbstractTimeoutStateMachineEvent {
	public PronouncedPrinceACKTimeoutStateMachineEvent(IBlaubotState fromState) {
		super(fromState);
	}
}