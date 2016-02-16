package eu.hgross.blaubot.core.acceptor.discovery;

import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;

/**
 * Contains the onDeviceDiscoveryEvent callback which is called immediately after a device's state is discovered.

 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotDiscoveryEventListener {
    /**
     * Gets called when a IBlaubotBeaconInterface implementation recognized another blaubot device.
     * @param discoveryEvent the event holding all information gathered by the beacon
     */
	public void onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent);
}
