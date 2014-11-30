package de.hsrm.blaubot.android.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import de.hsrm.blaubot.android.R;
import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import de.hsrm.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import de.hsrm.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;
import de.hsrm.blaubot.message.admin.AdminMessageFactory;
import de.hsrm.blaubot.message.admin.PrinceFoundAKingAdminMessage;
import de.hsrm.blaubot.protocol.IMessageListener;

/**
 * Android view to display informations about discovery events
 * 
 * Add this view to a blaubot instance like this: discoveryView.registerBlaubotInstance(blaubot);
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class DiscoveryView extends FrameLayout {
	private static final int MAX_CHARACTERS_IN_VIEW = 1000;
	private TextView mDiscoveryTextView;
	private Handler mUiHandler;
	private Blaubot mBlaubot;

	public DiscoveryView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public DiscoveryView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}

	private void initView() {
		View view = inflate(getContext(), R.layout.blaubot_discovery_view, null);
		addView(view);
		mUiHandler = new Handler(Looper.getMainLooper());
		mDiscoveryTextView = (TextView) view.findViewById(R.id.blaubot_discovery_view_textview);
	}

	private void updateDiscoveryTextView(final Object event) {
		mUiHandler.post(new Runnable() {
			@Override
			public void run() {
				String newText = event.toString() + "\n" + mDiscoveryTextView.getText();
				if(newText.length() > MAX_CHARACTERS_IN_VIEW) {
					newText = newText.substring(0, MAX_CHARACTERS_IN_VIEW);
				}
				mDiscoveryTextView.setText(newText);
			}
		});
	}

	private void clearDiscoveryTextView() {
		mUiHandler.post(new Runnable() {
			@Override
			public void run() {
				mDiscoveryTextView.setText("");
			}
		});
	}
	
	private IBlaubotConnectionStateMachineListener mBlaubotConnectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
		
		
		@Override
		public void onStateChanged(IBlaubotState oldState, final IBlaubotState state) {
			if(State.getStateByStatemachineClass(state.getClass()) == State.Free) {
				clearDiscoveryTextView();
			}
		}

		@Override
		public void onStateMachineStopped() {
			clearDiscoveryTextView();
		}

		@Override
		public void onStateMachineStarted() {
		}
	};
	
	private IBlaubotDiscoveryEventListener mDiscoveryEventListener = new IBlaubotDiscoveryEventListener() {
		@Override
		public void onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
			updateDiscoveryTextView(discoveryEvent);
		}
	};
	
	private IMessageListener connectionLayerAdminMessageListener = new IMessageListener() {
		@Override
		public void onMessage(BlaubotMessage message) {
			AbstractAdminMessage adminMessage = AdminMessageFactory.createAdminMessageFromRawMessage(message);
			if(adminMessage instanceof PrinceFoundAKingAdminMessage) {
				updateDiscoveryTextView((PrinceFoundAKingAdminMessage) adminMessage);
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
		this.mBlaubot.getConnectionStateMachine().addDiscoveryEventListener(mDiscoveryEventListener);
		
		// force some updates
		mBlaubotConnectionStateMachineListener.onStateMachineStarted();
	}

}