package de.hsrm.blaubot.core.statemachine.events;

import de.hsrm.blaubot.core.IBlaubotDevice;

public class DiscoveredKingEvent extends AbstractBlaubotDeviceDiscoveryEvent {
	public DiscoveredKingEvent(IBlaubotDevice d) {
		if(d == null)
			throw new NullPointerException();
		this.remoteDevice = d;
	}

	public String toString() {
		return "DiscoveredKingEvent["+remoteDevice+"]";
	}
}