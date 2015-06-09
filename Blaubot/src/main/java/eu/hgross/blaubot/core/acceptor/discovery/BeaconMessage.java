package eu.hgross.blaubot.core.acceptor.discovery;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.IUnidentifiedBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;
import eu.hgross.blaubot.util.Log;

/**
 * Represents a message exchanged when two devices connect via the beacon interface.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BeaconMessage implements Serializable {
	private static final String LOG_TAG = "BeaconMessage";
	private static final long serialVersionUID = 7447451131850355749L;
    private String ownUniqueDeviceId = "";

    private State currentState;
    private List<ConnectionMetaDataDTO> ownConnectionMetaDataList = new ArrayList<>();

    private String kingDeviceUniqueId = "";                                              // only set if currentState is State.Prince or State.Peasant
    private List<ConnectionMetaDataDTO> kingsConnectionMetaDataList = new ArrayList<>(); // only set if currentState is State.Prince or State.Peasant

    /**
     * Constructor for the beacon message in cases where we have no king or we are the king
     *
     * @param ownUniqueDeviceId the unique device id of the sending side's ownDevice
     * @param currentState our device's current state
     * @param ownConnectionMetaDataList our own connection meta data list of our acceptors
     */
    public BeaconMessage(String ownUniqueDeviceId, State currentState, List<ConnectionMetaDataDTO> ownConnectionMetaDataList) {
		this.ownUniqueDeviceId = ownUniqueDeviceId;
        this.currentState = currentState;
        this.ownConnectionMetaDataList = ownConnectionMetaDataList;
		if(this.currentState == State.Peasant || this.currentState == State.Prince) {
			throw new RuntimeException("You have to provide the king's uniqueDeviceId for the Peasant and Prince states. Use the appropriate constructor.");
		}
	}

    /**
     * Constructor for the beacon message in case we are a subordinate to a king and therefore have a connection to a king
     * 
     * @param ownUniqueDeviceId the unique device id of the sending side's ownDevice
     * @param currentState our device's current state
     * @param ownConnectionMetaDataList our own connection meta data list of our acceptors
     * @param kingDeviceId the king's uniqueDevice id
     * @param kingsConnectionMetaDataList the king's connection meta data list to propagate to other interested device
     */
	public BeaconMessage(String ownUniqueDeviceId, State currentState, List<ConnectionMetaDataDTO> ownConnectionMetaDataList, String kingDeviceId, List<ConnectionMetaDataDTO> kingsConnectionMetaDataList) {
        this.ownUniqueDeviceId = ownUniqueDeviceId;
        this.currentState = currentState;
        this.ownConnectionMetaDataList = ownConnectionMetaDataList;
		if(this.kingDeviceUniqueId == null || kingsConnectionMetaDataList == null) {
			throw new NullPointerException();
		}
        this.kingsConnectionMetaDataList = kingsConnectionMetaDataList;
		this.kingDeviceUniqueId = kingDeviceId;
	}

    /**
     * Only for internal use (de-serialization)
     */
	private BeaconMessage() {
	}

	public State getCurrentState() {
		return currentState;
	}

	public void setCurrentState(State currentState) {
		this.currentState = currentState;
	}

    /**
     * Get the byte representation of this message
     *
     * @return the byte array containing the message in the byte order of BlaubotConstants.STRING_CHARSET
     */
	public byte[] toBytes() {
        byte[] uniqueDeviceIdBytes = ownUniqueDeviceId.getBytes(BlaubotConstants.STRING_CHARSET);
		byte[] strBytes = currentState.name().getBytes(BlaubotConstants.STRING_CHARSET);
        byte[] connectionMetaDataListBytes = ConnectionMetaDataDTO.toJson(ownConnectionMetaDataList).getBytes(BlaubotConstants.STRING_CHARSET);
        byte[] deviceIdBytes = kingDeviceUniqueId.getBytes(BlaubotConstants.STRING_CHARSET);
        byte[] kingConnectionMetaDataListBytes = ConnectionMetaDataDTO.toJson(kingsConnectionMetaDataList).getBytes(BlaubotConstants.STRING_CHARSET);
        int uniqueDeviceId_length = uniqueDeviceIdBytes.length;
        int stateString_length = strBytes.length;
        int metadata_length = connectionMetaDataListBytes.length;
        int deviceId_length = deviceIdBytes.length;
        int kingMetadata_length = kingConnectionMetaDataListBytes.length;

        ByteBuffer bb = ByteBuffer.allocate(20 + uniqueDeviceId_length + stateString_length + metadata_length + deviceId_length + kingMetadata_length); // 5 ints + byte lengths
		bb.order(BlaubotConstants.BYTE_ORDER);

        // unique device id
        bb.putInt(uniqueDeviceId_length);
        bb.put(uniqueDeviceIdBytes);

        // state
        bb.putInt(stateString_length);
		bb.put(strBytes);

        // meta list
        bb.putInt(metadata_length);
        bb.put(connectionMetaDataListBytes);

        // king unique device id
        bb.putInt(deviceId_length);
        bb.put(deviceIdBytes);

        // king meta list
        bb.putInt(kingMetadata_length);
        bb.put(kingConnectionMetaDataListBytes);

		bb.flip();
		return bb.array();
		
	}

	public static BeaconMessage fromBytes(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(BlaubotConstants.BYTE_ORDER);

        int uniqueDeviceIdLength = bb.getInt();
        byte[] uniqueDeviceIdBytes = new byte[uniqueDeviceIdLength];
        bb.get(uniqueDeviceIdBytes, 0, uniqueDeviceIdLength);

        int stateString_length = bb.getInt();
		byte[] strBytes = new byte[stateString_length];
		bb.get(strBytes, 0, stateString_length);

        int metaDataList_length = bb.getInt();
        byte[] metaDataListBytes = new byte[metaDataList_length];
        bb.get(metaDataListBytes, 0, metaDataList_length);

        int deviceIdString_length = bb.getInt();
		byte[] deviceIdBytes = new byte[deviceIdString_length];
		bb.get(deviceIdBytes, 0, deviceIdString_length);

        int king_metaDataList_length = bb.getInt();
        byte[] king_metaDataListBytes = new byte[king_metaDataList_length];
        bb.get(king_metaDataListBytes, 0, king_metaDataList_length);
		
		BeaconMessage out = new BeaconMessage();
        out.ownUniqueDeviceId = new String(uniqueDeviceIdBytes, BlaubotConstants.STRING_CHARSET);
		out.currentState = State.valueOf(new String(strBytes, BlaubotConstants.STRING_CHARSET));
        out.ownConnectionMetaDataList = ConnectionMetaDataDTO.fromJson(new String(metaDataListBytes, BlaubotConstants.STRING_CHARSET));
		out.kingDeviceUniqueId = new String(deviceIdBytes, BlaubotConstants.STRING_CHARSET);
        out.kingsConnectionMetaDataList = ConnectionMetaDataDTO.fromJson(new String(king_metaDataListBytes, BlaubotConstants.STRING_CHARSET));
		return out;
	}
	
	/**
     * Create the message from the stream of a blaubot connection.
     * Note: The connection will be closed via disconnect() on IO errors.
	 * @param connection
	 * @return message or null, if smthg went wrong
	 */
	public static BeaconMessage fromBlaubotConnection(IBlaubotConnection connection) {
		/*
		    Read STATE
		 */
        // unique device id
        ByteBuffer bbUniqueDeviceIdLength = ByteBuffer.allocate(4);
        bbUniqueDeviceIdLength.order(BlaubotConstants.BYTE_ORDER);
        try {
            connection.readFully(bbUniqueDeviceIdLength.array(), 0, 4);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read length byte for unique device idfrom beacon message. Closing connection", e);
            connection.disconnect();
            return null;
        }
        int uniqueDeviceIdLength = bbUniqueDeviceIdLength.getInt();

        // state bytes
        ByteBuffer bbUniqueDeviceId = ByteBuffer.allocate(uniqueDeviceIdLength);
        bbUniqueDeviceId.order(BlaubotConstants.BYTE_ORDER);
        try {
            connection.readFully(bbUniqueDeviceId.array(), 0, uniqueDeviceIdLength);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read message bytes for unique device id from beacon message. Closing connection", e);
            connection.disconnect();
            return null;
        }


        final IBlaubotDevice remoteDevice = connection.getRemoteDevice();
        if(remoteDevice instanceof IUnidentifiedBlaubotDevice) {
            // we need to inject the unique id, because the beacon had no chance to get onto it
            // see IUnidentifiedBlaubotDevice JavaDoc.
            // deserialize
            byte[] uidBytes = new byte[uniqueDeviceIdLength];
            bbUniqueDeviceId.get(uidBytes, 0, uniqueDeviceIdLength);
            bbUniqueDeviceId.flip();
            final String uniqueDeviceIdStr = new String(uidBytes, BlaubotConstants.STRING_CHARSET);
            // finally set
            ((IUnidentifiedBlaubotDevice) remoteDevice).setUniqueDeviceId(uniqueDeviceIdStr);
        }

		// state length
        ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(BlaubotConstants.BYTE_ORDER);
		try {
			connection.readFully(bb.array(), 0, 4);
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to read length byte for state from beacon message. Closing connection", e);
			connection.disconnect();
			return null;
		}
		int l = bb.getInt();

        // state bytes
		ByteBuffer bbMsg = ByteBuffer.allocate(l);
		bbMsg.order(BlaubotConstants.BYTE_ORDER);
		try {
			connection.readFully(bbMsg.array(), 0, l);
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to read message bytes for state from beacon message. Closing connection", e);
			connection.disconnect();
			return null;
		}

        /*
            Read LIST
         */
        // connection meta data length
        ByteBuffer bbMeta = ByteBuffer.allocate(4);
        bbMeta.order(BlaubotConstants.BYTE_ORDER);
        try {
            connection.readFully(bbMeta.array(), 0, 4);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read length of meta data list. Closing connection", e);
            connection.disconnect();
            return null;
        }

        // connection meta data  bytes
        int metaLength = bbMeta.getInt();
        ByteBuffer bbMetaStr = ByteBuffer.allocate(metaLength);
        bbMetaStr.order(BlaubotConstants.BYTE_ORDER);
        try {
            connection.readFully(bbMetaStr.array(), 0, metaLength);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read bytes of meta data list. Closing connection", e);
            connection.disconnect();
            return null;
        }


        /*
            Read king's UNIQUE ID
         */
        // uniqueId length
		ByteBuffer bbId = ByteBuffer.allocate(4);
		bbId.order(BlaubotConstants.BYTE_ORDER);
		try {
			connection.readFully(bbId.array(), 0, 4);
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to read length of uniqueIdString. Closing connection", e);
			connection.disconnect();
			return null;
		}

        // uniqueId bytes
		int uniqueIdLength = bbId.getInt();
		ByteBuffer bbIdStr = ByteBuffer.allocate(uniqueIdLength);
		bbIdStr.order(BlaubotConstants.BYTE_ORDER);
		try {
			connection.readFully(bbIdStr.array(), 0, uniqueIdLength);
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to read length of uniqueIdString. Closing connection", e);
			connection.disconnect();
			return null;
		}

        /*
            Read KINGS META LIST
         */
        // king's connection meta data length
        ByteBuffer bbKingList = ByteBuffer.allocate(4);
        bbKingList.order(BlaubotConstants.BYTE_ORDER);
        try {
            connection.readFully(bbKingList.array(), 0, 4);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read length of the king's meta data list. Closing connection", e);
            connection.disconnect();
            return null;
        }

        // king's connection meta data  bytes
        int kingMetaListLength = bbKingList.getInt();
        ByteBuffer bbKingMetaStr = ByteBuffer.allocate(kingMetaListLength);
        bbKingMetaStr.order(BlaubotConstants.BYTE_ORDER);
        try {
            connection.readFully(bbKingMetaStr.array(), 0, kingMetaListLength);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read bytes of the king's meta data list. Closing connection", e);
            connection.disconnect();
            return null;
        }

        // Combine all into one array
		ByteBuffer together = ByteBuffer.allocate(bbUniqueDeviceId.capacity() + bbUniqueDeviceIdLength.capacity() + bb.capacity() + bbMsg.capacity() + bbMeta.capacity() + bbMetaStr.capacity() + bbId.capacity() + bbIdStr.capacity() + bbKingList.capacity() + bbKingMetaStr.capacity());
		together.order(BlaubotConstants.BYTE_ORDER);

        // unique device id
        together.put(bbUniqueDeviceIdLength.array());
        together.put(bbUniqueDeviceId.array());

		// state
        together.put(bb.array());
		together.put(bbMsg.array());

        // meta data list
        together.put(bbMeta.array());
        together.put(bbMetaStr.array());

		// unique id
        together.put(bbId.array());
		together.put(bbIdStr.array());

        //king's meta data list
        together.put(bbKingList.array());
        together.put(bbKingMetaStr.array());

		together.flip();
		return BeaconMessage.fromBytes(together.array());
	}

    /**
     * Getter for the unique device id of the sending side
     * @return the unique id
     */
    public String getUniqueDeviceId() {
        return ownUniqueDeviceId;
    }

    /**
     * Note: is only set if currentState is State.Prince or State.Peasant
     * @return the king's uniqueDeviceId or null, if currentState not in (State.Prince, State.Peasant)
     */
	public String getKingDeviceUniqueId() {
		return kingDeviceUniqueId;
	}

    /**
     * The acceptor meta data for the device for which we received the state
     * @return the meta data for the remote device's acceptors
     */
    public List<ConnectionMetaDataDTO> getOwnConnectionMetaDataList() {
        return ownConnectionMetaDataList;
    }

    /**
     * The acceptor meta data for the device's for which we received the state
     * @return the remote device's king's acceptor meta data or null, if it has no king
     */
    public List<ConnectionMetaDataDTO> getKingsConnectionMetaDataList() {
        return kingsConnectionMetaDataList;
    }

    public static void main (String args[]) throws IOException {
        final ArrayList<ConnectionMetaDataDTO> ownConnectionMetaDataList = new ArrayList<>();
        final ArrayList<ConnectionMetaDataDTO> kingsConnectionMetaDataList = new ArrayList<>();
        final ConnectionMetaDataDTO ownDto = new ConnectionMetaDataDTO();
        ownDto.getMetaData().put(UUID.randomUUID().toString(), "1");
        ownConnectionMetaDataList.add(ownDto);
        final ConnectionMetaDataDTO kingDto = new ConnectionMetaDataDTO();
        kingDto.getMetaData().put(UUID.randomUUID().toString(), "2");
        kingsConnectionMetaDataList.add(kingDto);


        IBlaubotDevice ownDevice = new BlaubotDevice("myDeviceId");
        BeaconMessage m = new BeaconMessage(ownDevice.getUniqueDeviceID(), State.Peasant, ownConnectionMetaDataList, "blabla", kingsConnectionMetaDataList);
		System.out.println(""+ m);
		System.out.println(""+ fromBytes(m.toBytes()));

        System.out.println("\n");


        m = new BeaconMessage(ownDevice.getUniqueDeviceID(), State.Free, ownConnectionMetaDataList);
		System.out.println(""+ m);
		System.out.println(""+ fromBytes(m.toBytes()));

        System.out.println("\n");


        BlaubotConnectionQueueMock connection1 = new BlaubotConnectionQueueMock(new BlaubotDevice(UUID.randomUUID().toString()));
        BlaubotConnectionQueueMock connection2 = connection1.getOtherEndpointConnection(new BlaubotDevice(UUID.randomUUID().toString()));

        connection1.write(m.toBytes());
        BeaconMessage m_de = BeaconMessage.fromBlaubotConnection(connection2);
        System.out.println(""+m);
        System.out.println(""+m_de);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BeaconMessage{");
        sb.append("currentState=").append(currentState);
        sb.append(", ownConnectionMetaDataList=").append(ownConnectionMetaDataList);
        sb.append(", kingDeviceUniqueId='").append(kingDeviceUniqueId).append('\'');
        sb.append(", kingsConnectionMetaDataList=").append(kingsConnectionMetaDataList);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BeaconMessage that = (BeaconMessage) o;

        if (currentState != that.currentState) return false;
        if (kingDeviceUniqueId != null ? !kingDeviceUniqueId.equals(that.kingDeviceUniqueId) : that.kingDeviceUniqueId != null)
            return false;
        if (kingsConnectionMetaDataList != null ? !kingsConnectionMetaDataList.equals(that.kingsConnectionMetaDataList) : that.kingsConnectionMetaDataList != null)
            return false;
        if (ownConnectionMetaDataList != null ? !ownConnectionMetaDataList.equals(that.ownConnectionMetaDataList) : that.ownConnectionMetaDataList != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = currentState != null ? currentState.hashCode() : 0;
        result = 31 * result + (ownConnectionMetaDataList != null ? ownConnectionMetaDataList.hashCode() : 0);
        result = 31 * result + (kingDeviceUniqueId != null ? kingDeviceUniqueId.hashCode() : 0);
        result = 31 * result + (kingsConnectionMetaDataList != null ? kingsConnectionMetaDataList.hashCode() : 0);
        return result;
    }
}
