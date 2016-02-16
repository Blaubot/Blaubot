package eu.hgross.blaubot.ethernet;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

/**
 * The metadata for connections over Ethernet (IP-Addresses)
 */
public class EthernetConnectionMetaDataDTO extends ConnectionMetaDataDTO {
    protected static final String ACCEPTOR_TYPE = BlaubotConstants.ACCEPTOR_TYPE_SOCKET_TCP;
    private static final String IP_ADDRESS_KEY = "IP_ADDR";
    private static final String ACCEPTOR_PORT_KEY = "ACCEPTOR_PORT";

    protected EthernetConnectionMetaDataDTO() {
        super();
    }

    public EthernetConnectionMetaDataDTO(ConnectionMetaDataDTO connectionMetaDataDTO) {
        super();
        if(!connectionMetaDataDTO.getConnectionType().equals(ACCEPTOR_TYPE)) {
            throw new IllegalArgumentException("Acceptor types don't match: " + ACCEPTOR_TYPE + " != " + connectionMetaDataDTO.getConnectionType());
        }
        metaData = connectionMetaDataDTO.getMetaData();
    }

    public EthernetConnectionMetaDataDTO(String ipAddress, int acceptorPort) {
        super();
        setAcceptorType(ACCEPTOR_TYPE);
        getMetaData().put(ACCEPTOR_PORT_KEY, acceptorPort+"");
        getMetaData().put(IP_ADDRESS_KEY, ipAddress);
    }

    /**
     * The IP-Address of this device
     * @return ip address
     */
    public String getIpAddress() {
        return getMetaData().get(IP_ADDRESS_KEY);
    }

    public String setIpAddress(String ipAddress) {
        return getMetaData().put(IP_ADDRESS_KEY, ipAddress);
    }

    /**
     * Get the acceptor port of this device, at which connectors will try to connect to
     * @return the acceptor port
     */
    public int getAcceptorPort() {
        return Integer.parseInt(getMetaData().get(ACCEPTOR_PORT_KEY));
    }

}
