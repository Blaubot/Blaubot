package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.ui.IBlaubotDebugView;
import eu.hgross.blaubot.util.KingdomCensusLifecycleListener;
import eu.hgross.blaubot.util.Log;

/**
 * Android view to display informations about the StateMachine's state.
 * 
 * Add this view to a blaubot instance like this: stateView.registerBlaubotInstance(blaubot);
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class StateView extends FrameLayout implements IBlaubotDebugView {
	private TextView mCurrentStateTextView;
	private TextView mConnectedDevicesCountTextView;
    private TextView mKingdomDevicesCountTextView;
    private ImageView mStateImageView;
	private Button mStartStopButton;
	private Handler mUiHandler;
	private Blaubot mBlaubot;
    private boolean mShowOwnUniqueId = false;
    private TextView mOwnUniqueIdTextView;
    private KingdomCensusLifecycleListener mKingdomCensusLifecycleListener;

    public StateView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context, attrs);
	}

	public StateView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView(context, attrs);
	}


	private void initView(Context context, AttributeSet attrs) {
		View view = inflate(getContext(), R.layout.blaubot_status_view, null);
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.StateView,
                    0, 0);
            try {
                mShowOwnUniqueId = a.getBoolean(R.styleable.StateView_showOwnUniqueId, mShowOwnUniqueId);
            } finally {
                a.recycle();
            }
        }

		addView(view);
		mUiHandler = new Handler(Looper.getMainLooper());
		mCurrentStateTextView = (TextView) findViewById(R.id.blaubot_status_view_current_state);
		mConnectedDevicesCountTextView = (TextView) findViewById(R.id.blaubot_status_view_connected_devices_count);
        mOwnUniqueIdTextView = (TextView) findViewById(R.id.blaubot_status_view_ownUnqiueId);
        mOwnUniqueIdTextView.setVisibility(mShowOwnUniqueId ? VISIBLE : GONE);
        mKingdomDevicesCountTextView = (TextView) findViewById(R.id.blaubot_status_view_kigndom_devices_count);
        mStateImageView = (ImageView) findViewById(R.id.blaubot_status_view_state_image);
		mStartStopButton = (Button) findViewById(R.id.blaubot_status_view_start_stop_button);
		mStartStopButton.setOnClickListener(new OnClickListener() {
			private String LOG_TAG = "StateView.mStartStopButton.onClickListener";

			@Override
			public synchronized void onClick(View v) {
				if(mBlaubot == null) {
					if(Log.logWarningMessages()) {
						Log.w(LOG_TAG , "Blaubot not set - ignoring onClick.");
					}
					return;
				}
                mStartStopButton.setEnabled(false);
                if(mBlaubot.isStarted()) {
                    mBlaubot.stopBlaubot();
                } else {
                    mBlaubot.startBlaubot();
                }
			}
		});
	}

    private final IBlaubotConnectionStateMachineListener mConnectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
        @Override
        public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
            // not used
        }

        @Override
        public void onStateMachineStopped() {
            setButtonText();
        }

        @Override
        public void onStateMachineStarted() {
            setButtonText();
        }
    };

	private IBlaubotConnectionManagerListener mConnectionManagerListener = new IBlaubotConnectionManagerListener() {
		private void updateUI() {
            final int num;
            if(mBlaubot != null) {
                num = mBlaubot.getConnectionManager().getAllConnections().size();
            } else {
                num = 0;
            }
			mUiHandler.post(new Runnable() {
				@Override
				public void run() {
					String text = String.format(getResources().getString(R.string.blaubot_status_view_connected_devices_count), num);
					mConnectedDevicesCountTextView.setText(text);
				}
			});
		}
		
		@Override
		public void onConnectionClosed(IBlaubotConnection connection) {
			updateUI();
		}
		
		@Override
		public void onConnectionEstablished(IBlaubotConnection connection) {
			updateUI();
		}
	};

    private void setButtonText() {
        final boolean started = mBlaubot.isStarted();
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // await either started or stopped
                final Resources resources = getContext().getResources();
                final Drawable img;
                if(started) {
                    img = resources.getDrawable(R.drawable.ic_media_stop);
                    mStartStopButton.setText("Stop");
                } else {
                    img = resources.getDrawable(R.drawable.ic_media_play);
                    mStartStopButton.setText("Start");
                }
                mStartStopButton.setCompoundDrawablesWithIntrinsicBounds( img, null, null, null);
                mStartStopButton.setEnabled(true);
            }
        });
    }

	private volatile IBlaubotState mCurrentBlaubotState;
	private IBlaubotConnectionStateMachineListener mBlaubotConnectionListener = new IBlaubotConnectionStateMachineListener() {

		private void updateUI() {
			mUiHandler.post(new Runnable() {
				@Override
				public void run() {
                    if(mCurrentBlaubotState != null) {
                        mCurrentStateTextView.setText(mCurrentBlaubotState.toString());
                        mStateImageView.setImageDrawable(ViewUtils.getDrawableForBlaubotState(getContext(), mCurrentBlaubotState));
                    } else {
                        mCurrentStateTextView.setText("no registered instance");
                    }
				}
			});
		}
		
		@Override
		public void onStateChanged(IBlaubotState oldState, final IBlaubotState state) {
			mCurrentBlaubotState = state;
			updateUI();
		}

		@Override
		public void onStateMachineStopped() {
			updateUI();
		}

		@Override
		public void onStateMachineStarted() {
			updateUI();
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
        this.mKingdomCensusLifecycleListener = new KingdomCensusLifecycleListener(mBlaubot.getOwnDevice()) {
            {
                updateUI();
            }

            private void updateUI() {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final long size = getDevices().size();
                        mKingdomDevicesCountTextView.setText("Kingdom size: " + size);
                    }
                });
            }

            @Override
            public void onConnected() {
                super.onConnected();
                updateUI();
            }

            @Override
            public void onDisconnected() {
                super.onDisconnected();
                updateUI();
            }

            @Override
            public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
                super.onDeviceJoined(blaubotDevice);
                updateUI();
            }

            @Override
            public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
                super.onDeviceLeft(blaubotDevice);
                updateUI();
            }
        };
        this.mBlaubot.addLifecycleListener(mKingdomCensusLifecycleListener);
        this.mCurrentBlaubotState = blaubot.getConnectionStateMachine().getCurrentState();
        this.mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(mBlaubotConnectionListener);
        this.mBlaubot.getConnectionManager().addConnectionListener(mConnectionManagerListener);
        this.mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(mConnectionStateMachineListener);
        this.mOwnUniqueIdTextView.setText(mBlaubot.getOwnDevice().getUniqueDeviceID());

		// force some updates
		mConnectionManagerListener.onConnectionClosed(null);
		mBlaubotConnectionListener.onStateMachineStarted();
        setButtonText();
	}

    @Override
    public void unregisterBlaubotInstance() {
        if(mBlaubot != null) {
            this.mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(mBlaubotConnectionListener);
            this.mBlaubot.getConnectionManager().removeConnectionListener(mConnectionManagerListener);
            this.mBlaubot.removeLifecycleListener(mKingdomCensusLifecycleListener);
            this.mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(mConnectionStateMachineListener);
            this.mBlaubot = null;
            this.mKingdomCensusLifecycleListener = null;
            this.mCurrentBlaubotState = null;
        }

        // force some updates
        mConnectionManagerListener.onConnectionClosed(null);
        mBlaubotConnectionListener.onStateMachineStarted();
    }

}