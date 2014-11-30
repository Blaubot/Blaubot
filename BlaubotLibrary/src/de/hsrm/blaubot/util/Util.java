package de.hsrm.blaubot.util;

import java.util.BitSet;

public class Util {

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
