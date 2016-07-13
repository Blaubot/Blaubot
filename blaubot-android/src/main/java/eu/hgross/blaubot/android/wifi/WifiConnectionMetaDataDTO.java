package eu.hgross.blaubot.android.wifi;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.ethernet.EthernetConnectionMetaDataDTO;

/**
 * Used for the WifiAcceptor/Connector
 */
public class WifiConnectionMetaDataDTO extends EthernetConnectionMetaDataDTO {
    protected static final String ACCEPTOR_TYPE = BlaubotConstants.ACCEPTOR_TYPE_WIFI_AP;
    private static final String SSID_KEY = "SSID";
    private static final String PSK_KEY = "PSK";
    private static final String MAC_KEY = "MACADDR";

    public WifiConnectionMetaDataDTO(ConnectionMetaDataDTO connectionMetaDataDTO) {
        super();
        if(!connectionMetaDataDTO.getConnectionType().equals(ACCEPTOR_TYPE)) {
            throw new IllegalArgumentException("Acceptor types don't match: " + ACCEPTOR_TYPE + " != " + connectionMetaDataDTO.getConnectionType());
        }
        metaData = connectionMetaDataDTO.getMetaData();
    }

    public WifiConnectionMetaDataDTO(String ssid, String psk, String ipAddress, String macAddress, int acceptorPort) {
        super(ipAddress, acceptorPort);
        // override acceptor type
        setAcceptorType(ACCEPTOR_TYPE);

        // add wifi ssid and psk
        metaData.put(SSID_KEY, ssid);
        metaData.put(PSK_KEY, psk);
        metaData.put(MAC_KEY, macAddress);
    }

    /**
     * The ssid of the acceptors created access point
     * @return the ssid
     */
    public String getSsid() {
        return metaData.get(SSID_KEY);
    }

    /**
     * The psk to access the ssid
     * @return the pre-shared key
     */
    public String getPsk() {
        return metaData.get(PSK_KEY);
    }


    /**
     * The acceptor's device's mac address
     * @return the mac address
     */
    public String getMacAddress() {
        return metaData.get(MAC_KEY);
    }

}
