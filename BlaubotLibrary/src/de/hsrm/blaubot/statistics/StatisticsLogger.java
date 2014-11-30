package de.hsrm.blaubot.statistics;

import de.hsrm.blaubot.util.Log;

/**
 * Thread for logging current statistics info of the StatisticsUtil to the log
 * output in a predefined interval.
 * The logged time values will be in micro seconds!!!
 * 
 * @author manuelpras
 * 
 */
public class StatisticsLogger extends Thread {

	private long interval;
	private static final String TAG = "StatisticsLogger";
	public static Thread currentThread;

	/**
	 * @param interval
	 *            Every interval Seconds the StatisticsLogger will log the
	 *            current stat info to the log output. Interval is in milli
	 *            seconds
	 */
	public StatisticsLogger(long interval) {
		this.interval = interval;
	}

	@Override
	public void run() {
		while (Thread.currentThread() == currentThread && !isInterrupted()) {
			try {
				sleep(interval);
			} catch (InterruptedException e) {
				break;
			}
			int readCompleteCount = StatisticsUtil.getReadCompleteCount().get();
			long minReadComplete = StatisticsUtil.getMinReadComplete().get();
			long maxReadComplete = StatisticsUtil.getMaxReadComplete().get();
			long averageReadComplete = StatisticsUtil.getAverageReadComplete().get();
			logData("Read", readCompleteCount, minReadComplete, maxReadComplete, averageReadComplete);

			int processDataCount = StatisticsUtil.getProcessDataCount().get();
			long minProcessData = StatisticsUtil.getMinProcessData().get();
			long maxProcessData = StatisticsUtil.getMaxProcessData().get();
			long averageProcessData = StatisticsUtil.getAverageProcessData().get();
			logData("Process", processDataCount, minProcessData, maxProcessData, averageProcessData);

			int sentDataCount = StatisticsUtil.getSentDataCount().get();
			long minSentData = StatisticsUtil.getMinSentData().get();
			long maxSentData = StatisticsUtil.getMaxSentData().get();
			long averageSentData = StatisticsUtil.getAverageSentData().get();
			logData("Sent", sentDataCount, minSentData, maxSentData, averageSentData);
			
			Log.d(TAG, "...");
		}
	}

	private void logData(String comment, int count, long min, long max, long average) {
		// incoming: nano sec, divide in order to get millis
		float devideBy = 1000 * 1000;
		float minOutput = min / devideBy;
		float maxOutputmax = max / devideBy;
		float averageOutput = average / devideBy;
		Log.d(TAG, String.format("%s: #%d (%.2f, %.2f, %.2f) ms", comment, count, minOutput, averageOutput, maxOutputmax));
	}

}
