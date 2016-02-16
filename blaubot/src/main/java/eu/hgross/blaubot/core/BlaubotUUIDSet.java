package eu.hgross.blaubot.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds all the needed UUIDs.
 * Beacon implementations are free to use a single beaconUUID or an UUID for each state.
 * A beacon implementation should use the method that best suits it's specific capabilities.
 * 
 * Note: If a beacon implementation uses the single beaconUUID retrievable via getBeaconUUID()
 *       it SHOULD not use the state specific UUIDs and vice versa.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotUUIDSet {
	private static final String BEACON_NAMESPACE_PREFIX = "de.hsrm.blaubot.beacon.";
	private final UUID appUUID;
	private final UUID beaconUUID;
	private final Map<State, UUID> stateToUUID; 
	private final Map<UUID, State>  UUIDtoState;
	
	/**
	 * @param appUUID
	 *            the app's base uuid to generate the uuids from
	 */
	public BlaubotUUIDSet(UUID appUUID) {
		this.appUUID = appUUID;
		this.beaconUUID = UUID.nameUUIDFromBytes((BEACON_NAMESPACE_PREFIX + appUUID.toString()).getBytes(BlaubotConstants.STRING_CHARSET));
		int numStates = State.values().length;
		stateToUUID = new HashMap<State, UUID>(numStates);
		UUIDtoState = new HashMap<UUID, State>(numStates);
		createUUIDsForStates();
	}

	private void createUUIDsForStates() {
		for(State state : State.values()) {
			UUID stateUUID = UUID.nameUUIDFromBytes((BEACON_NAMESPACE_PREFIX + appUUID.toString() + "." + state.toString()).getBytes(BlaubotConstants.STRING_CHARSET));
			stateToUUID.put(state, stateUUID);
			UUIDtoState.put(stateUUID, state);
		}
	}
	
	public static void main(String args[]) {
		UUID APP_UUID = UUID.fromString("8790ba22-47a6-4f1c-86db-a32d7b2b82ba");
		BlaubotUUIDSet s = new BlaubotUUIDSet(APP_UUID);
		System.out.println(s.appUUID + ", " + s.beaconUUID);

		UUID uuid1 = UUID.randomUUID();
		UUID uuid2 = UUID.randomUUID();
		System.out.println(UUID.nameUUIDFromBytes((BEACON_NAMESPACE_PREFIX + uuid1.toString()).getBytes(BlaubotConstants.STRING_CHARSET)).toString());
		System.out.println(UUID.nameUUIDFromBytes((BEACON_NAMESPACE_PREFIX + uuid2.toString()).getBytes(BlaubotConstants.STRING_CHARSET)).toString());

	}

	/**
	 * The UUID identifying the app
	 * @return
	 */
	public UUID getAppUUID() {
		return appUUID;
	}

	/**
	 * The beaconUUID. See this class' JavaDoc whether you should use this or a specific UUID (getStateSpecificUUID(...))
	 * @return the single beaconUUID
	 */
	public UUID getBeaconUUID() {
		return beaconUUID;
	}
	
	/**
	 * Retrieves the UUID encoding the {@link State} state.
	 * @param state the state 
	 * @return the UUID encoding state
	 */
	public UUID getStateSpecificUUID(State state) {
		return stateToUUID.get(state);
	}
	
	/**
	 * Retrieves the {@link State} encoded by the given {@link UUID} uuid.
	 * @param uuid the UUID encoding a {@link State}
	 * @return the state or null, if uuid does not encode any state of this application.
	 */
	public State getStateFromUUID(UUID uuid) {
		return UUIDtoState.get(uuid);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((appUUID == null) ? 0 : appUUID.hashCode());
		result = prime * result + ((beaconUUID == null) ? 0 : beaconUUID.hashCode());
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
		BlaubotUUIDSet other = (BlaubotUUIDSet) obj;
		if (appUUID == null) {
			if (other.appUUID != null)
				return false;
		} else if (!appUUID.equals(other.appUUID))
			return false;
		if (beaconUUID == null) {
			if (other.beaconUUID != null)
				return false;
		} else if (!beaconUUID.equals(other.beaconUUID))
			return false;
		return true;
	}

}
