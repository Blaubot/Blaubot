package eu.hgross.blaubot.ethernet;

/**
 * Interface defining some common methods for Ethernet beacons to reuse components as the accept thread ...
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IEthernetBeacon {
	public Thread getAcceptThread();
    public int getBeaconPort();
}
