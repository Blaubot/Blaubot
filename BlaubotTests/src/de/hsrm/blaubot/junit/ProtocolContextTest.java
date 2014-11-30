package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.hsrm.blaubot.protocol.ProtocolContext;
import de.hsrm.blaubot.util.Log;
import de.hsrm.blaubot.util.Log.LogLevel;

public class ProtocolContextTest {

	@Before
	public void setUp() throws Exception {
		Log.LOG_LEVEL = LogLevel.DEBUG;
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetUniqueDeviceID() throws InterruptedException, ExecutionException, TimeoutException {
		String ownUniqueDeviceID = "own_one";
		final ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
		final String uniqueDeviceID = "another_one";
		final short shortDeviceID = 12;

		Future<String> future = context.getUniqueDeviceId(shortDeviceID);

		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				context.putShortDeviceIdToUnqiueDeviceId(shortDeviceID, uniqueDeviceID);
			}
		}).start();

		String result = future.get(1, TimeUnit.SECONDS);
		assertNotNull(result);
		assertEquals(uniqueDeviceID, result);
	}

	@Test
	public void testGetShortDeviceID() throws InterruptedException, ExecutionException, TimeoutException {
		String ownUniqueDeviceID = "own_one";
		final ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
		final String uniqueDeviceID = "another_one";
		final short shortDeviceID = 12;

		Future<Short> future = context.getShortDeviceId(uniqueDeviceID);

		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				context.putShortDeviceIdToUnqiueDeviceId(shortDeviceID, uniqueDeviceID);
			}
		}).start();

		Short result = future.get(1, TimeUnit.SECONDS);
		assertNotNull(result);
		assertEquals(shortDeviceID, (short) result);
	}

	@Test(expected = TimeoutException.class)
	public void testDontGetShortDeviceID() throws InterruptedException, ExecutionException, TimeoutException {
		String ownUniqueDeviceID = "own_one";
		ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
		final String uniqueDeviceID = "another_one";
		final short shortDeviceID = 17;

		FutureTask<Short> future = context.getShortDeviceId(uniqueDeviceID);
		Thread.sleep(100);

		// put another mapping than the one we expect in the get thread above ->
		// provoke timeout exception
		context.putShortDeviceIdToUnqiueDeviceId(shortDeviceID, uniqueDeviceID + "blabla");
		future.get(100, TimeUnit.MILLISECONDS);
	}

	@Test(timeout = 100)
	public void createChannelIdTest() {
		String ownUniqueDeviceID = "own_one";
		ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
		final String uniqueDeviceID = "another_one";
		final short shortDeviceId = 345;

		Short result = context.createChannelId(uniqueDeviceID, shortDeviceId);
		assertNotNull(result);
		assertEquals((Short.MIN_VALUE + shortDeviceId), (short) result);
	}

	@Test
	public void getChannelIdTest() throws InterruptedException, ExecutionException, TimeoutException {
		String ownUniqueDeviceID = "own_one";
		final ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
		final String uniqueDeviceID = "another_one";
		final short shortDeviceId = 345;

		FutureTask<Short> future = context.getDeviceChannelId(uniqueDeviceID);

		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				context.createChannelId(uniqueDeviceID, shortDeviceId);
			}
		}).start();

		Short retrievedChannelId = future.get(1, TimeUnit.SECONDS);
		assertNotNull(retrievedChannelId);
		assertEquals((Short.MIN_VALUE + shortDeviceId), (short) retrievedChannelId);
	}

	@Test(expected = TimeoutException.class)
	public void dontGetChannelIdTest() throws InterruptedException, ExecutionException, TimeoutException {
		String ownUniqueDeviceID = "own_one";
		ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
		final String uniqueDeviceID = "another_one";
		final short shortDeviceId = 345;

		FutureTask<Short> future = context.getDeviceChannelId(uniqueDeviceID);
		context.createChannelId(uniqueDeviceID + "blabla", shortDeviceId);
		future.get(100, TimeUnit.MILLISECONDS);
	}

	@Test
	public void uniqueToShortTwiceTest() {
		String ownUniqueDeviceID = "own_one";
		ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
		final String uniqueDeviceID = "another_one";
		final short shortDeviceId = 345;

		context.putShortDeviceIdToUnqiueDeviceId(shortDeviceId, uniqueDeviceID);
		context.putShortDeviceIdToUnqiueDeviceId((short) (shortDeviceId + 5), uniqueDeviceID);
	}

}
