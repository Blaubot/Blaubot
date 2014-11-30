package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.hsrm.blaubot.statistics.StatisticsUtil;

public class StatisticsUtilTest {

	private static final int[] TIMEOUTS = new int[] { 10, 20, 30, 40, 50 };
	private static final int LATCH_TIMEOUT_SECONDS = 10;
	private final int THREAD_COUNT = 100;
	// create a latch in order to await thread executions to finish
	private CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test(timeout = LATCH_TIMEOUT_SECONDS * 2000)
	public void testWithNanoSeconds() throws InterruptedException {
		StatisticsUtil.reset();

		// execute threadCount Threads
		for (int i = 0; i < THREAD_COUNT; i++) {
			new TestThreadNano().start();
		}

		boolean awaited = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		if (!awaited) {
			Assert.fail();
		}

		// factor for the millis
		final int nanoTimeFactor = 1000 * 1000;
		// sum of timeouts defined above
		int timeoutSum = 0;
		for (int timeout : TIMEOUTS) {
			timeoutSum += timeout;
		}
		// number of timeouts which are used in the executed threads
		int numOfTimeouts = TIMEOUTS.length;
		int numOfCycles = numOfTimeouts * THREAD_COUNT;

		// get values from the stats util
		long minProcessData = StatisticsUtil.getMinProcessData().get();
		long maxProcessData = StatisticsUtil.getMaxProcessData().get();
		long averageProcessData = StatisticsUtil.getAverageProcessData().get();
		int dataCount = StatisticsUtil.getProcessDataCount().get();
		long sumProcessData = StatisticsUtil.getSumProcessData().get();
		int freeID = StatisticsUtil.getFreeID();

		System.out.println("minProcessData: " + minProcessData);
		System.out.println("maxProcessData: " + maxProcessData);
		System.out.println("averageProcessData: " + averageProcessData);
		System.out.println("dataCount: " + dataCount);
		System.out.println("sumProcessData: " + sumProcessData);
		System.out.println("freeID: " + freeID);

		// assert correct data count
		assertEquals(numOfCycles, dataCount);
		// assert correct sum
		assertTrue(sumProcessData > (THREAD_COUNT * timeoutSum * nanoTimeFactor));
		// assert correct current free ID
		assertEquals(numOfCycles, freeID);

		// assert correct bounds for min, max, average
		assertTrue(minProcessData < (20 * nanoTimeFactor));
		assertTrue(minProcessData >= (10 * nanoTimeFactor));
		assertTrue(maxProcessData >= (50 * nanoTimeFactor));
		assertTrue(maxProcessData < (70 * nanoTimeFactor));
		assertTrue(averageProcessData >= (30 * nanoTimeFactor));
		assertTrue(averageProcessData < (40 * nanoTimeFactor));

	}

	/**
	 * this thread fakes read, process, send actions by sleeping for above
	 * defined timeout millis
	 * 
	 * @author manuelpras
	 * 
	 */
	class TestThreadNano extends Thread {

		@Override
		public void run() {
			for (int i = 0; i < TIMEOUTS.length; i++) {
				int id = StatisticsUtil.getFreeID();
				final int timeout = TIMEOUTS[i];

				// read start
				long start = currentNanos();
				try {
					sleep(timeout);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				long end = currentNanos();
				// read complete
				long timeDelta = end - start;
				StatisticsUtil.onNewReadComplete(id, timeDelta);

				// process data start
				start = currentNanos();
				try {
					sleep(timeout);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				end = currentNanos();
				// process data complete
				timeDelta = end - start;
				StatisticsUtil.onNewProcessData(id, timeDelta);

				// sent data start
				start = currentNanos();
				try {
					sleep(timeout);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				end = currentNanos();
				// sent data complete
				timeDelta = end - start;
				StatisticsUtil.onNewSendData(id, timeDelta);
			}
			latch.countDown();
		}

		private long currentNanos() {
			return System.nanoTime();
		}

	}

}
