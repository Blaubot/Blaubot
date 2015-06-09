package eu.hgross.blaubot.admin;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Informs about devices and their roles (states) in the kingdom.
 *
 * TODO: use json for the map
 *
 * The status message is sent as one String in this form:
 * 		DeviceId|StateString;Device2Id|StateString; ...
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class CensusMessage extends AbstractAdminMessage {
	private volatile Map<String, State> deviceStates;
	
	public CensusMessage(Map<String, State> deviceStates) {
		super(CLASSIFIER_CENSUS_MESSAGE);
		this.deviceStates = deviceStates;
	}
	
	public CensusMessage(BlaubotMessage rawMessage) {
		super(rawMessage);
	}
	
	private static String mapToString(Map<String, State> deviceStates) {
		StringBuffer sb = new StringBuffer();
		
		for(Entry<String, State> entry : deviceStates.entrySet()) {
			sb.append(entry.getKey()); // uniqueId
			sb.append("|");
			sb.append(entry.getValue().name()); // state
			sb.append(";");
		}
		return new String(sb);
	}
	
	private static Map<String, State> stringToMap(String s) {
		Map<String, State> map = new HashMap<String, State>();
		for(String data : s.split(";")) {
			String[] k_v = data.split("\\|");
			if(k_v.length <= 1) 
				break;
			String deviceUniqueId = k_v[0], stateStr = k_v[1];
			map.put(deviceUniqueId, State.valueOf(stateStr));
		}
		return map;
	}
	
	public static void main(String args[]) {
		Map<String ,State> m = new HashMap<String, State>();
		m.put("test1", State.Peasant);
		m.put("test2", State.Prince);
		final CensusMessage cm = new CensusMessage(m);
		System.out.println(cm+"");
		System.out.println(m+"");
		System.out.println(mapToString(m));
		System.out.println(stringToMap(mapToString(m)));
		System.out.println(new CensusMessage(cm.toBlaubotMessage())+"");

        for(int i=0; i<10;i++) {
            final int il = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while(true) {
                        if(il % 2 == 0) {
                            for(Entry<String, State> e : cm.getDeviceStates().entrySet()) {
                                System.out.println(e);
                            };
                        } else {
                            System.out.println(cm.toString());
                        }
                    }
                }
            }).start();
        }
	}
	
	@Override
	protected byte[] payloadToBytes() {
		String strToSend = mapToString(deviceStates);
		return strToSend.getBytes(BlaubotConstants.STRING_CHARSET);
	}

	@Override
	protected void setUpFromBytes(ByteBuffer messagePayloadAsBytes) {
		byte[] stringBytes = new byte[messagePayloadAsBytes.remaining()];
		messagePayloadAsBytes.get(stringBytes);
		String readString = new String(stringBytes, BlaubotConstants.STRING_CHARSET);
		deviceStates = stringToMap(readString);
	}

	public Map<String, State> getDeviceStates() {
		return deviceStates;
	}
	
	/**
	 * Extracts the prince's unique id from the device state map (if possible)
	 * @return the prince's unique id string or null, if no prince
	 */
	public String extractPrinceUniqueId() {
		for(Entry<String, State> entry : deviceStates.entrySet()) {
			if(entry.getValue().equals(State.Prince)) {
				return entry.getKey();
			}
		}
		return null;
	}
	
	/**
	 * Extracts the king's unique id from the device state map
	 * @return the king's unique id or null, if the message was empty
	 */
	public String extractKingUniqueId() {
		for(Entry<String, State> entry : deviceStates.entrySet()) {
			if(entry.getValue().equals(State.King)) {
				return entry.getKey();
			}
		}
        if(deviceStates.size() > 0) {
            throw new IllegalStateException("A network needs to have exactly one king but no king was part of the census message: " + deviceStates);
        }
        return null;
	}

	@Override
	public String toString() {
		return "CensusMessage [deviceStates=" + deviceStates + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((deviceStates == null) ? 0 : deviceStates.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		CensusMessage other = (CensusMessage) obj;
		if (deviceStates == null) {
			if (other.deviceStates != null)
				return false;
		} else if (!deviceStates.equals(other.deviceStates))
			return false;
		return true;
	}
	
	
}
