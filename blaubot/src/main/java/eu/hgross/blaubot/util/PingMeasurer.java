package eu.hgross.blaubot.util;

import com.google.gson.Gson;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.ui.PingMessage;

/**
 * Sends n PingMessages, awaits their echo and calculates the min,max,avg values.
 */
public class PingMeasurer {
    private static final String LOG_TAG = "PingMeasurer";
    /**
     * Max amount of ms for one ping message before we time out
     */
    private static final long PING_MEASURE_TIMEOUT = 25000;
    private final IBlaubotChannel mChannel;
    private final IBlaubotDevice mOwnDevice;
    private final Gson mGson = new Gson();

    public PingMeasurer(IBlaubotChannel channel, IBlaubotDevice ownDevice) {
        this.mChannel = channel;
        this.mOwnDevice = ownDevice;
    }


    /**
     * Sends one ping message
     *
     * @return number of bytes of the ping message
     */
    private int sendPingMessage() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Sending ping ...");
        }
        PingMessage pingMessage = new PingMessage();
        pingMessage.setSenderUniqueDeviceId(mOwnDevice.getUniqueDeviceID());
        pingMessage.setTimestamp(System.currentTimeMillis());
        String serialized = mGson.toJson(pingMessage);
        final byte[] bytes = serialized.getBytes(BlaubotConstants.STRING_CHARSET);
        mChannel.publish(bytes);
        return bytes.length;
    }

    /**
     * Measure the RTT by sending n pings and awaitng the echos.
     *
     * @param n the number of pings to send
     * @return the result of the RTT measurement
     */
    public Future<PingMeasurerResult> measure(final int n) {
        FutureTask<PingMeasurerResult> future = new FutureTask<PingMeasurerResult>(new Callable<PingMeasurerResult>() {
            long bytesSent = 0;
            long min = -1;
            long max = -1;
            long sum = 0;
            int i = 0;

            public PingMeasurerResult call() {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Measuring RTT with " + n + " messages ...");
                }
                final CountDownLatch latch = new CountDownLatch(n);
                mChannel.addMessageListener(new IBlaubotMessageListener() {
                    @Override
                    public void onMessage(BlaubotMessage blaubotMessage) {
                        final Date receivedDate = new Date();
                        final String msg = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                        final PingMessage pingMessage = mGson.fromJson(msg, PingMessage.class);
                        long rtt = receivedDate.getTime() - pingMessage.getTimestamp();
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Got ping (RTT=" + rtt + ")");
                        }
                        if (min < 0 || rtt < min) {
                            min = rtt;
                        }
                        if (max < 0 || rtt > max) {
                            max = rtt;
                        }
                        sum += rtt;
                        i += 1;

                        if (n - i > 0) {
                            // we need to send another
                            bytesSent += sendPingMessage();
                        }
                        latch.countDown();
                    }
                });

                if (n - i > 0) {
                    bytesSent += sendPingMessage();
                }

                try {
                    final boolean timedOut = !latch.await(PING_MEASURE_TIMEOUT * n, TimeUnit.MILLISECONDS);
                    if (timedOut) {
                        if (Log.logWarningMessages()) {
                            Log.w(LOG_TAG, "PingMeasurement could not be completed (timeout of " + PING_MEASURE_TIMEOUT * n + " ms occured first)");
                        }
                    }
                } catch (InterruptedException e) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "PingMeasurement has been interrupted");
                    }
                }

                final float avg = ((float) sum) / ((float) i);
                return new PingMeasurerResult(min, max, avg, bytesSent, i);
            }
        });

        new Thread(future).start();
        return future;
    }

}