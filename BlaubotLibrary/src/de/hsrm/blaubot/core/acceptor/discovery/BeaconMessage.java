package de.hsrm.blaubot.core.acceptor.discovery;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.util.Log;

/**
 * Represents a message exchanged when two devices connect via the beacon interface.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BeaconMessage implements Serializable {
	private static final String LOG_TAG = "BeaconMessage";
	private static final long serialVersionUID = 7447451131850355749L;
	private static final Charset STRING_CHARSET = Charset.forName("UTF-8");
	
	private State currentState;
	private String kingDeviceUniqueId = ""; // only set if currentState is State.Prince or State.Peasant

	public BeaconMessage(State currentState) {
		this.currentState = currentState;
		if(this.currentState == State.Peasant || this.currentState == State.Prince) {
			throw new RuntimeException("You have to provide the king's uniqueDeviceId for the Peasant and Prince states. Use the appropriate constructor.");
		}
	}
	
	public BeaconMessage(State currentState, String kingDeviceId) {
		this.currentState = currentState;
		if(this.kingDeviceUniqueId == null) {
			throw new NullPointerException();
		}
		this.kingDeviceUniqueId = kingDeviceId;
	}
	

	private BeaconMessage() {
	}

	public State getCurrentState() {
		return currentState;
	}

	public void setCurrentState(State currentState) {
		this.currentState = currentState;
	}

	public byte[] toBytes() {
		byte[] strBytes = currentState.name().getBytes(BlaubotConstants.STRING_CHARSET);
		byte[] deviceIdBytes = kingDeviceUniqueId.getBytes(BlaubotConstants.STRING_CHARSET);
		int stateString_length = strBytes.length;
		int deviceId_length = deviceIdBytes.length;
		ByteBuffer bb = ByteBuffer.allocate(8 + stateString_length + deviceId_length);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(stateString_length);
		bb.put(strBytes);
		bb.putInt(deviceId_length);
		bb.put(deviceIdBytes);
		bb.flip();
		return bb.array();
		
	}

	public static BeaconMessage fromBytes(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.BIG_ENDIAN);
		int stateString_length = bb.getInt();
		byte[] strBytes = new byte[stateString_length];
		bb.get(strBytes, 0, stateString_length);
		int deviceIdString_length = bb.getInt();
		byte[] deviceIdBytes = new byte[deviceIdString_length];
		bb.get(deviceIdBytes, 0, deviceIdString_length);
		
		BeaconMessage out = new BeaconMessage();
		out.currentState = State.valueOf(new String(strBytes, STRING_CHARSET));
		out.kingDeviceUniqueId = new String(deviceIdBytes, STRING_CHARSET);
		return out;
	}
	
	/**
	 * 
	 * @param connection
	 * @return message or null, if smthg went wrong
	 */
	public static BeaconMessage fromBlaubotConnection(IBlaubotConnection connection) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.BIG_ENDIAN);
		try {
			connection.readFully(bb.array(), 0, 4);
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to read length byte from beacon message. Closing connection", e);
			connection.disconnect();
			return null;
		}
		int l = bb.getInt();
		ByteBuffer bbMsg = ByteBuffer.allocate(l);
		bbMsg.order(ByteOrder.BIG_ENDIAN);
		try {
			connection.readFully(bbMsg.array(), 0, l);
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to read message bytes from beacon message. Closing connection", e);
			connection.disconnect();
			return null;
		}
		
		ByteBuffer bbId = ByteBuffer.allocate(4);
		bbId.order(ByteOrder.BIG_ENDIAN);
		try {
			connection.readFully(bbId.array(), 0, 4);
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to read length of uniqueIdString. Closing connection", e);
			connection.disconnect();
			return null;
		}
		
		int uniqueIdLength = bbId.getInt();
		ByteBuffer bbIdStr = ByteBuffer.allocate(uniqueIdLength);
		bbIdStr.order(ByteOrder.BIG_ENDIAN);
		try {
			connection.readFully(bbIdStr.array(), 0, uniqueIdLength);
		} catch (IOException e) {
			Log.e(LOG_TAG, "Failed to read length of uniqueIdString. Closing connection", e);
			connection.disconnect();
			return null;
		}
		
		ByteBuffer together = ByteBuffer.allocate(bb.capacity() + bbMsg.capacity() + bbId.capacity() + bbIdStr.capacity());
		together.order(ByteOrder.BIG_ENDIAN);
		together.put(bb.array()); 
		together.put(bbMsg.array());
		together.put(bbId.array());
		together.put(bbIdStr.array());
		together.flip();
		return BeaconMessage.fromBytes(together.array());
	}
	
	public String getKingDeviceUniqueId() {
		return kingDeviceUniqueId;
	}

	public static void main (String args[]) {
		BeaconMessage m = new BeaconMessage(State.Peasant, "blabla");
		System.out.println(""+ m);
		System.out.println(""+ fromBytes(m.toBytes()));
		
		
		m = new BeaconMessage(State.Free);
		System.out.println(""+ m);
		System.out.println(""+ fromBytes(m.toBytes()));
	}

	@Override
	public String toString() {
		return "BeaconMessage [currentState=" + currentState + ", kingDeviceUniqueId=" + kingDeviceUniqueId + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((currentState == null) ? 0 : currentState.hashCode());
		result = prime * result + ((kingDeviceUniqueId == null) ? 0 : kingDeviceUniqueId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BeaconMessage other = (BeaconMessage) obj;
		if (currentState != other.currentState)
			return false;
		if (kingDeviceUniqueId == null) {
			if (other.kingDeviceUniqueId != null)
				return false;
		} else if (!kingDeviceUniqueId.equals(other.kingDeviceUniqueId))
			return false;
		return true;
	}
	
	
	
}
