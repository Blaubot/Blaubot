package eu.hgross.blaubot.android.nfc;

import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

/**
 * Created by henna on 19.02.15.
 * TODO: useless?
 */
public class NFCConnectionMetaDataDTO extends ConnectionMetaDataDTO {
    public static final String ACCEPTOR_TYPE = "NFCBeam_1.0";

    public NFCConnectionMetaDataDTO(ConnectionMetaDataDTO connectionMetaDataDTO) {
        super();
        if(!connectionMetaDataDTO.getConnectionType().equals(ACCEPTOR_TYPE)) {
            throw new IllegalArgumentException("Acceptor types don't match");
        }
        metaData = connectionMetaDataDTO.getMetaData();
    }

    public NFCConnectionMetaDataDTO () {
        super();
        setAcceptorType(ACCEPTOR_TYPE);
    }
}
