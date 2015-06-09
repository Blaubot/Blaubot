package eu.hgross.blaubot.core.statemachine.events;

import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.admin.AbstractAdminMessage;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class AdminMessageStateMachineEvent extends AbstractBlaubotStateMachineEvent {
	private final AbstractAdminMessage adminMessage;

	public AdminMessageStateMachineEvent(final IBlaubotState currentState, final AbstractAdminMessage adminMessage) {
		this.adminMessage = adminMessage;
		this.setConnectionStateMachineState(currentState);
	}

	public AbstractAdminMessage getAdminMessage() {
		return adminMessage;
	}

	@Override
	public String toString() {
		return "AdminMessageStateMachineEvent [adminMessage=" + adminMessage + "]";
	}
	
	
}
