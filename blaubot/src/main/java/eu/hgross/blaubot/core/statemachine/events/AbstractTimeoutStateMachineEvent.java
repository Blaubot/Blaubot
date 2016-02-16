package eu.hgross.blaubot.core.statemachine.events;

import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;

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
		this.setConnectionStateMachineState(fromState);
	}

	/**
	 * The state that triggered the timeout
	 * @deprecated is now redundant to {@link #getConnectionStateMachineState()}
	 * @return
	 */
	public IBlaubotState getFromState() {
		return fromState;
	}

}
