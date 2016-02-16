package eu.hgross.blaubot.core;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.concurrent.Semaphore;

import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Blaubot wide constants.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotConstants {
    /**
     * A monitor that can be used to avoid concurrent bluetooth operations due to some
     * some bad bluetooth adapters that will fail when stressed.
     */
    public static final Semaphore BLUETOOTH_ADAPTER_LOCK = new Semaphore(3);

	/**
	 * Charset used for Strings
	 */
	public static final Charset STRING_CHARSET = Charset.forName("UTF-8");

    /**
     * The default byte order used within all Blaubot serializations
     */
    public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    /**
     * Default channel id which is used, when no additional config was given
     */
    public static final short DEFAULT_CHANNEL_ID = (short) 0;

    /**
     * Current version of the message schema (used in every BlaubotMessage's header
     */
    public static final byte MESSAGE_SCHEMA_VERSION = (byte) 0;

    /**
     * Name of the bonjour path for blaubot beacons
     */
    public static final String BLAUBOT_BEACON_BONJOUR_SERVICE_NAME = "_blaubot._tcp.local.";

    /**
     * Max value of an unsigned short
     */
    public static final int USHORT_MAX_VALUE = 0xffff;
    
    /**
     * Maximum payload size for BlaubotMessages in bytes.
     * == (max unsigned short value) - full blaubot message header length.
     *
     * Every BlaubotMessage with bigger payload than this constant will be automatically chunked
     * by the MessageSenders and MessageReceivers.
     *
     * Because of the RelayMessages, we need to subtract a full header length in bytes from the
     * payload, because for BlaubotMessages wrapped by a RelayAdminMessage, the header will be
     * part of the payload.
     *
     * Additionally we need to subtract the AdminMessage header length which is one byte.
     */
    public static final int MAX_PAYLOAD_SIZE = USHORT_MAX_VALUE - BlaubotMessage.FULL_HEADER_LENGTH - AbstractAdminMessage.HEADER_LENGTH;

    /*
        Acceptor classifiers
     */
    public static final String ACCEPTOR_TYPE_RFCOMM_JSR82_BLUETOOTH = "JSR82Bluetooth_1.0";
    public static final String ACCEPTOR_TYPE_RFCOMM_ANDROID_BLUETOOTH = "AndroidBluetooth_1.0";
    public static final String ACCEPTOR_TYPE_SOCKET_TCP = "EthernetAcceptor_1.0";
    public static final String ACCEPTOR_TYPE_WIFI_AP = "WifiAcceptor_1.0";
    public static final String ACCEPTOR_TYPE_WEBSOCKET = "WebsocketAcceptor_1.0";

    /**
     * The service name used for all RFCOMM based acceptors
     */
    public static final String BLUETOOTH_ACCEPTORS_RFCOMM_SDP_SERVICE_NAME = "BlaubotBluetoothAcceptor";
}
