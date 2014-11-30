package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

/**
 * Abstract class for generic timeout events used in {@link IBlaubotState} implementations.
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public abstract class AbstractTimeoutStateMachineEvent extends AbstractBlaubotStateMachineEvent {
	private IBlaubotState fromState;

	public AbstractTimeoutStateMachineEvent(IBlaubotState fromState) {
		this.fromState = fromState;
		this.setState(fromState);
	}

	/**
	 * The state that triggered the timeout
	 * @deprecated is now redundant to {@link #getState()}
	 * @return
	 */
	public IBlaubotState getFromState() {
		return fromState;
	}

}
