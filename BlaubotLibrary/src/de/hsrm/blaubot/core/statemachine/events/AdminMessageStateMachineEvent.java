package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class AdminMessageStateMachineEvent extends AbstractBlaubotStateMachineEvent {
	private AbstractAdminMessage adminMessage;

	public AdminMessageStateMachineEvent(IBlaubotState currentState, AbstractAdminMessage adminMessage) {
		this.adminMessage = adminMessage;
		this.setState(currentState);
	}

	public AbstractAdminMessage getAdminMessage() {
		return adminMessage;
	}

	@Override
	public String toString() {
		return "AdminMessageStateMachineEvent [adminMessage=" + adminMessage + "]";
	}
	
	
}
