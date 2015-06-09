package eu.hgross.blaubot.android.wifip2p;

import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

/**
 * Created by henna on 16.02.15.
 */
public class WifiP2PConnectionMetaDataDTO extends ConnectionMetaDataDTO {
    private static final String TYPE = "WifiP2PAcceptor_1.0";

    public WifiP2PConnectionMetaDataDTO(String ipAddress, int acceptorPort) {
        super();
        getMetaData().put(CONNECTION_TYPE_KEY, TYPE);
    }
}
