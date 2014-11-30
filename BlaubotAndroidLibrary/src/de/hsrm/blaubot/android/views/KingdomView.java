package de.hsrm.blaubot.android.views;

import java.util.Map.Entry;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import de.hsrm.blaubot.android.R;
import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import de.hsrm.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;
import de.hsrm.blaubot.message.admin.AdminMessageFactory;
import de.hsrm.blaubot.message.admin.CensusMessage;
import de.hsrm.blaubot.protocol.IMessageListener;

/**
 * Android view to display informations about the StateMachine's state.
 * 
 * Add this view to a blaubot instance like this: stateView.registerBlaubotInstance(blaubot);
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class KingdomView extends FrameLayout {
	private TextView mPeasantsTextView;
	private Handler mUiHandler;
	private Blaubot mBlaubot;

	public KingdomView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public KingdomView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}

	private void initView() {
		View view = inflate(getContext(), R.layout.blaubot_kingdom_view, null);
		addView(view);
		mUiHandler = new Handler(Looper.getMainLooper());
		mPeasantsTextView = (TextView) view.findViewById(R.id.blaubot_kingdom_view_peasants_textview);
	}

	private void updatePeasantsTextView(CensusMessage censusMessage) {
		StringBuffer sb = new StringBuffer();
		for(Entry<String, State> entry : censusMessage.getDeviceStates().entrySet()) {
			sb.append("(");
			sb.append(entry.getValue().name());
			sb.append(")\n");
			sb.append(entry.getKey());
			sb.append("\n--------\n");
		}
		final String s = new String(sb);
		mUiHandler.post(new Runnable() {
			@Override
			public void run() {
				mPeasantsTextView.setText(s);
			}
		});
	}

	private void clearPeasantsTextView() {
		mUiHandler.post(new Runnable() {
			@Override
			public void run() {
				mPeasantsTextView.setText("");
			}
		});
	}
	
	private IBlaubotConnectionManagerListener mConnectionManagerListener = new IBlaubotConnectionManagerListener() {
		
		@Override
		public void onConnectionClosed(IBlaubotConnection connection) {
		}
		
		@Override
		public void onConnectionEstablished(IBlaubotConnection connection) {
		}
	};
	
	private IBlaubotConnectionStateMachineListener mBlaubotConnectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
		
		
		@Override
		public void onStateChanged(IBlaubotState oldState, final IBlaubotState state) {
			if(State.getStateByStatemachineClass(state.getClass()) == State.Free) {
				clearPeasantsTextView();
			}
		}

		@Override
		public void onStateMachineStopped() {
			clearPeasantsTextView();
		}

		@Override
		public void onStateMachineStarted() {
		}
	};
	
	private IMessageListener connectionLayerAdminMessageListener = new IMessageListener() {
		
		@Override
		public void onMessage(BlaubotMessage message) {
			AbstractAdminMessage adminMessage = AdminMessageFactory.createAdminMessageFromRawMessage(message);
			if(adminMessage instanceof CensusMessage) {
				updatePeasantsTextView((CensusMessage) adminMessage);
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
			throw new IllegalStateException("There is already a blaubot instance registered with this StateView.");
		}
		this.mBlaubot = blaubot;
		this.mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(mBlaubotConnectionStateMachineListener);
		this.mBlaubot.getConnectionStateMachine().getStateMachineSession().getAdminBroadcastChannel().subscribe(connectionLayerAdminMessageListener);
		this.mBlaubot.getConnectionManager().addConnectionListener(mConnectionManagerListener);
		
		// force some updates
		mConnectionManagerListener.onConnectionClosed(null);
		mBlaubotConnectionStateMachineListener.onStateMachineStarted();
	}

}