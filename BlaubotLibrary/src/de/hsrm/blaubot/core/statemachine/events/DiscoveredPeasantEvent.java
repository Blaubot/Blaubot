package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.IBlaubotDevice;

public class DiscoveredPeasantEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredPeasantEvent(IBlaubotDevice d) {
		this.remoteDevice = d;
	}

	public String toString() {
		return "DiscoveredPeasantEvent["+remoteDevice+"]";
	}
}
