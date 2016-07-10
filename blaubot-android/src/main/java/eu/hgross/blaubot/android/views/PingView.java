package eu.hgross.blaubot.android.views;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.ui.BlaubotDebugViewConstants;
import eu.hgross.blaubot.ui.IBlaubotDebugView;
import eu.hgross.blaubot.ui.PingMessage;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.util.PingMeasurer;
import eu.hgross.blaubot.util.PingMeasurerResult;

/**
 * Android view to send and receive pings on a blaubot channel
 * 
 * Add this view to a blaubot instance like this: pingView.registerBlaubotInstance(blaubot);
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class PingView extends FrameLayout implements IBlaubotDebugView {
    private static final short CHANNEL_ID = BlaubotDebugViewConstants.PING_VIEW_CHANNEL_ID;
    private static final int VIBRATE_TIME = 350;
    /**
     * Number of Ping messages to be sent by the measurement when a long click on the ping button occurs
     */
    public static final int NUMBER_OF_PINGS_FOR_MEASURE = 50;
    private static final String LOG_TAG = "PingView";
    private TextView mLastReceivedPingTextView;
    private Button mPingButton;
    private Handler mUiHandler;
    private Blaubot mBlaubot;
    private IBlaubotChannel mChannel;
    private Vibrator mVibrator;
    private boolean mUseVibrator;
    private Gson mGson;

    public PingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public PingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        mGson = new Gson();
        View view = inflate(context, R.layout.blaubot_ping_view, null);
        mUiHandler = new Handler(Looper.getMainLooper());


        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.PingView,
                    0, 0);
            try {
                mUseVibrator = a.getBoolean(R.styleable.PingView_vibrateOnPing, mUseVibrator);
            } finally {
                a.recycle();
            }
        }

        addView(view);
        mLastReceivedPingTextView = (TextView) findViewById(R.id.lastReceivedPingTimestamp);
        mPingButton = (Button) findViewById(R.id.pingButton);
        mPingButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mChannel != null) {
                    PingMessage pingMessage = new PingMessage();
                    pingMessage.setSenderUniqueDeviceId(mBlaubot.getOwnDevice().getUniqueDeviceID());
                    pingMessage.setTimestamp(System.currentTimeMillis());
                    String serialized = mGson.toJson(pingMessage);
                    final boolean published = mChannel.publish(serialized.getBytes(BlaubotConstants.STRING_CHARSET));
                    if (!published) {
                        Toast.makeText(getContext(), "Ping was not sent: Queue is full", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        mPingButton.setLongClickable(true);
        mPingButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mChannel != null && mBlaubot != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            PingMeasurer measurer = new PingMeasurer(mChannel, mBlaubot.getOwnDevice());
                            final Future<PingMeasurerResult> measure = measurer.measure(NUMBER_OF_PINGS_FOR_MEASURE);
                            try {
                                final PingMeasurerResult pingMeasurerResult = measure.get();
                                if (Log.logDebugMessages()) {
                                    Log.d(LOG_TAG, "PingMeasurement completed: " + pingMeasurerResult);
                                }

                                mUiHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // custom view
                                        PingMeasureResultView pingMeasureResultView = new PingMeasureResultView(getContext());
                                        pingMeasureResultView.setPingMeasureResult(pingMeasurerResult);

                                        // show dialog
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                        builder.setView(pingMeasureResultView);
                                        builder.setTitle("Ping measurement result");
                                        builder.setPositiveButton("close", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                            }
                                        });
                                        final AlertDialog alertDialog = builder.create();


                                        alertDialog.show();
                                    }
                                });

                            } catch (InterruptedException e) {
                            } catch (ExecutionException e) {
                            }
                        }
                    }).start();


                } else {
                    Toast.makeText(getContext(), "Error: No Blaubot instance registered!", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
    }


    private IBlaubotMessageListener mMessageListener = new IBlaubotMessageListener() {
        @Override
        public void onMessage(BlaubotMessage blaubotMessage) {
            final Date receivedDate = new Date();
            final String msg = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
            final PingMessage pingMessage = mGson.fromJson(msg, PingMessage.class);

            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(), pingMessage.toString(), Toast.LENGTH_SHORT).show();
                    StringBuilder sb = new StringBuilder(receivedDate.toString());
                    if (pingMessage.getSenderUniqueDeviceId().equals(mBlaubot.getOwnDevice().getUniqueDeviceID())) {
                        // append RTT
                        long rtt = receivedDate.getTime() - pingMessage.getTimestamp();
                        sb.append("\n");
                        sb.append("RTT: ");
                        sb.append(rtt);
                        sb.append(" ms");
                    }
                    mLastReceivedPingTextView.setText(sb.toString());
                }
            });
            if (mVibrator != null && mUseVibrator) {
                mVibrator.vibrate(VIBRATE_TIME);
            }
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
        this.mChannel.getChannelConfig().setTransmitReflexiveMessages(true); // needed to measure a meaningful rtt
        this.mChannel.subscribe(mMessageListener);
//        this.mBlaubot.addLifecycleListener(new ILifecycleListener() {
//            @Override
//            public void onConnected() {
//                final PingMessage src = new PingMessage();
//                src.setTimestamp(System.currentTimeMillis());
//                src.setSenderUniqueDeviceId(mBlaubot.getOwnDevice().getUniqueDeviceID());
//                mChannel.publish(mGson.toJson(src).getBytes()); 
//            }
//
//            @Override
//            public void onDisconnected() {
//
//            }
//
//            @Override
//            public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
//
//            }
//
//            @Override
//            public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
//
//            }
//
//            @Override
//            public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
//
//            }
//
//            @Override
//            public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
//
//            }
//        });
        if (mUseVibrator) {
            mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        }

    }

    @Override
    public void unregisterBlaubotInstance() {
        if (mBlaubot != null) {
            this.mChannel.unsubscribe();
            this.mChannel.removeMessageListener(mMessageListener);
            this.mBlaubot = null;
        }
    }

}