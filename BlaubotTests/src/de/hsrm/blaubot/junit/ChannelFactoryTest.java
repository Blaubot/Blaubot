package de.hsrm.blaubot.junit;

import static org.junit.Assert.*;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import de.hsrm.blaubot.mock.BlaubotDeviceMock;
import de.hsrm.blaubot.protocol.ProtocolContext;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.client.ProtocolClient;
import de.hsrm.blaubot.protocol.client.channel.Channel;
import de.hsrm.blaubot.protocol.client.channel.ChannelConfig;
import de.hsrm.blaubot.protocol.client.channel.ChannelConfigFactory;
import de.hsrm.blaubot.protocol.client.channel.ChannelFactory;
import de.hsrm.blaubot.util.Log;
import de.hsrm.blaubot.util.Log.LogLevel;

public class ChannelFactoryTest {

	protected static final String TAG = "ChannelFactoryTest";
	private final String ownUniqueDeviceID = "ownUniqueDeviceID";
	private final short ownShortDeviceId = 123;
	private final ProtocolContext protocolContext = new ProtocolContext(ownUniqueDeviceID);
	private final ProtocolManager protocolManager = new ProtocolManager(protocolContext);
	private final ProtocolClient protocolClient = new ProtocolClient(protocolContext);
	private final ChannelFactory channelFactory = new ChannelFactory(protocolManager, protocolClient);

	@Before
	public void setup() {
		Log.LOG_LEVEL = LogLevel.DEBUG;
	}

	@Test
	public void getAdminDeviceChannelWithIdTest() {
		// first add short device id to context
		this.protocolContext.putShortDeviceIdToUnqiueDeviceId(ownShortDeviceId, ownUniqueDeviceID);
		// then retrieve own admin device channel
		Channel channel = this.channelFactory.getAdminDeviceChannel(this.ownUniqueDeviceID);
		assertNotNull(channel);
	}

	@Test
	public void getAdminDeviceChannelWithDeviceTest() {
		// first add short device id to context
		this.protocolContext.putShortDeviceIdToUnqiueDeviceId(ownShortDeviceId, ownUniqueDeviceID);
		// then retrieve own admin device channel
		BlaubotDeviceMock device = new BlaubotDeviceMock(ownUniqueDeviceID);
		Channel channel = this.channelFactory.getAdminDeviceChannel(device);
		assertNotNull(channel);
	}

	@Test
	public void testConcurrentChannelCreateWithSameChannelId() throws InterruptedException {
		short threads = 100;
		final CyclicBarrier barrier = new CyclicBarrier(threads);
		final CountDownLatch latch = new CountDownLatch(threads);
		final AtomicInteger counter = new AtomicInteger(0);
		/*
		 * creates #threads Threads which await a barrier in order to
		 * concurrently create a user channel with the same channel id. this
		 * test is expected to throw runtime exceptions and to time out because
		 * the latch hasn't been count down
		 */
		for (short i = 0; i < threads; i++) {
			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						barrier.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (BrokenBarrierException e) {
						e.printStackTrace();
					}
					ChannelConfig config = ChannelConfigFactory.getNoLimitWithStandardPriorityConfig();
					Channel channel = channelFactory.createUserChannel(config);
					assertNotNull(channel);
					latch.countDown();
					counter.incrementAndGet();
				}
			}).start();
		}
		boolean awaited = latch.await(1000, TimeUnit.MILLISECONDS);
		assertFalse(awaited);
		// only one channel should be successfully created
		assertEquals(1, counter.get());
	}

	@Test
	public void testConcurrentChannelCreate() throws InterruptedException {
		short threads = 100;
		final CyclicBarrier barrier = new CyclicBarrier(threads);
		final CountDownLatch latch = new CountDownLatch(threads);
		final AtomicInteger counter = new AtomicInteger(0);
		/*
		 * creates #threads Threads which await a barrier in order to
		 * concurrently create a user channel with different channel ids.
		 */
		for (final AtomicInteger i = new AtomicInteger(0); i.get() < threads; i.incrementAndGet()) {
			// use different channel ids
			final short id = (short) i.get();
			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						barrier.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (BrokenBarrierException e) {
						e.printStackTrace();
					}
					ChannelConfig config = ChannelConfigFactory.getNoLimitWithStandardPriorityConfig();
					config.Id(id);
					if (Log.logDebugMessages()) {
						Log.d(TAG, "id == " + id);
					}
					Channel channel = channelFactory.createUserChannel(config);
					assertNotNull(channel);
					latch.countDown();
					counter.incrementAndGet();
				}
			}).start();
		}
		boolean awaited = latch.await(1000, TimeUnit.MILLISECONDS);
		assertTrue(awaited);
		// all channels should be successfully created
		assertEquals(threads, counter.get());
	}

}
