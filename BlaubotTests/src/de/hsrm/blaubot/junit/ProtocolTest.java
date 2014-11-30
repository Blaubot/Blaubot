package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.mock.BlaubotConnectionQueueMock;
import de.hsrm.blaubot.mock.BlaubotDeviceMock;
import de.hsrm.blaubot.protocol.IMessageListener;
import de.hsrm.blaubot.protocol.ProtocolContext;
import de.hsrm.blaubot.protocol.ProtocolEnums.MessageRateType;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.client.ProtocolClient;
import de.hsrm.blaubot.protocol.client.channel.Channel;
import de.hsrm.blaubot.protocol.client.channel.ChannelConfig;
import de.hsrm.blaubot.protocol.client.channel.ChannelConfigFactory;
import de.hsrm.blaubot.protocol.client.channel.ChannelFactory;
import de.hsrm.blaubot.util.Log;
import de.hsrm.blaubot.util.Log.LogLevel;

public class ProtocolTest {

	private static final int DEVICE_COUNT = 10;
	private static final long TEST_TIMEOUT = 1000 * 5;
	private final static String MASTER_ID = "masterID";
	protected static final String TAG = "ProtocolTest";

	private final int messageCount = 100;
	private final String testString = "TestMessage Oida!";
	private ProtocolManager masterProtocolManager;
	private List<ProtocolManager> clientProtocolManagers;
	private List<IBlaubotConnection> connections;
	private static int protocolStart = 50000;

	@BeforeClass
	public static void setUpClass() {
		Log.LOG_LEVEL = LogLevel.DEBUG;
	}

	@Before
	public void setUp() {
		protocolStart += DEVICE_COUNT * 3;
	}

	private void networkSetup(int size) throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		Log.d(TAG, "setting up");

		ProtocolTestHelper testHelper = new ProtocolTestHelper(protocolStart, MASTER_ID);
		testHelper.createAndConnectMockNetwork(size);

		this.clientProtocolManagers = testHelper.getClientProtocolManagers();
		this.masterProtocolManager = testHelper.getMasterProtocolManager();
		// merge into one list
		this.connections = testHelper.getMasterConnections();
		this.connections.addAll(testHelper.getClientConnections());
	}

	@After
	public void cleanUp() throws UnknownHostException, InterruptedException {
		Log.d(TAG, "cleaning up");
		// first close connections then stop the managers
		closeConnections();

		if (this.masterProtocolManager != null) {
			this.masterProtocolManager.deactivate();
		}

		if (this.clientProtocolManagers != null) {
			for (ProtocolManager protocolManager : this.clientProtocolManagers) {
				protocolManager.deactivate();
			}
		}
	}

	private synchronized void closeConnections() {
		if (this.connections == null) {
			return;
		}
		for (IBlaubotConnection connection : this.connections) {
			connection.disconnect();
		}
		this.connections.clear();
	}

	@Test
	public void testMasterTrueFalse() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		networkSetup(10);
		ProtocolManager master = this.masterProtocolManager;
		master.setMaster(true);
		master.setMaster(false);
		master.setMaster(true);
		master.setMaster(false);
		master.setMaster(false);
		master.setMaster(true);
		master.setMaster(true);
		master.setMaster(true);
		master.setMaster(false);
		master.setMaster(true);
		master.setMaster(true);
		master.setMaster(true);
		master.setMaster(false);
		master.setMaster(true);
		master.setMaster(false);
		master.setMaster(false);
		master.setMaster(false);
	}

	@Test
	public void testMasterOnOffWithSendMessages() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, ExecutionException {
		networkSetup(3);

		final ProtocolManager master = this.masterProtocolManager;
		final ProtocolManager client1 = this.clientProtocolManagers.get(0);
		final ProtocolManager client2 = this.clientProtocolManagers.get(1);

		final Channel client1Channel = getTestChannel(client1);
		Channel client2Channel = getTestChannel(client2);

		final String testMsg = "Test...";
		final int periods = 50;

		final AtomicInteger messageCount = new AtomicInteger(0);

		IMessageListener listener = new IMessageListener() {

			@Override
			public void onMessage(BlaubotMessage message) {
				messageCount.incrementAndGet();
			}
		};

		final Short client2Id = client2.getContext().getShortDeviceId(client2.getContext().getOwnUniqueDeviceID()).get();
		client2Channel.subscribe(listener);
		waitForSubscribeToFinish();

		final CountDownLatch latch = new CountDownLatch(1);

		new Thread(new Runnable() {

			@Override
			public void run() {
				boolean isMaster = true;

				for (int i = 0; i < periods; i++) {
					// only send if master is true (otherwise msg wouldn't be
					// multiplexed)
					if (isMaster) {
						client1Channel.post(testMsg.getBytes());
						Set<Short> channelSubscriptions = master.getContext().getChannelSubscriptions((short) 1);
						assertTrue(channelSubscriptions.contains(client2Id));
					}

					// close all connections
					closeConnections();

					// switch flag
					isMaster = !isMaster;
					master.setMaster(isMaster);

					// reconnect master and clients (previously disconnected)
					connectMasterAndClient(master, client1);
					connectMasterAndClient(master, client2);

					if (Log.logDebugMessages()) {
						Log.d(TAG, "####################################");
						Log.d(TAG, "# end of period " + i);
						Log.d(TAG, "####################################");
					}
				}

				latch.countDown();
			}
		}).start();

		// TODO nachrichten nach master off on werden nicht mehr empfangen
		boolean awaited = latch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertTrue(String.format("Got only %d of %d messages before timeout", messageCount.get(), periods / 2), awaited);
	}

	protected void doFail() {
		fail();
	}

	@Test
	public void testMasterOnOff() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		networkSetup(1);

		final ProtocolManager master = this.masterProtocolManager;
		final ChannelFactory channelFactory = master.getChannelFactory();
		final ProtocolContext context = master.getContext();

		final int periods = 30;
		final CountDownLatch latch = new CountDownLatch(1);
		final int maxSleep = 200;
		final int minSleep = 100;

		new Thread(new Runnable() {

			@Override
			public void run() {
				Random r = new Random();
				boolean isMaster = false;

				for (int i = 0; i < periods; i++) {
					if (Log.logDebugMessages())
						Log.d(TAG, "started stage == " + i);

					// random in range 100 - 200
					int randomInt = r.nextInt(maxSleep - minSleep) + minSleep;
					try {
						Thread.sleep(randomInt);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					// switch flag
					isMaster = !isMaster;
					master.setMaster(isMaster);

					String ownUniqueDeviceID = context.getOwnUniqueDeviceID();
					assertNotNull(ownUniqueDeviceID);

					try {
						Short shortDeviceId = context.getShortDeviceId(ownUniqueDeviceID).get();
						assertNotNull(shortDeviceId);

						Short deviceChannelId = context.getDeviceChannelId(ownUniqueDeviceID).get();
						assertNotNull(deviceChannelId);
					} catch (InterruptedException e) {
						e.printStackTrace();
						fail();
					} catch (ExecutionException e) {
						e.printStackTrace();
						fail();
					}

					Channel ownDeviceAdminChannel = channelFactory.getOwnDeviceAdminChannel();
					assertNotNull(ownDeviceAdminChannel);

					if (Log.logDebugMessages())
						Log.d(TAG, "ended stage == " + i);
				}

				latch.countDown();
			}
		}).start();

		// await latch / send thread
		int timeout = maxSleep * periods * 2;
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;
		boolean awaited = latch.await(timeout, timeUnit);
		assertTrue(awaited);
	}

	private synchronized void connectMasterAndClient(ProtocolManager master, ProtocolManager client) {
		String masterID = master.getContext().getOwnUniqueDeviceID();
		String clientID = client.getContext().getOwnUniqueDeviceID();
		BlaubotDeviceMock masterDevice = new BlaubotDeviceMock(masterID);
		BlaubotConnectionQueueMock connectionToMaster = new BlaubotConnectionQueueMock(masterDevice);
		BlaubotDeviceMock clientDevice = new BlaubotDeviceMock(clientID);
		BlaubotConnectionQueueMock connectionToClient = connectionToMaster.getOtherEndpointConnection(clientDevice);

		master.addConnection(connectionToClient);
		client.addConnection(connectionToMaster);

		connections.add(connectionToClient);
		connections.add(connectionToMaster);
	}

	/**
	 * sleeps some milliseconds to let the subscriptions pass through the master
	 * 
	 * @throws InterruptedException
	 */
	private void waitForSubscribeToFinish() throws InterruptedException {
		Thread.sleep(300);
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testDeviceChannelSubscription() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		networkSetup(DEVICE_COUNT);

		ProtocolManager master = this.masterProtocolManager;
		ProtocolManager client = this.clientProtocolManagers.get(0);

		String clientUniqueDeviceID = client.getContext().getOwnUniqueDeviceID();
		String masterUniqueDeviceID = master.getContext().getOwnUniqueDeviceID();

		// make sure that messages are sent and received via these channels
		final CountDownLatch latch = new CountDownLatch(2);
		final String testMsg = "Test Message!";
		final IMessageListener listener = new IMessageListener() {

			@Override
			public void onMessage(BlaubotMessage message) {
				String string = new String(message.getPayload());
				if (string.equals(testMsg)) {
					latch.countDown();
				}
			}
		};

		ChannelFactory masterFactory = master.getChannelFactory();
		ChannelFactory clientFactory = client.getChannelFactory();

		// add listeners on master and client
		Channel masterOwnDeviceAdminChannel = masterFactory.getOwnDeviceAdminChannel();
		assertNotNull(masterOwnDeviceAdminChannel);
		masterOwnDeviceAdminChannel.addListener(listener);

		Channel clientOwnDeviceAdminChannel = clientFactory.getOwnDeviceAdminChannel();
		assertNotNull(clientOwnDeviceAdminChannel);
		clientOwnDeviceAdminChannel.addListener(listener);

		// post message from master and client
		Channel clientDeviceChannel = masterFactory.getAdminDeviceChannel(clientUniqueDeviceID);
		assertNotNull(clientDeviceChannel);
		clientDeviceChannel.post(testMsg.getBytes());

		Channel masterDeviceChannel = clientFactory.getAdminDeviceChannel(masterUniqueDeviceID);
		assertNotNull(masterDeviceChannel);
		masterDeviceChannel.post(testMsg.getBytes());

		if (Log.logDebugMessages())
			Log.d(TAG, "awaiting messages");
		latch.await();
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testGetOwnAdminDeviceChannel() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, ExecutionException {
		networkSetup(DEVICE_COUNT);

		ProtocolManager master = this.masterProtocolManager;
		ProtocolContext context = master.getContext();
		short ownShortDeviceId = context.getShortDeviceId(context.getOwnUniqueDeviceID()).get();
		ChannelFactory masterFactory = master.getChannelFactory();
		Channel masterOwnAdminChannel = masterFactory.getOwnDeviceAdminChannel();
		// make sure that own admin channel exists
		assertNotNull(masterOwnAdminChannel);
		// channel id == min value of short + short device id
		assertEquals(Short.MIN_VALUE + ownShortDeviceId, masterOwnAdminChannel.getConfig().getId());

		if (Log.logDebugMessages())
			Log.d(TAG, "awaiting messages");
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testGetOwnAdminDeviceChannelLowLevel() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, ExecutionException {
		/**
		 * this test awaits (via future task) the own admin device channel
		 * before setting the corresponding values to the context. this means
		 * that the channel is awaited before all necessary values have been
		 * set.
		 */

		// repeat several times in order to assure that these operations don't
		// take too long
		for (int i = 0; i < 100; i++) {
			String ownUniqueDeviceID = "testId";
			ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
			final short shortDeviceId = 123;

			ProtocolManager protocolManager = new ProtocolManager(context);
			ProtocolClient protocolClient = new ProtocolClient(context);
			final ChannelFactory channelFactory = new ChannelFactory(protocolManager, protocolClient);
			final CountDownLatch latch = new CountDownLatch(1);

			new Thread(new Runnable() {

				@Override
				public void run() {
					Channel ownAdminChannel = channelFactory.getOwnDeviceAdminChannel();
					// make sure that own admin channel exists
					assertNotNull(ownAdminChannel);
					// channel id == min value of short + short device id
					assertEquals(Short.MIN_VALUE + shortDeviceId, ownAdminChannel.getConfig().getId());
					latch.countDown();
				}
			}).start();

			boolean notYetExisting = context.putShortDeviceIdToUnqiueDeviceId(shortDeviceId, ownUniqueDeviceID);
			assertTrue(notYetExisting);
			short readShortDeviceId = context.getShortDeviceId(context.getOwnUniqueDeviceID()).get();
			assertEquals(shortDeviceId, readShortDeviceId);

			if (Log.logDebugMessages())
				Log.d(TAG, "awaiting messages");
			latch.await();
		}
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testHandShake2() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		networkSetup(2);
		waitForSubscribeToFinish();

		Log.d(TAG, "Handshake with 2 done");
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testHandShake3() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		networkSetup(3);

		Log.d(TAG, "Handshake with 3 done");
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testBasic() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		networkSetup(2);

		Channel masterChannel = getTestChannel(this.masterProtocolManager);
		Channel clientChannel = getTestChannel(this.clientProtocolManagers.get(0));

		final CountDownLatch countDownLatch = new CountDownLatch(1);
		final String testMessage = "123";

		clientChannel.subscribe(new IMessageListener() {

			@Override
			public void onMessage(BlaubotMessage message) {
				if (Log.logDebugMessages())
					Log.d(TAG, "onMessage called");
				String msg = new String(message.getPayload());
				assertEquals(testMessage, msg);
				countDownLatch.countDown();
			}
		});
		waitForSubscribeToFinish();
		Log.d(TAG, "Subscription done");

		masterChannel.post(testMessage.getBytes());
		Log.d(TAG, "message " + testMessage + " posted");

		countDownLatch.await();
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testHandshakeMasterSelf() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		/*
		 * create only master by choosing network size == 1. then subscribe to
		 * user channel == 1 and send a message via this channel. master himself
		 * has to receive the message after sending it because he previously
		 * subscribed to its messages.
		 */
		networkSetup(1);

		Channel masterChannel = getTestChannel(this.masterProtocolManager);

		final CountDownLatch countDownLatch = new CountDownLatch(1);
		final String testMessage = "123";

		masterChannel.subscribe(new IMessageListener() {

			@Override
			public void onMessage(BlaubotMessage message) {
				if (Log.logDebugMessages())
					Log.d(TAG, "on message called");
				String msg = new String(message.getPayload());
				assertEquals(testMessage, msg);
				countDownLatch.countDown();
			}
		});
		waitForSubscribeToFinish();

		masterChannel.post(testMessage.getBytes());

		countDownLatch.await();
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testMasterReceive() throws InterruptedException, IOException, BrokenBarrierException, TimeoutException {
		networkSetup(DEVICE_COUNT);

		Channel masterReceiveChannel = getTestChannel(this.masterProtocolManager);
		final CountDownLatch masterLatch = new CountDownLatch(messageCount);
		// verify that the master himself gets the message too
		masterReceiveChannel.subscribe(new IMessageListener() {

			@Override
			public void onMessage(BlaubotMessage message) {
				String actualString = new String(message.getPayload());
				assertEquals(testString, actualString);
				masterLatch.countDown();
			}
		});
		waitForSubscribeToFinish();

		// get one client
		ProtocolManager clientProtocolManager = this.clientProtocolManagers.get(3);
		Channel sendChannel = getTestChannel(clientProtocolManager);

		for (int i = 0; i < messageCount; i++) {
			sendChannel.post(testString.getBytes());
		}

		masterLatch.await();
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testSendReceiveClientToClient() throws InterruptedException, IOException, BrokenBarrierException, TimeoutException {
		networkSetup(DEVICE_COUNT);

		ProtocolManager clientProtocolManager1 = this.clientProtocolManagers.get(3);
		Channel sendChannel = getTestChannel(clientProtocolManager1);

		ProtocolManager clientProtocolManager2 = this.clientProtocolManagers.get(7);
		Channel receiveChannel = getTestChannel(clientProtocolManager2);

		final CountDownLatch clientLatch = new CountDownLatch(messageCount);
		receiveChannel.subscribe(new IMessageListener() {
			@Override
			public void onMessage(BlaubotMessage message) {
				String actualString = new String(message.getPayload(), BlaubotConstants.STRING_CHARSET);
				assertEquals(testString, actualString);
				clientLatch.countDown();
			}
		});
		waitForSubscribeToFinish();

		for (int i = 0; i < messageCount; i++) {
			sendChannel.post(testString.getBytes());
		}

		clientLatch.await();
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testSendAndReceiveClientToMany() throws InterruptedException, IOException, BrokenBarrierException, TimeoutException {
		networkSetup(DEVICE_COUNT);

		ProtocolManager clientProtocolManager1 = this.clientProtocolManagers.get(3);
		Channel sendChannel = getTestChannel(clientProtocolManager1);

		ProtocolManager clientProtocolManager2 = this.clientProtocolManagers.get(5);
		Channel receiveChannel1 = getTestChannel(clientProtocolManager2);

		ProtocolManager clientProtocolManager3 = this.clientProtocolManagers.get(7);
		Channel receiveChannel2 = getTestChannel(clientProtocolManager3);

		// latch is counted down by two receivers
		final CountDownLatch latch = new CountDownLatch(messageCount * 2);
		IMessageListener receiveListener = new IMessageListener() {
			@Override
			public void onMessage(BlaubotMessage message) {
				String actualString = new String(message.getPayload());
				assertEquals(testString, actualString);
				latch.countDown();
			}
		};
		receiveChannel1.subscribe(receiveListener);
		receiveChannel2.subscribe(receiveListener);
		waitForSubscribeToFinish();

		for (int i = 0; i < messageCount; i++) {
			sendChannel.post(testString.getBytes());
		}

		latch.await();
	}

	@Test(timeout = TEST_TIMEOUT)
	public void testFixedRate() throws InterruptedException, IOException, BrokenBarrierException, TimeoutException {
		networkSetup(DEVICE_COUNT);
		ProtocolManager clientProtocolManager1 = this.clientProtocolManagers.get(3);
		ChannelFactory channelFactory = clientProtocolManager1.getChannelFactory();
		final short id = 1;
		// send every 100 msecs (equals 10 messages per second)
		ChannelConfig config = new ChannelConfig().Id(id).MessageRate(10).MessageRateType(MessageRateType.FIXED_DISCARD_OLD);
		Channel sendChannel = channelFactory.createUserChannel(config);

		ProtocolManager clientProtocolManager2 = this.clientProtocolManagers.get(5);
		Channel receiveChannel = getTestChannel(clientProtocolManager2);

		final AtomicInteger received = new AtomicInteger(0);
		final List<String> sreceived = new ArrayList<String>();
		IMessageListener receiveListener = new IMessageListener() {
			@Override
			public void onMessage(BlaubotMessage message) {
				String smessage = new String(message.getPayload());
				received.incrementAndGet();
				sreceived.add(smessage);
			}
		};
		receiveChannel.subscribe(receiveListener);
		waitForSubscribeToFinish();

		for (int i = 0; i < 100; i++) {
			sendChannel.post(("msg" + i).getBytes());
			Thread.sleep(10); // try to send 10 times faster than channel send
								// rate
		}
		Thread.sleep(100); // should be fine after 100 milliseconds (discard!)
		if (received.get() < 8 || received.get() > 12) {
			fail("10 Messages expected but got: " + received.get());
		}
	}

	/**
	 * @param protocolManager
	 * @return channel with id == 1 and no limit message rate
	 */
	private Channel getTestChannel(ProtocolManager protocolManager) {
		ChannelFactory channelFactory = protocolManager.getChannelFactory();
		final short id = 1;
		ChannelConfig config = ChannelConfigFactory.getNoLimitConfig().Id(id);
		Channel channel = channelFactory.createUserChannel(config);
		return channel;
	}

}
