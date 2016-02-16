package eu.hgross.blaubot.util;

import java.util.BitSet;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import eu.hgross.blaubot.core.statemachine.states.KingState;

/**
 * Some common utility methods
 */
public class Util {
    /**
     * Extracts the king's uniqueDevice id from the state.
     * @param state must be a {@link eu.hgross.blaubot.core.statemachine.states.IBlaubotSubordinatedState} or
     *                 {@link eu.hgross.blaubot.core.statemachine.states.KingState}
     * @param ownDevice the own device
     * @return the king's uniqueDevice id
     */
    public static String extractKingUniqueDeviceIdFromState(IBlaubotState state, IBlaubotDevice ownDevice) {
        final String oldKingUniqueId;
        if (state instanceof IBlaubotSubordinatedState) {
            oldKingUniqueId = ((IBlaubotSubordinatedState) state).getKingUniqueId();
        } else if (state instanceof KingState) {
            oldKingUniqueId = ownDevice.getUniqueDeviceID();
        } else {
            throw new IllegalStateException("state should be IBlaubotSubordinateState or KingState");
        }
        if (oldKingUniqueId == null) {
            throw new NullPointerException("Unique id of the former kingdom's king could not be determined.");
        }
        return oldKingUniqueId;
    }

	/**
	 * Converts a BitSet to a byte[] including length bytes (length*8 bits). 
	 * May temporarily create a BitSet to force sufficient length.
	 * @param bs BitSet, to convert to a byte array
	 * @param length int, length of generated array
	 * @return byte[] of length 
	 */
	public static byte[] toFixedLengthByteArray(BitSet bs, int length) {
		BitSet prefix = bs.get(0, length*8);
		BitSet lastByte = prefix.get((length-1)*8, length*8);
		if (lastByte.cardinality() > 0) { // happy path, last bit set
			return prefix.toByteArray();
		} else { // last bit zero, need to fiddle
			prefix.set(length*8-1);
			byte[] a = prefix.toByteArray();
			a[a.length-1] = 0;
			return a;
		}
	}
}
