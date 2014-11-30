package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.IBlaubotDevice;

public class DiscoveredFreeEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredFreeEvent(IBlaubotDevice device) {
		this.remoteDevice = device;
	}

	public String toString() {
		return "DiscoveredFreeEvent["+remoteDevice+"]";
	}
}
