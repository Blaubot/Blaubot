package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.BeforeClass;
import org.junit.Test;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.mock.BlaubotConnectionQueueMock;
import de.hsrm.blaubot.mock.BlaubotDeviceMock;
import de.hsrm.blaubot.protocol.handshake.IProtocolHandshakeListener;
import de.hsrm.blaubot.protocol.handshake.ProtocolHandshakeAckMessage;
import de.hsrm.blaubot.protocol.handshake.ProtocolHandshakeClientTask;
import de.hsrm.blaubot.protocol.handshake.ProtocolHandshakeMasterTask;
import de.hsrm.blaubot.protocol.handshake.ProtocolHandshakeMessage;
import de.hsrm.blaubot.protocol.master.ProtocolMaster;
import de.hsrm.blaubot.util.Log;
import de.hsrm.blaubot.util.Log.LogLevel;

public class ProtocolHandshakeTest {

	private static final long TIMEOUT_FOR_ON_FAILURE_EVENTS = ProtocolMaster.HANDSHAKE_CALLBACK_TIMEOUT;
	private static IBlaubotDevice mockDevice1;
	private static IBlaubotDevice mockDevice2;
	
	@BeforeClass
	public static void beforeClass() {
		Log.LOG_LEVEL = LogLevel.DEBUG;
		mockDevice1 = new BlaubotDeviceMock("DeviceMock1UUID");
		mockDevice2 = new BlaubotDeviceMock("DeviceMock2UUID");
	}
	
	
	/**
	 * Tests handshake and follow up data.
	 * 
	 * 1.  Executes the handshake
	 * 2.  Sends data from the client to the server
	 * 2.1 asserts that this data is equal to the sent data
	 * 3.  Sends data from the server to the client
	 * 3.1 asserts that this data is euqal to the sent data
	 * 
	 * @throws InterruptedException
	 * @throws IOException 
	 * @throws SocketTimeoutException 
	 */
	@Test(timeout = 5000)
	public void testHandshakeFollowingData() throws InterruptedException, SocketTimeoutException, IOException {
		final CountDownLatch finishedLatch = new CountDownLatch(1);
		final AtomicBoolean result = new AtomicBoolean(false);
		final short deviceId = (short) 1;

		// test the handshake
		IProtocolHandshakeListener handshakeListener = new IProtocolHandshakeListener() {
			@Override
			public void onSuccess(short shortDeviceId) {
				assertEquals(deviceId, shortDeviceId);
				result.set(true);
				finishedLatch.countDown();
			}

			@Override
			public void onFailure(IBlaubotConnection connection) {
				result.set(false);
				finishedLatch.countDown();
			}
		};

		// build a pair of mocked connections
		BlaubotConnectionQueueMock conn1 = new BlaubotConnectionQueueMock(mockDevice1);
		BlaubotConnectionQueueMock conn2 = conn1.getOtherEndpointConnection(mockDevice2);

		// let the hands shake
		ProtocolHandshakeMasterTask masterTask = new ProtocolHandshakeMasterTask(deviceId, conn2, handshakeListener);
		ProtocolHandshakeClientTask clientTask = new ProtocolHandshakeClientTask(conn1, handshakeListener);
		masterTask.execute();
		clientTask.execute();

		boolean timeoutOccured = !finishedLatch.await(1000, TimeUnit.MILLISECONDS);
		if (timeoutOccured) {
			fail("Timeout occured while waiting for the handshake to complete.");
		}
		assertTrue(result.get());
		
		// Send data client -> master
		// Create data
		byte[] sentBytes = createRandomTestByteArray(200);
		byte[] readBytes = new byte[sentBytes.length];
		conn1.write(sentBytes);
		conn2.readFully(readBytes);
		assertByteArrayEquals(sentBytes, readBytes);
		
		// master -> client
		readBytes = new byte[sentBytes.length];
		conn2.write(sentBytes);
		conn1.readFully(readBytes);
		assertByteArrayEquals(sentBytes, readBytes);
	}
	
	private void assertByteArrayEquals(byte[] a1, byte[] a2) {
		int length = a1.length;
		for (int i=0;i<length;i++) {
			assertEquals(a1[i], a2[i]);
		}
	}
	
	/**
	 * Tests the serialization and deserialization of the Handshake messages. 
	 */
	@Test(timeout = 5000)
	public void testMessageSerialization() {
		for (short i = 0; i < 1000; i++) {
			// ProtocolHandshakeMsg
			ProtocolHandshakeMessage msg = new ProtocolHandshakeMessage((short) i);
			byte[] serialized = msg.toBytes();
			ProtocolHandshakeMessage msgDeserialized = ProtocolHandshakeMessage.fromBytes(serialized);
			assertEquals(msg, msgDeserialized);
			assertEquals(msg.getShortDeviceId(), msgDeserialized.getShortDeviceId());

			// ProtocolHandshakeAckMessage
			ProtocolHandshakeAckMessage ackMsg = new ProtocolHandshakeAckMessage((short) i);
			serialized = msg.toBytes();
			ProtocolHandshakeAckMessage ackMsgDeserialized = ProtocolHandshakeAckMessage.fromBytes(serialized);
			assertEquals(ackMsg, ackMsgDeserialized);
			assertEquals(ackMsg.getShortDeviceId(), ackMsgDeserialized.getShortDeviceId());
		}
	}

	/**
	 * Tests the actual handshake using connection mock ups.
	 * @throws InterruptedException
	 */
	@Test(timeout = 5000)
	public void testHandshakeTasks() throws InterruptedException {
		final CountDownLatch finishedLatch = new CountDownLatch(1);
		final AtomicBoolean result = new AtomicBoolean(false);
		final short deviceId = (short) 1;

		// test the handshake
		IProtocolHandshakeListener handshakeListener = new IProtocolHandshakeListener() {
			@Override
			public void onSuccess(short shortDeviceId) {
				assertEquals(deviceId, shortDeviceId);
				result.set(true);
				finishedLatch.countDown();
			}

			@Override
			public void onFailure(IBlaubotConnection connection) {
				result.set(false);
				finishedLatch.countDown();
			}
		};

		// build a pair of mocked connections
		BlaubotConnectionQueueMock conn1 = new BlaubotConnectionQueueMock(mockDevice1);
		BlaubotConnectionQueueMock conn2 = conn1.getOtherEndpointConnection(mockDevice2);

		// let the hands shake
		ProtocolHandshakeMasterTask masterTask = new ProtocolHandshakeMasterTask(deviceId, conn2, handshakeListener);
		ProtocolHandshakeClientTask clientTask = new ProtocolHandshakeClientTask(conn1, handshakeListener);
		masterTask.execute();
		clientTask.execute();

		boolean timeoutOccured = !finishedLatch.await(1000, TimeUnit.MILLISECONDS);
		if (timeoutOccured) {
			fail("Timeout occured while waiting for the handshake to complete.");
		}
		
		assertTrue(result.get());
	}
	
	@Test(timeout=10000)
	public void testHandshakeTimeoutMasterSide() throws InterruptedException {
		/*
		 * We start task without its opponent on the other side and await a failure
		 */
		final CountDownLatch failedLatch = new CountDownLatch(1);
		final AtomicBoolean result = new AtomicBoolean(false);
		final short deviceId = (short) 1;
		
		// Create a timeout latch for this task (see ProtocolHandshakeTask documentation)
		final CountDownLatch timeoutLatch = new CountDownLatch(1);

		// test the handshake
		IProtocolHandshakeListener handshakeListener = new IProtocolHandshakeListener() {
			@Override
			public void onSuccess(short shortDeviceId) {
				result.set(false);
				timeoutLatch.countDown();
				assertTrue("Expected an onFailure event on Handshake but got on success", false);
			}

			@Override
			public void onFailure(IBlaubotConnection connection) {
				result.set(true);
				timeoutLatch.countDown();
				failedLatch.countDown();
			}
		};

		// build a pair of mocked connections
		BlaubotConnectionQueueMock conn1 = new BlaubotConnectionQueueMock(mockDevice1);
		BlaubotConnectionQueueMock conn2 = conn1.getOtherEndpointConnection(mockDevice2);

		// let the hands shake
		ProtocolHandshakeMasterTask masterTask = new ProtocolHandshakeMasterTask(deviceId, conn2, handshakeListener);
		masterTask.execute();
		
		// now await a timeout
		boolean timedOut = !timeoutLatch.await(TIMEOUT_FOR_ON_FAILURE_EVENTS*2, TimeUnit.MILLISECONDS);
		if(timedOut) {
			// a timeout occured -> neither onFaiure nor onSuccess was called
			// close connections
			conn1.disconnect();
			conn2.disconnect();
		} else {
			fail("onSuccess or onFailure called without a counterpart");
		}

		// now we expect the onFailure method to be called (almos immediately)
		boolean onFailureTimeoutOccured = !failedLatch.await(TIMEOUT_FOR_ON_FAILURE_EVENTS, TimeUnit.MILLISECONDS);
		if (onFailureTimeoutOccured) {
			fail("Expected onFailure was not called after " + (TIMEOUT_FOR_ON_FAILURE_EVENTS) + " ms");
		}
		
		assertTrue(result.get());
	}
	
	@Test(timeout=10000)
	public void testHandshakeTimeoutClientSide() throws InterruptedException {
		/*
		 * We start task without its opponent on the other side and await a failure
		 */
		final CountDownLatch failedLatch = new CountDownLatch(1);
		final AtomicBoolean result = new AtomicBoolean(false);
		
		// Create a timeout latch for this task (see ProtocolHandshakeTask documentation)
		final CountDownLatch timeoutLatch = new CountDownLatch(1);

		// test the handshake
		IProtocolHandshakeListener handshakeListener = new IProtocolHandshakeListener() {
			@Override
			public void onSuccess(short shortDeviceId) {
				result.set(false);
				timeoutLatch.countDown();
				assertTrue("Expected an onFailure event on Handshake but got on success", false);
			}

			@Override
			public void onFailure(IBlaubotConnection connection) {
				result.set(true);
				timeoutLatch.countDown();
				failedLatch.countDown();
			}
		};

		// build a pair of mocked connections
		BlaubotConnectionQueueMock conn1 = new BlaubotConnectionQueueMock(mockDevice1);
		BlaubotConnectionQueueMock conn2 = conn1.getOtherEndpointConnection(mockDevice2);

		// let the hands shake
		ProtocolHandshakeClientTask clientTask = new ProtocolHandshakeClientTask(conn1, handshakeListener);
		clientTask.execute();
		
		// now await a timeout
		boolean timedOut = !timeoutLatch.await(TIMEOUT_FOR_ON_FAILURE_EVENTS*2, TimeUnit.MILLISECONDS);
		if(timedOut) {
			// a timeout occured -> neither onFaiure nor onSuccess was called
			// close connections
			conn1.disconnect();
			conn2.disconnect();
		} else {
			fail("onSuccess or onFailure called without a counterpart");
		}

		// now we expect the onFailure method to be called (almos immediately)
		boolean onFailureTimeoutOccured = !failedLatch.await(TIMEOUT_FOR_ON_FAILURE_EVENTS, TimeUnit.MILLISECONDS);
		if (onFailureTimeoutOccured) {
			fail("Expected onFailure was not called after " + (TIMEOUT_FOR_ON_FAILURE_EVENTS) + " ms");
		}
		
		assertTrue(result.get());
	}

	
	/**
	 * Creates a random byte array with CNT elements
	 * @param CNT
	 * @return
	 */
	private byte[] createRandomTestByteArray(int CNT) {
		Random random = new Random();
		byte[] data = new byte[CNT];
		for(int i=0;i<CNT;i++) {
			data[i] = (byte) random.nextInt(i+1);
		}
		return data;
	}
}
