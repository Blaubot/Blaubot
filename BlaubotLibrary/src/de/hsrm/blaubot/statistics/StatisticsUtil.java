package de.hsrm.blaubot.statistics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Threadsafe Util for collecting statistics data
 * 
 * @author manuelpras
 * 
 */
public class StatisticsUtil {

	/**
	 * Save a timestamp together with a unique ID in order to update it later.
	 * After the last time measurement delete the entry from the hash map
	 */
	private static final ConcurrentHashMap<Integer, Long> times = new ConcurrentHashMap<Integer, Long>();
	private static AtomicLong minReadComplete, maxReadComplete, sumReadComplete, averageReadComplete;
	private static AtomicLong minProcessData, maxProcessData, sumProcessData, averageProcessData;
	private static AtomicLong minSentData, maxSentData, sumSentData, averageSentData;
	private static AtomicInteger readCompleteCount, processDataCount, sentDataCount;
	private static AtomicInteger freeID;
	static {
		reset();
	}
	
	/**
	 * resets all saved data such as time entries, min max ave sum values and
	 * data count. Invoke this method before starting using this class!
	 */
	public static void reset() {
		minReadComplete = new AtomicLong(-1);
		maxReadComplete = new AtomicLong(0);
		sumReadComplete = new AtomicLong(0);
		averageReadComplete = new AtomicLong(0);

		minProcessData = new AtomicLong(-1);
		maxProcessData = new AtomicLong(0);
		sumProcessData = new AtomicLong(0);
		averageProcessData = new AtomicLong(0);

		minSentData = new AtomicLong(-1);
		maxSentData = new AtomicLong(0);
		sumSentData = new AtomicLong(0);
		averageSentData = new AtomicLong(0);

		times.clear();

		readCompleteCount = new AtomicInteger(0);
		processDataCount = new AtomicInteger(0);
		sentDataCount = new AtomicInteger(0);

		freeID = new AtomicInteger(0);
	}

	/**
	 * Use this method in order to receive a id for identifying a time entry.
	 * Can be used to get an id for a BlaubotMessage
	 * 
	 * @return returns an id which wasn't returned since the last time the
	 *         reset() method of this class was called.
	 */
	public static int getFreeID() {
		return freeID.getAndIncrement();
	}

	/**
	 * @return current timestamp in nano seconds
	 */
	public static long getCurrentNanos() {
		return System.nanoTime();
	}

	// ###########################################################
	// GET PROCESS DATA
	// ###########################################################

	public static AtomicLong getMinProcessData() {
		return minProcessData;
	}

	public static AtomicLong getMaxProcessData() {
		return maxProcessData;
	}

	public static AtomicLong getSumProcessData() {
		return sumProcessData;
	}

	public static AtomicLong getAverageProcessData() {
		return averageProcessData;
	}

	// ###########################################################
	// GET SENT DATA
	// ###########################################################

	public static AtomicLong getMinSentData() {
		return minSentData;
	}

	public static AtomicLong getMaxSentData() {
		return maxSentData;
	}

	public static AtomicLong getSumSentData() {
		return sumSentData;
	}

	public static AtomicLong getAverageSentData() {
		return averageSentData;
	}

	// ###########################################################
	// GET READ COMPLETE
	// ###########################################################

	public static AtomicLong getMinReadComplete() {
		return minReadComplete;
	}

	public static AtomicLong getMaxReadComplete() {
		return maxReadComplete;
	}

	public static AtomicLong getSumReadComplete() {
		return sumReadComplete;
	}

	public static AtomicLong getAverageReadComplete() {
		return averageReadComplete;
	}

	// ###########################################################
	// GET DATA COUNTS
	// ###########################################################

	public static AtomicInteger getReadCompleteCount() {
		return readCompleteCount;
	}

	public static AtomicInteger getProcessDataCount() {
		return processDataCount;
	}

	public static AtomicInteger getSentDataCount() {
		return sentDataCount;
	}

	// ###########################################################
	// NEW DATA SECTION
	// ###########################################################

	public static void onNewReadComplete(int id, long timeDelta) {
		times.put(id, timeDelta);
		onNewData(timeDelta, minReadComplete, maxReadComplete, sumReadComplete, averageReadComplete, readCompleteCount);
	}

	public static void onNewSendData(int id, long timeDelta) {
		times.put(id, timeDelta);
		onNewData(timeDelta, minSentData, maxSentData, sumSentData, averageSentData, sentDataCount);
	}

	public static void onNewProcessData(int id, long timeDelta) {
		times.put(id, timeDelta);
		onNewData(timeDelta, minProcessData, maxProcessData, sumProcessData, averageProcessData, processDataCount);
	}

	// ###########################################################
	// PRIVATE HELPER METHODS
	// ###########################################################

	private static void onNewData(long timeDelta, AtomicLong minValue, AtomicLong maxValue, AtomicLong sumValue, AtomicLong averageValue, AtomicInteger dataCount) {
		dataCount.incrementAndGet();
		updateMinValue(minValue, timeDelta);
		updateMaxValue(maxValue, timeDelta);
		updateSumValue(sumValue, timeDelta);
		updateAverageValue(averageValue, timeDelta, sumValue, dataCount.get());
	}

	private static void updateMinValue(AtomicLong valueToUpdate, long timeDelta) {
		long oldValue;
		do {
			// get old value
			oldValue = valueToUpdate.get();
			// if old value < 0 set new value (-1 is initial min value)
			if (oldValue < 0) {
				break;
			}
			// if old value smaller than new one cancel method
			if (oldValue < timeDelta) {
				return;
			}
		}
		// while old value has changed in meantime (if concurrent access)
		while (!valueToUpdate.compareAndSet(oldValue, timeDelta));
		// if everything went fine (no concurrent access and new value less than
		// old one) update value
		valueToUpdate.set(timeDelta);
	}

	private static void updateMaxValue(AtomicLong valueToUpdate, long timeDelta) {
		long oldValue;
		do {
			// get old value
			oldValue = valueToUpdate.get();
			// if old value greater than new one cancel method
			if (oldValue > timeDelta) {
				return;
			}
		}
		// while old value has changed in meantime (if concurrent access)
		while (!valueToUpdate.compareAndSet(oldValue, timeDelta));
		// if everything went fine (no concurrent access and new value greater
		// than
		// old one) update value
		valueToUpdate.set(timeDelta);
	}

	private static void updateSumValue(AtomicLong valueToUpdate, long timeDelta) {
		valueToUpdate.addAndGet(timeDelta);
	}

	private static void updateAverageValue(AtomicLong valueToUpdate, long timeDelta, AtomicLong sum, int dataCount) {
		long oldValue;
		long average;
		do {
			// get old value
			oldValue = valueToUpdate.get();
			// calculate average
			average = sum.get() / dataCount;
		}
		// while old value has changed in meantime (if concurrent access)
		while (!valueToUpdate.compareAndSet(oldValue, average));
	}

	public static ConcurrentHashMap<Integer, Long> getTimes() {
		return times;
	}

}
