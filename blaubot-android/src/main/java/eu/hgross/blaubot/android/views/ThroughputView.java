package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.ui.BlaubotDebugViewConstants;
import eu.hgross.blaubot.ui.IBlaubotDebugView;
import eu.hgross.blaubot.util.Log;

/**
 * Android view to send and receive many bytes in an endless loop through a blaubot channel
 * 
 * Add this view to a blaubot instance like this: throughputview.registerBlaubotInstance(blaubot);
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class ThroughputView extends FrameLayout implements IBlaubotDebugView {
    private static final short CHANNEL_ID = BlaubotDebugViewConstants.THROUGHPUT_VIEW_CHANNEL_ID;
    private static final long UPDATE_INTERVAL = 150;
    private Button mStartButton;
    private Handler mUiHandler;
    private Blaubot mBlaubot;
    private IBlaubotChannel mChannel;
    private TextView mResultTextView;
    private ThroughputTester mThroughPutTester;
    private Timer mUpdateTimer;

    private static class ThroughputTester {
        /**
         * The size of one message in bytes to be sentBytes while testing
         */
        private static final short MESSAGE_SIZE = 1400;
        /**
         * The period in ms that is taken into account when getting the throughput.
         * Older packets are ignored
         */
        private static final int MEASURE_PERIOD = 2000; // ms
        private final IBlaubotChannel channel;
        private static final String LOG_TAG = "ThroughputTester";
        private final StartStopListener startStopListener;

        private long sentBytes = 0;
        private long sentMessages = 0;
        private long receivedBytes = 0;
        private long receivedMessages = 0;
        private Object sendLock = new Object();
        private Object receiveLock = new Object();
        private CircularFifoQueue<Pair<Long, Integer>> lastRecMessages; // timestamp, bytes
        private CircularFifoQueue<Pair<Long, Integer>> lastSendMessages; // timestamp, bytes

        private ExecutorService executorService;
        private volatile Runnable sendRunnable;

        interface StartStopListener {
            void onStart();

            void onStop();
        }

        class SendTask implements Runnable {
            private final Random random = new Random();

            @Override
            public void run() {
                startStopListener.onStart();
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "SendTask started");
                }
                reset();
                while (sendRunnable == this) {
                    byte[] data = new byte[MESSAGE_SIZE];
                    random.nextBytes(data);
                    channel.publish(data);
                    onMessageSent(data);
                }
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "SendTask finished");
                }
                startStopListener.onStop();
            }
        }

        ;

        public ThroughputTester(IBlaubotChannel channel, StartStopListener startStopListener) {
            this.startStopListener = startStopListener;
            this.channel = channel;
            this.lastRecMessages = new CircularFifoQueue<>(20);
            this.lastSendMessages = new CircularFifoQueue<>(20);
            channel.addMessageListener(new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage blaubotMessage) {
                    onMessageReceived(blaubotMessage.getPayload());
                }
            });
        }

        /**
         * Resets to the current timestamp
         */
        public void reset() {
            synchronized (sendLock) {
                sentBytes = 0;
                sentMessages = 0;
            }
            synchronized (receiveLock) {
                receivedBytes = 0;
                receivedMessages = 0;
            }
        }

        private void onMessageSent(byte[] msg) {
            final int size = msg.length;
            synchronized (sendLock) {
                lastSendMessages.add(new Pair<>(System.currentTimeMillis(), size));
                sentBytes += size;
                sentMessages += 1;
            }
        }

        private void onMessageReceived(byte[] msg) {
            final int size = msg.length;
            synchronized (receiveLock) {
                lastRecMessages.add(new Pair<>(System.currentTimeMillis(), size));
                receivedBytes += size;
                receivedMessages += 1;
            }
        }

        /**
         * The send throughput
         *
         * @return bytes / ms
         */
        public float getSendThroughput() {
            if (lastSendMessages.isEmpty()) {
                return 0;
            }

            final ArrayList<Pair<Long, Integer>> data;
            synchronized (sendLock) {
                data = new ArrayList<>(lastSendMessages);
            }
            final long now = System.currentTimeMillis();
            final long minTimestamp = now - MEASURE_PERIOD;
            long startTime = 0;
            int byteSum = 0;
            for (Pair pair : data) {
                final Long time = (Long) pair.first;
                if (time < minTimestamp) {
                    continue;
                } else if (startTime == 0) {
                    startTime = time;
                }
                final Integer bytes = (Integer) pair.second;
                byteSum += bytes;
            }
            final float timespan = (now - startTime);
            if (timespan == 0)
                return 0;
            return ((float) byteSum) / timespan;
        }

        /**
         * The receive throughput
         *
         * @return bytes / ms
         */
        public float getReceiveThroughput() {
            if (lastRecMessages.isEmpty()) {
                return 0;
            }

            final ArrayList<Pair<Long, Integer>> data;
            synchronized (receiveLock) {
                data = new ArrayList<>(lastRecMessages);
            }
            final long now = System.currentTimeMillis();
            final long minTimestamp = now - MEASURE_PERIOD;
            long startTime = 0;
            int byteSum = 0;
            for (Pair pair : data) {
                final Long time = (Long) pair.first;
                if (time < minTimestamp) {
                    continue;
                } else if (startTime == 0) {
                    startTime = time;
                }
                final Integer bytes = (Integer) pair.second;
                byteSum += bytes;
            }
            final float timespan = (now - startTime);
            if (timespan == 0)
                return 0;
            return ((float) byteSum) / timespan;
        }

        public long getAvgBytesPerReceivedMessage() {
            return receivedMessages / receivedBytes;
        }

        public long getAvgBytesPerSentMessage() {
            return sentMessages / sentBytes;
        }

        public synchronized void start() {
            stop();
            executorService = Executors.newSingleThreadExecutor();
            sendRunnable = new SendTask();
            executorService.execute(sendRunnable);
        }

        public synchronized void stop() {
            this.sendRunnable = null;
            if (this.executorService != null) {
                this.executorService.shutdown();
                try {
                    this.executorService.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public synchronized boolean isStarted() {
            return sendRunnable != null;
        }

    }

    public ThroughputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public ThroughputView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        View view = inflate(context, R.layout.blaubot_throughput_view, null);
        mUiHandler = new Handler(Looper.getMainLooper());

        addView(view);
        mResultTextView = (TextView) findViewById(R.id.resultTextView);
        mResultTextView.setMaxLines(2);
        mResultTextView.setLines(2);
        mStartButton = (Button) findViewById(R.id.startButton);
        mStartButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mThroughPutTester.isStarted()) {
                    mThroughPutTester.stop();
                } else {
                    mThroughPutTester.start();
                }
                mStartButton.setEnabled(false);
            }
        });
    }


    /**
     * Listens to the used channel of the throughput tester and updates the ui on new messages
     */
    private IBlaubotMessageListener mMessageListener = new IBlaubotMessageListener() {

        @Override
        public void onMessage(BlaubotMessage blaubotMessage) {

        }
    };

    /**
     * Register this view with the given blaubot instance
     *
     * @param blaubot the blaubot instance to connect with
     */
    public void registerBlaubotInstance(Blaubot blaubot) {
        if (mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        this.mChannel = this.mBlaubot.createChannel(CHANNEL_ID);
        this.mChannel.getChannelConfig().setPriority(BlaubotMessage.Priority.LOW);
        this.mChannel.subscribe();
        this.mThroughPutTester = new ThroughputTester(mChannel, new ThroughputTester.StartStopListener() {
            @Override
            public void onStart() {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mStartButton.setEnabled(true);
                        mStartButton.setText(" Stop measurement");
                    }
                });
            }

            @Override
            public void onStop() {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mStartButton.setEnabled(true);
                        mStartButton.setText(" Start measurement");
                    }
                });
            }
        });
        this.mChannel.addMessageListener(mMessageListener);
        final TimerTask timerTask = new TimerTask() {

            @Override
            public void run() {
                final float receiveThroughput = mThroughPutTester.getReceiveThroughput() * 1000;
                final float sendThroughput = mThroughPutTester.getSendThroughput() * 1000;
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final String humanReadbaleRx = ViewUtils.humanReadableByteCount((int) receiveThroughput, false);
                        final String humanReadbaleTx = ViewUtils.humanReadableByteCount((int) sendThroughput, false);
                        StringBuilder sb = new StringBuilder("Rx: ");
                        sb.append(humanReadbaleRx);
                        sb.append("/s\n");
                        sb.append("Tx: ");
                        sb.append(humanReadbaleTx);
                        sb.append("/s");
                        mResultTextView.setText(sb.toString());
                    }
                });
            }
        };
        this.mUpdateTimer = new Timer();
        this.mUpdateTimer.scheduleAtFixedRate(timerTask, 0, UPDATE_INTERVAL);
    }

    @Override
    public void unregisterBlaubotInstance() {
        if (mBlaubot != null) {
            this.mChannel.unsubscribe();
            this.mChannel.removeMessageListener(mMessageListener);
            this.mBlaubot = null;
        }
        if (mUpdateTimer != null) {
            mUpdateTimer.cancel();
            mUpdateTimer = null;
        }
    }


}