package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.IBlaubotDevice;

public class DiscoveredStoppedEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredStoppedEvent(IBlaubotDevice device) {
		this.remoteDevice = device;
	}

	public String toString() {
		return "DiscoveredStoppedEvent["+remoteDevice+"]";
	}
}
