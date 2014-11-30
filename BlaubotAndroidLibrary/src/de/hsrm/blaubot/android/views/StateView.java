package de.hsrm.blaubot.android.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import de.hsrm.blaubot.android.R;
import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import de.hsrm.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.util.Log;

/**
 * Android view to display informations about the StateMachine's state.
 * 
 * Add this view to a blaubot instance like this: stateView.registerBlaubotInstance(blaubot);
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class StateView extends FrameLayout {
	private TextView mCurrentStateTextView;
	private TextView mConnectedDevicesCountTextView;
	private Button mStartStopButton;
	private Handler mUiHandler;
	private Blaubot mBlaubot;

	public StateView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public StateView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}

	private void initView() {
		View view = inflate(getContext(), R.layout.blaubot_status_view, null);
		addView(view);
		mUiHandler = new Handler(Looper.getMainLooper());
		mCurrentStateTextView = (TextView) findViewById(R.id.blaubot_status_view_current_state);
		mConnectedDevicesCountTextView = (TextView) findViewById(R.id.blaubot_status_view_connected_devices_count);
		mStartStopButton = (Button) findViewById(R.id.blaubot_status_view_start_stop_button);
		mStartStopButton.setOnClickListener(new OnClickListener() {
			private String LOG_TAG = "StateView.mStartStopButton.onClickListener";

			@Override
			public void onClick(View v) {
				if(mBlaubot == null) {
					if(Log.logWarningMessages()) {
						Log.w(LOG_TAG , "Blaubot not set - ignoring onClick.");
					}
					return;
				}
				if(mBlaubot.isStarted()) {
					mBlaubot.stopBlaubot();
				} else {
					mBlaubot.startBlaubot();
				}
			}
		});
	}

	private IBlaubotConnectionManagerListener mConnectionManagerListener = new IBlaubotConnectionManagerListener() {
		private void updateUI() {
			mUiHandler.post(new Runnable() {
				@Override
				public void run() {
					int num = mBlaubot.getConnectionManager().getAllConnections().size();
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
	
	private volatile IBlaubotState currentBlaubotState;
	private IBlaubotConnectionStateMachineListener mBlaubotConnectionListener = new IBlaubotConnectionStateMachineListener() {
		
		private void updateUI() {
			mUiHandler.post(new Runnable() {
				@Override
				public void run() {
					if(mBlaubot.isStarted()) {
						mStartStopButton.setText("Stop Blaubot");
					} else {
						mStartStopButton.setText("Start Blaubot");
					}
					
					StringBuffer sb = new StringBuffer();
					sb.append("CurrentState: ");
					sb.append(currentBlaubotState.toString());
					sb.append("\n");
					mCurrentStateTextView.setText("CurrentState: " + currentBlaubotState);
				}
			});
		}
		
		@Override
		public void onStateChanged(IBlaubotState oldState, final IBlaubotState state) {
			currentBlaubotState = state;
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
			throw new IllegalStateException("There is already a blaubot instance registered with this StateView.");
		}
		this.mBlaubot = blaubot;
		this.currentBlaubotState = blaubot.getConnectionStateMachine().getCurrentState();
		this.mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(mBlaubotConnectionListener);
		this.mBlaubot.getConnectionManager().addConnectionListener(mConnectionManagerListener);
		
		// force some updates
		mConnectionManagerListener.onConnectionClosed(null);
		mBlaubotConnectionListener.onStateMachineStarted();
	}

}