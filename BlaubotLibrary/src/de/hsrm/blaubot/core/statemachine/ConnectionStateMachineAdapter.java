package de.hsrm.blaubot.core.statemachine;

import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

/**
 * Adapter class for the {@link IBlaubotConnectionStateMachineListener}.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class ConnectionStateMachineAdapter implements IBlaubotConnectionStateMachineListener {

	@Override
	public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
	}

	@Override
	public void onStateMachineStopped() {
	}

	@Override
	public void onStateMachineStarted() {
	}

}
