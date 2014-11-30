package de.hsrm.blaubot.core.acceptor.discovery;

import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;

/**
 * Contains the onDeviceDiscoveryEvent callback which is called immediately after a device's state is discovered.

 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotDiscoveryEventListener {
	public void onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent);
}
