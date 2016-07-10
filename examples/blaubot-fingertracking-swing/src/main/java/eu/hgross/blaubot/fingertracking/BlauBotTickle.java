package eu.hgross.blaubot.fingertracking;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import eu.hgross.blaubot.fingertracking.model.Finger;
import eu.hgross.blaubot.fingertracking.model.FingerMessage;

/**
 * Takes {@link FingerMessage}s to update a {@link FingerField}.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlauBotTickle {
	private final Gson gson = new Gson();
	private ConcurrentHashMap<String, FingerMessage> fingers;  // ClientUUID -> lastFingerMessage
	private FingerField fingerField;

	public BlauBotTickle(FingerField field) {
		this.fingers = new ConcurrentHashMap<String, FingerMessage>();
		this.fingerField = field;
	}

	/**
	 * Updates the {@link FingerField} with the contents of a serialized {@link FingerMessage}
	 * @param message a {@link FingerMessage}'s JSON-Representation as String
	 */
	public void onMessage(String message) {
		FingerMessage fm = gson.fromJson(message, FingerMessage.class);
		String uuid = fm.getClientUUID();
		fingers.put(uuid, fm);
		ArrayList<Finger> drawFingers = new ArrayList<Finger>();
		for(FingerMessage fm1 : fingers.values()) {
			for(int i=0;i<fm1.getFingers().length ; i++) {
				drawFingers.add(fm1.getFingers()[i]);
			}
		}
		fingerField.setFingers(drawFingers);
	}
	
	

}
