package eu.hgross.blaubot.util;

/**
 * Pojo for the ping measurers results
 */
public class PingMeasurerResult {
    private final long mMin;
    private final long mMax;
    private final float mAvg;
    private final long mSentBytes;
    private final int mNumberOfPings;

    public PingMeasurerResult(long min, long max, float avg, long sentBytes, int numberOfPings) {
        this.mMin = min;
        this.mMax = max;
        this.mAvg = avg;
        this.mSentBytes = sentBytes;
        this.mNumberOfPings = numberOfPings;
    }

    /**
     * The fastest RTT time
     *
     * @return RTT time in ms
     */
    public long getMinRtt() {
        return mMin;
    }

    /**
     * The slowest RTT time
     *
     * @return RTT time in ms
     */
    public long getMaxRtt() {
        return mMax;
    }

    /**
     * The averaged RTT
     *
     * @return RTT time in ms
     */
    public float getAvgRtt() {
        return mAvg;
    }

    /**
     * Number of bytes sent during the ping measurement
     *
     * @return number of bytes
     */
    public long getSentBytes() {
        return mSentBytes;
    }

    /**
     * Number of ping messages sent during the ping measurement
     *
     * @return number of ping messages send
     */
    public int getNumberOfPings() {
        return mNumberOfPings;
    }

    /**
     * The size of the ping message
     *
     * @return number of bytes
     */
    public int getPingMessageSize() {
        return (int) (mSentBytes / mNumberOfPings);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("PingMeasurerResult{");
        sb.append("mMin=").append(mMin);
        sb.append(", mMax=").append(mMax);
        sb.append(", mAvg=").append(mAvg);
        sb.append(", mSentBytes=").append(mSentBytes);
        sb.append(", mNumberOfPings=").append(mNumberOfPings);
        sb.append('}');
        return sb.toString();
    }
}