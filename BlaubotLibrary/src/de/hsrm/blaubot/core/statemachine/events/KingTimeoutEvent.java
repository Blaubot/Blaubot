package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

/**
 * Used if a king timeout occurs (no peasants for a given amount of time)
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class KingTimeoutEvent extends AbstractTimeoutStateMachineEvent {

	public KingTimeoutEvent(IBlaubotState fromState) {
		super(fromState);
	}
	
}
