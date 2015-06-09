package eu.hgross.blaubot.bluetooth;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;

/**
 * The acceptor meta data class for the generic bluetooth acceptor
 */
public class Jsr82BluetoothConnectionMetaDataDTO extends ConnectionMetaDataDTO {
    protected static final String ACCEPTOR_TYPE = BlaubotConstants.ACCEPTOR_TYPE_RFCOMM_JSR82_BLUETOOTH;
    private static final String MAC_ADDRESS_KEY = "MacAddr";

    public Jsr82BluetoothConnectionMetaDataDTO(ConnectionMetaDataDTO connectionMetaDataDTO) {
        super();
        //if(!connectionMetaDataDTO.getConnectionType().equals(ACCEPTOR_TYPE)) {
        //    throw new IllegalArgumentException("Acceptor types don't match");
        //}
        metaData = connectionMetaDataDTO.getMetaData();
    }

    public Jsr82BluetoothConnectionMetaDataDTO(String bluetoothMacAddress) {
        super();
        setAcceptorType(ACCEPTOR_TYPE);
        getMetaData().put(MAC_ADDRESS_KEY, bluetoothMacAddress);
    }

    /**
     * Get the mac address of the acceptor's device
     * @return the mac address
     */
    public String getMacAddress() {
        return getMetaData().get(MAC_ADDRESS_KEY);
    }

}
