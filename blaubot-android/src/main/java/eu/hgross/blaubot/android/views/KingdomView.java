package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.CensusMessage;
import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.ui.IBlaubotDebugView;

/**
 * Android view to display informations about the StateMachine's state.
 * 
 * Add this view to a blaubot instance like this: stateView.registerBlaubotInstance(blaubot);
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class KingdomView extends LinearLayout implements IBlaubotDebugView {
	private Handler mUiHandler;
	private Blaubot mBlaubot;
    private Context mContext;

    public KingdomView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
	}

	public KingdomView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView(context);
	}

	private void initView(Context context) {
        this.mContext = context;
		mUiHandler = new Handler(Looper.getMainLooper());
	}

    private final static String NO_CENSUS_MESSAGE_SO_FAR_TEXT = "Got no census message so far";
	private void updateUI(final CensusMessage censusMessage) {
		mUiHandler.post(new Runnable() {
			@Override
			public void run() {
                final List<View> stateItems = new ArrayList<>();

                if(censusMessage != null) {
                    final Set<Entry<String, State>> entries = censusMessage.getDeviceStates().entrySet();
                    for(Entry<String, State> entry : entries) {
                        final String uniqueDeviceId = entry.getKey();
                        final State state = entry.getValue();
						View item = createKingdomViewListItem(mContext, state, uniqueDeviceId);
						stateItems.add(item);
                    }
                }

                // Never got a message
                if(stateItems.isEmpty()) {
                    TextView tv = new TextView(mContext);
                    tv.setText(NO_CENSUS_MESSAGE_SO_FAR_TEXT);
                    stateItems.add(tv);
                }

                removeAllViews();
                for(View v : stateItems) {
                    addView(v);
                }
			}
		});
	}

	/**
	 * Creates a kingdom view list item
	 * 
	 * @param context the context
	 * @param state the state of the device to visualize
	 * @param uniqueDeviceId the unique device id
	 * @return the constructed view
	 */
	public static View createKingdomViewListItem(Context context, State state, String uniqueDeviceId) {
		final Drawable icon = ViewUtils.getDrawableForBlaubotState(context, state);
		View item = inflate(context, R.layout.blaubot_kingdom_view_list_item, null);
		TextView uniqueDeviceIdTextView = (TextView) item.findViewById(R.id.uniqueDeviceIdLabel);
		TextView stateTextView = (TextView) item.findViewById(R.id.stateLabel);
		ImageView iconImageView = (ImageView) item.findViewById(R.id.stateIcon);
		iconImageView.setImageDrawable(icon);
		uniqueDeviceIdTextView.setText(uniqueDeviceId);
		stateTextView.setText(state.toString());
		return item;
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
				updateUI(null);
			}
		}

		@Override
		public void onStateMachineStopped() {
			updateUI(null);
		}

		@Override
		public void onStateMachineStarted() {
		}
	};
	
	private IBlaubotAdminMessageListener connectionLayerAdminMessageListener = new IBlaubotAdminMessageListener() {
		
		@Override
		public void onAdminMessage(AbstractAdminMessage adminMessage) {
        if(adminMessage instanceof CensusMessage) {
            updateUI((CensusMessage) adminMessage);
        }
		}
	};

	/**
	 * Register this view with the given blaubot instance
	 * 
	 * @param blaubot
	 *            the blaubot instance to connect with
	 */
    @Override
	public void registerBlaubotInstance(Blaubot blaubot) {
		if (mBlaubot != null) {
			unregisterBlaubotInstance();
		}
		this.mBlaubot = blaubot;
		this.mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(mBlaubotConnectionStateMachineListener);
		this.mBlaubot.getChannelManager().addAdminMessageListener(connectionLayerAdminMessageListener);
		this.mBlaubot.getConnectionManager().addConnectionListener(mConnectionManagerListener);

        // update
        updateUI(null);
	}

    @Override
    public void unregisterBlaubotInstance() {
        if(mBlaubot != null) {
            this.mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(mBlaubotConnectionStateMachineListener);
            this.mBlaubot.getChannelManager().removeAdminMessageListener(connectionLayerAdminMessageListener);
            this.mBlaubot.getConnectionManager().removeConnectionListener(mConnectionManagerListener);
        }
        // force some updates
        updateUI(null);
    }

}