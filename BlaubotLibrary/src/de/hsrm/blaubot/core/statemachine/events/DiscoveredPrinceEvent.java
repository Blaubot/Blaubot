package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.IBlaubotDevice;

public class DiscoveredPrinceEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredPrinceEvent(IBlaubotDevice d) {
		this.remoteDevice = d;
	}

	public String toString() {
		return "DiscoveredPrinceEvent["+remoteDevice+"]";
	}
}
