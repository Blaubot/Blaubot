package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.IBlaubotDevice;

public abstract class AbstractBlaubotDeviceDiscoveryEvent extends AbstractBlaubotStateMachineEvent {
	protected IBlaubotDevice remoteDevice;
	
	public IBlaubotDevice getRemoteDevice() {
		return this.remoteDevice;
	}
	
}
