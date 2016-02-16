package eu.hgross.blaubot.core;

/**
 * Interface for IBlaubotDevice implementations used in Beacons to signal the
 * EthernetExchangeTask to set the right uniqueDevice ID once a bi-directional
 * connection was established and the uniqueId can be exchanged.
 *
 * This construct is needed because some technologies used for beacons don't have
 * the possibility to correlate between discovery and succeeded connections - like
 * WifiP2p does and their connect() and notify() via BroadcastReceivers do.
 * For this purpose, an IBlaubotConnection can be created with a IUnidentifiedBlaubotDevice
 * flagged IBlaubotDevice-Implementation and the uniqueId will be injected during
 * the state exchange.
 */
public interface IUnidentifiedBlaubotDevice extends IBlaubotDevice {
    /**
     * Sets the formerly unknown unique device id, see class doc.
     * @param uniqueDeviceId the uniqueDeviceId to be set
     */
    public void setUniqueDeviceId(String uniqueDeviceId);
}
