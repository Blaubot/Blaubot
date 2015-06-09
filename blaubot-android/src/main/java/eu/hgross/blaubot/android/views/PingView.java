package eu.hgross.blaubot.android.views;

import android.content.Context;
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

import java.util.Date;

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

/**
 * Android view to send and receive pings on a blaubot channel
 * 
 * Add this view to a blaubot instance like this: pingView.registerBlaubotInstance(blaubot);
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class PingView extends FrameLayout implements IBlaubotDebugView {
    private static final short CHANNEL_ID = BlaubotDebugViewConstants.PING_VIEW_CHANNEL_ID;
    private static final int VIBRATE_TIME = 350;
    private TextView mLastReceivedPingTextView;
	private Button mPingButton;
	private Handler mUiHandler;
	private Blaubot mBlaubot;
    private IBlaubotChannel mChannel;
    private Vibrator mVibrator;
    private boolean mUseVibrator;

    public PingView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context, attrs);
	}

	public PingView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView(context, attrs);
	}

	private void initView(Context context, AttributeSet attrs) {
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
                if(mChannel != null) {
                    String msg = "Ping!";
                    final boolean published = mChannel.publish(msg.getBytes(BlaubotConstants.STRING_CHARSET));
                    if (!published) {
                        Toast.makeText(getContext(), "Ping was not sent: Queue is full", Toast.LENGTH_LONG).show();
                    }
                }
			}
		});
	}


    private ILifecycleListener mLifecycleListener = new ILifecycleListener() {
        @Override
        public void onConnected() {
            mChannel.subscribe(mMessageListener);
        }

        @Override
        public void onDisconnected() {
            mChannel.removeMessageListener(mMessageListener);
        }

        @Override
        public void onDeviceJoined(IBlaubotDevice blaubotDevice) {

        }

        @Override
        public void onDeviceLeft(IBlaubotDevice blaubotDevice) {

        }

        @Override
        public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {

        }

        @Override
        public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {

        }
    };

    private IBlaubotMessageListener mMessageListener = new IBlaubotMessageListener() {
        @Override
        public void onMessage(BlaubotMessage blaubotMessage) {
            final String msg = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                    mLastReceivedPingTextView.setText("" + new Date());
                }
            });
            if(mVibrator != null && mUseVibrator) {
                mVibrator.vibrate(VIBRATE_TIME);
            }
        }
    };

	/**
	 * Register this view with the given blaubot instance
	 * 
	 * @param blaubot
	 *            the blaubot instance to connect with
	 */
	public void registerBlaubotInstance(Blaubot blaubot) {
		if (mBlaubot != null) {
			unregisterBlaubotInstance();
		}
		this.mBlaubot = blaubot;
        this.mChannel = this.mBlaubot.createChannel(CHANNEL_ID);
		this.mBlaubot.addLifecycleListener(mLifecycleListener);
        if(mUseVibrator) {
            mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        }

	}

    @Override
    public void unregisterBlaubotInstance() {
        if(mBlaubot != null) {
            this.mBlaubot.removeLifecycleListener(mLifecycleListener);
            this.mBlaubot = null;
        }
    }

}