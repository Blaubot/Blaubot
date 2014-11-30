package de.hsrm.blaubot.junit;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.util.Log;

public class ProtocolTestHelperTest {

	private static final String TAG = "ProtocolTestHelperTest";

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void testQueueMockMasterToClient() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		ProtocolTestHelper helper = new ProtocolTestHelper(0, "masterID");
		helper.createMockNetwork(2);

		List<IBlaubotConnection> clientConnections = helper.getClientConnections();
		List<IBlaubotConnection> masterConnections = helper.getMasterConnections();

		Assert.assertEquals(1, clientConnections.size());
		Assert.assertEquals(1, masterConnections.size());

		final IBlaubotConnection clientConn = clientConnections.get(0);
		final IBlaubotConnection masterConn = masterConnections.get(0);

		ExecutorService readExecutor = Executors.newCachedThreadPool();

		final String msg = "asd";
		byte[] msgBytes = msg.getBytes();
		final int count = msgBytes.length;
		final CountDownLatch latch = new CountDownLatch(2);

		// read on master
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					masterConn.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "master read msg == " + readMsg);
				assertEquals(msg, readMsg);
				latch.countDown();
			}
		});

		// read on client (expect to read nothing because we are sender)
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					clientConn.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "client read msg == " + readMsg);
				assertEquals(null, readMsg);
			}
		});

		clientConn.write(msgBytes);
		if (Log.logDebugMessages())
			Log.d(TAG, "client sent msg");

		boolean awaited = latch.await(2, TimeUnit.SECONDS);
		// assert that there was no 2. countdown (in client read)
		assertFalse(awaited);

		clientConn.disconnect();
		masterConn.disconnect();
	}

	@Test
	public void testMasterToClient() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		ProtocolTestHelper helper = new ProtocolTestHelper(4567, "masterID");
		helper.createSocketNetwork(2);

		List<IBlaubotConnection> clientConnections = helper.getClientConnections();
		List<IBlaubotConnection> masterConnections = helper.getMasterConnections();

		Assert.assertEquals(1, clientConnections.size());
		Assert.assertEquals(1, masterConnections.size());

		final IBlaubotConnection clientConn = clientConnections.get(0);
		final IBlaubotConnection masterConn = masterConnections.get(0);

		ExecutorService readExecutor = Executors.newCachedThreadPool();

		final String msg = "asd";
		byte[] msgBytes = msg.getBytes();
		final int count = msgBytes.length;
		final CountDownLatch latch = new CountDownLatch(2);

		// read on master
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					masterConn.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "master read msg == " + readMsg);
				assertEquals(msg, readMsg);
				latch.countDown();
			}
		});

		// read on client (expect to read nothing because we are sender)
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					clientConn.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "client read msg == " + readMsg);
				assertEquals(null, readMsg);
			}
		});

		clientConn.write(msgBytes);
		if (Log.logDebugMessages())
			Log.d(TAG, "client sent msg");

		boolean awaited = latch.await(2, TimeUnit.SECONDS);
		// assert that there was no 2. countdown (in client read)
		assertFalse(awaited);

		clientConn.disconnect();
		masterConn.disconnect();
	}

	@Test
	public void testSideEffects() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		ProtocolTestHelper helper = new ProtocolTestHelper(1234, "masterID");
		helper.createSocketNetwork(5);

		List<IBlaubotConnection> clientConnections = helper.getClientConnections();
		List<IBlaubotConnection> masterConnections = helper.getMasterConnections();

		Assert.assertEquals(4, clientConnections.size());
		Assert.assertEquals(4, masterConnections.size());

		final IBlaubotConnection clientConn1 = clientConnections.get(1);
		final IBlaubotConnection clientConn2 = clientConnections.get(3);
		final IBlaubotConnection masterConn = masterConnections.get(0);

		ExecutorService readExecutor = Executors.newCachedThreadPool();

		final String msg = "asd";
		byte[] msgBytes = msg.getBytes();
		final int count = msgBytes.length;
		final CountDownLatch latch = new CountDownLatch(3);

		// read on master
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					masterConn.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "master read msg == " + readMsg);
				assertEquals(msg, readMsg);
				latch.countDown();
			}
		});

		// read on client1 (expect to read nothing because we are sender)
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					clientConn1.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "client1 read msg == " + readMsg);
				assertEquals(null, readMsg);
			}
		});

		// read on client2 (expect to read nothing because we are on another
		// connection to master than sender)
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					clientConn2.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "client2 read msg == " + readMsg);
				assertEquals(null, readMsg);
			}
		});

		clientConn1.write(msgBytes);
		if (Log.logDebugMessages())
			Log.d(TAG, "client1 sent msg");

		boolean awaited = latch.await(1, TimeUnit.SECONDS);
		// assert that there was no 2. countdown (in client 1/2 read)
		assertFalse(awaited);
		
		clientConn1.disconnect();
		clientConn2.disconnect();
		masterConn.disconnect();
	}

	@Test
	public void testSideEffects2() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		ProtocolTestHelper helper = new ProtocolTestHelper(6789, "masterID");
		helper.createSocketNetwork(5);

		List<IBlaubotConnection> clientConnections = helper.getClientConnections();
		List<IBlaubotConnection> masterConnections = helper.getMasterConnections();

		Assert.assertEquals(4, clientConnections.size());
		Assert.assertEquals(4, masterConnections.size());

		final IBlaubotConnection clientConn1 = clientConnections.get(1);
		final IBlaubotConnection clientConn2 = clientConnections.get(3);
		final IBlaubotConnection masterConn = masterConnections.get(0);

		ExecutorService readExecutor = Executors.newCachedThreadPool();

		final String msg = "asd";
		byte[] msgBytes = msg.getBytes();
		final int count = msgBytes.length;
		final CountDownLatch latch = new CountDownLatch(3);

		// read on master (expect to read nothing because we are sender)
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					masterConn.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "master read msg == " + readMsg);
				assertEquals(null, readMsg);
				latch.countDown();
			}
		});

		// read on client1
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					clientConn1.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "client1 read msg == " + readMsg);
				assertEquals(msg, readMsg);
			}
		});

		// read on client2 (expect to read nothing because we are on another
		// connection to master than sender)
		readExecutor.execute(new Runnable() {

			@Override
			public void run() {
				byte[] buffer = new byte[count];
				try {
					clientConn2.readFully(buffer);
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
				String readMsg = new String(buffer);
				if (Log.logDebugMessages())
					Log.d(TAG, "client2 read msg == " + readMsg);
				assertEquals(null, readMsg);
			}
		});

		masterConn.write(msgBytes);
		if (Log.logDebugMessages())
			Log.d(TAG, "master sent msg");

		boolean awaited = latch.await(1, TimeUnit.SECONDS);
		// assert that there was no 2. countdown (in client 2 / master read)
		assertFalse(awaited);
		
		clientConn1.disconnect();
		clientConn2.disconnect();
		masterConn.disconnect();
	}

}
