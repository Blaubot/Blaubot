package eu.hgross.blaubot.ethernet;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import eu.hgross.blaubot.core.connector.IBlaubotConnector;

/**
 * Helper methods to deal with the BlaubotEthernetFixedDeviceSetBeacon and the construction of it's
 * FixedDeviceSetBlaubotDevice device implementation from convenient config strings to be able to
 * create networks mainly for jUnit-Tests.
 */
public class FixedDeviceSetHelper {
    /**
     * Creates the fixed device set config {@link String} identifying a {@link eu.hgross.blaubot.ethernet.BlaubotEthernetFixedDeviceSetBeacon.FixedDeviceSetBlaubotDevice}.
     *
     * @param inetAddr the ip address
     * @param acceptorPort the acceptor's port
     * @param beaconPort the beacon's port
     * @return valid config string
     */
    public static String createFixedDeviceSetConfigString(InetAddress inetAddr, int acceptorPort, int beaconPort) {
        StringBuilder sb;
        sb = new StringBuilder();
        sb.append(inetAddr.getHostAddress());
        sb.append(FIXED_DEVICE_SET_CONFIG_STRING_SEPARATOR);
        sb.append(acceptorPort);
        sb.append(FIXED_DEVICE_SET_CONFIG_STRING_SEPARATOR);
        sb.append(beaconPort);
        return sb.toString();
    }
    private static String FIXED_DEVICE_SET_CONFIG_STRING_SEPARATOR = "###";


    /**
     * Retrieve the acceptorPort out of a fixed device set config string.
     *
     * @param configString extracts the acceptor port from an ethernet fixed device configString
     * @return the acceptorPort
     */
    public static int getAcceptorPortFromFixedDeviceConfigString(String configString) {
        String[] splitted = configString.split(FIXED_DEVICE_SET_CONFIG_STRING_SEPARATOR);
        return Integer.parseInt(splitted[1]);
    }

    /**
     * Retrieve the beaconPort out of a fixedDeviceConfigSring.
     *
     * @param fixedDeviceConfigSring extracts the beacon port from an ethernet fixed device config string
     * @return the beaconPort
     */
    public static int getBeaconPortFromFixedDeviceConfigString(String fixedDeviceConfigSring) {
        String[] splitted = fixedDeviceConfigSring.split(FIXED_DEVICE_SET_CONFIG_STRING_SEPARATOR);
        return Integer.parseInt(splitted[2]);
    }

    /**
     * Retrieve the InetAddress out of a fixedDeviceConfigSring.
     *
     * @param fixedDeviceConfigSring extracts the InetAddress from an ethernet fixedDeviceConfigSring
     * @return the beaconPort
     * @throws java.net.UnknownHostException
     */
    public static InetAddress getInetAddressFromConfigString(String fixedDeviceConfigSring) throws UnknownHostException {
        String[] splitted = fixedDeviceConfigSring.split(FIXED_DEVICE_SET_CONFIG_STRING_SEPARATOR);
        return InetAddress.getByName(splitted[0]);
    }

    /**
     * Retrieve the ipAddress out of a fixedDeviceConfigSring.
     *
     * @param fixedDeviceConfigSring extracts the ipaddress from an ethernet fixedDeviceConfigSring
     * @return the beaconPort
     * @throws UnknownHostException
     */
    public static String getIpAddressFromConfigString(String fixedDeviceConfigSring) {
        String[] splitted = fixedDeviceConfigSring.split(FIXED_DEVICE_SET_CONFIG_STRING_SEPARATOR);
        return splitted[0];
    }

    /**
     * Transforms a set of fixed device config {@link String}s into a {@link java.util.Set} of {@link eu.hgross.blaubot.core.IBlaubotDevice} instances.
     * The config string has to be of the form
     * @param fixedDevicesSet
     * @param connector
     * @return
     */
    public static Set<BlaubotEthernetFixedDeviceSetBeacon.FixedDeviceSetBlaubotDevice> createFixedDeviceSetInstances(Set<String> fixedDevicesSet, IBlaubotConnector connector) throws UnknownHostException {
        HashSet<BlaubotEthernetFixedDeviceSetBeacon.FixedDeviceSetBlaubotDevice> deviceInstances = new HashSet<>();
        for(String configString : fixedDevicesSet){
            InetAddress inetAddr = getInetAddressFromConfigString(configString);
            int beaconPort = getBeaconPortFromFixedDeviceConfigString(configString);
            BlaubotEthernetFixedDeviceSetBeacon.FixedDeviceSetBlaubotDevice device = new BlaubotEthernetFixedDeviceSetBeacon.FixedDeviceSetBlaubotDevice(configString, inetAddr, beaconPort);
            deviceInstances.add(device);
        }
        return deviceInstances;
    }

}
