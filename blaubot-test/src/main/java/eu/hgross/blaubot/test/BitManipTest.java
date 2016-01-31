package eu.hgross.blaubot.test;


import java.util.BitSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static eu.hgross.blaubot.util.Util.toFixedLengthByteArray;

// Checks bit manipulations, focus on toFixedLengthByteArray
public class BitManipTest {

	// sets all bits in a new bitset at positions in int field a
	private BitSet genBitSet(int[] bits) {
		BitSet bs = new BitSet();
		for (int bit : bits) {
			bs.set(bit);
		}
		return bs;
	}

	// checks whether exactly the bits in bits are set in a byte array a
	private void checkBits(byte[] a, int[] bits) {
		BitSet bs = BitSet.valueOf(a);
		assertEquals(bits.length, bs.cardinality());
		for (int bit : bits) {
			assertTrue(bs.get(bit));
		}
	}

	// runs one test of toFixedLengthByteArray
	private void checkToFixedLengthByteArray(int[] bits, int length) {
		BitSet bs = genBitSet(bits);
		byte[] a = toFixedLengthByteArray(bs, length);
		assertEquals(a.length, length);
		checkBits(a, bits);
	}
	
	@Test
	public void testToFixedByteArray_OneByteMessageInTwoBytes() {
		checkToFixedLengthByteArray(new int[]{4, 6}, 2);
	}
	
	@Test
	public void testToFixedByteArray_TwoByteMessageInTwoBytes() {
		checkToFixedLengthByteArray(new int[]{4, 6, 8}, 2);
		checkToFixedLengthByteArray(new int[]{4, 6, 15}, 2);
	}
	
	@Test
	public void testToFixedByteArray_ThreeByteMessageinTwoBytes() {
		int[] in = new int[] {0, 8, 16};
		int[] out = new int[] {0, 8}; // cut off
		BitSet bs = genBitSet(in);
		byte[] a = toFixedLengthByteArray(bs, 2);
		assertEquals(a.length, 2);
		checkBits(a, out);		
	}
	
	@Test
	public void testToFixedByteArray_WalkOne() {
		final int BYTES=1024;
		for (int i=0; i < BYTES*8; i++) {
			int[] bits = new int[]{ i };
			checkToFixedLengthByteArray(bits, BYTES);
		}
		for (int i=0; i < BYTES*8; i+=8) {
			int[] bits = new int[]{ i, i+2, i+4, i+6 };
			checkToFixedLengthByteArray(bits, BYTES);
		}
		for (int i=0; i < BYTES*8; i+=8) {
			int[] bits = new int[]{ i+1, i+3, i+5, i+7 };
			checkToFixedLengthByteArray(bits, BYTES);
		}
	}

}
