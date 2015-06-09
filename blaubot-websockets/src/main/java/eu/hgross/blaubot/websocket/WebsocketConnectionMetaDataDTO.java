package eu.hgross.blaubot.websocket;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

/**
 * The connection meta data for the Websocket acceptor
 */
public class WebsocketConnectionMetaDataDTO extends ConnectionMetaDataDTO {
    protected static final String ACCEPTOR_TYPE = BlaubotConstants.ACCEPTOR_TYPE_WEBSOCKET;
    private static final String URI = "URI";

    public WebsocketConnectionMetaDataDTO(ConnectionMetaDataDTO connectionMetaDataDTO) {
        super();
        if(!connectionMetaDataDTO.getConnectionType().equals(ACCEPTOR_TYPE)) {
            throw new IllegalArgumentException("Acceptor types don't match: " + ACCEPTOR_TYPE + " != " + connectionMetaDataDTO.getConnectionType());
        }
        metaData = connectionMetaDataDTO.getMetaData();
    }

    /**
     * @param host the hostname to connect to
     * @param path the path to connect the websocket with (with leading slash)
     * @param port the port
     */
    public WebsocketConnectionMetaDataDTO(String host, String path, int port) {
        super();
        setAcceptorType(ACCEPTOR_TYPE);
        getMetaData().put(URI, "ws://" + host + ":" + port + path);
    }

    /**
     * @return the uri string to connect to the acceptor including port, host, path and protocol (ws, wss)
     */
    public String getUri() {
        return metaData.get(URI);
    }
}
