package eu.hgross.blaubot.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;
import eu.hgross.blaubot.mock.BlaubotDeviceMock;

/**
 * Tests the Mock objects
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class MockObjectsTest {
	public static final int TEST_DATA_SIZE = 1000;

	@Test(timeout = 5000)
	public void testBlaubotConnectionQueueMock() throws SocketTimeoutException, IOException {
		BlaubotConnectionQueueMock conn1 = new BlaubotConnectionQueueMock(null);
		byte[] data = createRandomTestByteArray(TEST_DATA_SIZE);
		for (int i = 0; i < TEST_DATA_SIZE; i++) {
			conn1.write(data[i]);
			byte r = (byte) conn1.getInputStreamForWrittenConnectionData().read();
			assertEquals(data[i], r);
		}
	}

	@Test(timeout = 5000)
	public void testBlaubotConnectionQueueMockBorderCases() throws SocketTimeoutException, IOException {
		BlaubotConnectionQueueMock conn1 = new BlaubotConnectionQueueMock(null);
		byte[] data = new byte[] { 0, 1, -1, -128, 127};
		int length = data.length;
		byte[] buffer = new byte[length];
		conn1.write(data);
		int read = conn1.getInputStreamForWrittenConnectionData().read(buffer);
		assertTrue(length == read);
		assertArrayEquals(data, buffer);

		conn1 = new BlaubotConnectionQueueMock(null);
		BlaubotConnectionQueueMock conn2 = conn1.getOtherEndpointConnection(null);
		data = new byte[] { 0, 1, -1, -128, 127};
		length = data.length;
		buffer = new byte[length];
		conn1.write(data);
		conn2.readFully(buffer, 0, length);
		assertArrayEquals(data, buffer);
	}

	@Test(timeout = 5000)
	public void testBlaubotConnectionQueueMockByteArrays() throws SocketTimeoutException, IOException {
		BlaubotConnectionQueueMock conn1 = new BlaubotConnectionQueueMock(null);
		byte[] data = createRandomTestByteArray(TEST_DATA_SIZE);

		conn1.write(data);
		DataInputStream is = new DataInputStream(conn1.getInputStreamForWrittenConnectionData());

		byte[] read = new byte[TEST_DATA_SIZE];
		is.readFully(read, 0, TEST_DATA_SIZE);
		assertArrayEquals(data, read);
	}

	@Test
	public void testBlaubotConnectionQueueMockPair() throws SocketTimeoutException, IOException {
		BlaubotConnectionQueueMock conn1 = new BlaubotConnectionQueueMock(null);
		BlaubotConnectionQueueMock conn2 = conn1.getOtherEndpointConnection(null);

		// create 2 byte arrays with random content for conn1 and conn2 to write
		byte[] data1 = createRandomTestByteArray(TEST_DATA_SIZE);
		byte[] data2 = createRandomTestByteArray(TEST_DATA_SIZE);

		// write
		for (int i = 0; i < TEST_DATA_SIZE; i++) {
			conn1.write(data1[i]);
			conn2.write(data2[i]);
		}

		// read and check
		for (int i = 0; i < TEST_DATA_SIZE; i++) {
			// conn2 should read the contents that conn1 wrote
			byte expected = data1[i];
			byte read = (byte) conn2.read();
			assertEquals(expected, read);

			// conn1 should read the contents that conn2 wrote
			expected = data2[i];
			read = (byte) conn1.read();
			assertEquals(expected, read);
		}
	}

	@Test(timeout = 5000)
	/**
	 * Tests that mock objects throw IOExceptions if they are blocked via .readFully() and get closed 
	 */
	public void testThrowingExceptionsIfClosedOnReadFully() {
		final int timeout = 1000;
		final IBlaubotConnection conn = new BlaubotConnectionQueueMock(new BlaubotDeviceMock("aUniqueId"));
		byte[] buffer = new byte[20];
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(timeout);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				conn.disconnect();
			}
		}).start();

		final AtomicBoolean result = new AtomicBoolean(false);
		try {
			// blocks and should throw an exception after timeout ms
			conn.readFully(buffer);
		} catch (SocketTimeoutException e) {
		} catch (IOException e) {
			result.set(true);
		}
		assertTrue(result.get());
	}

	@Test(timeout = 5000)
	/**
	 * Tests that mock objects throw IOExceptions if write() is called if closed.
	 */
	public void testThrowingExceptionsIfClosedOnWrite() {
		final IBlaubotConnection conn = new BlaubotConnectionQueueMock(new BlaubotDeviceMock("aUniqueId"));
		byte[] buffer = new byte[20];
		conn.disconnect();

		final AtomicBoolean result = new AtomicBoolean(false);
		try {
			// blocks and should throw an exception after timeout ms
			conn.write(buffer);
		} catch (IOException e) {
			result.set(true);
		}
		assertTrue(result.get());
	}

	/**
	 * Creates a random byte array with CNT elements
	 * 
	 * @param CNT
	 * @return
	 */
	private byte[] createRandomTestByteArray(int CNT) {
		Random random = new Random();
		byte[] data = new byte[CNT];
		for (int i = 0; i < CNT; i++) {
			data[i] = (byte) random.nextInt(i + 1);
		}
		return data;
	}

}
