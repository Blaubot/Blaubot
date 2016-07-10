package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotKingdomConnection;
import eu.hgross.blaubot.core.BlaubotServerConnector;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.KingState;
import eu.hgross.blaubot.ui.IBlaubotDebugView;

/**
 * Android view to display informations about the ServerConnector and the connection to the server
 * 
 * Add this view to a blaubot instance like this: view.registerBlaubotInstance(blaubot);
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class ServerConnectorView extends FrameLayout implements IBlaubotDebugView {
    /**
     * The interval to check the state of the ServerConnector and update the ui
     */
    public static final long UPDATE_INTERVAL = 1500;
	private ToggleButton mToggleButton;
    private View mServerConnectionInfoContainer;
    private ConnectionView mConnectionView;
    private TextView mConnectionInfoTextView;
    private CheckBox mHasServerConnectionCheckBox;
	private Handler mUiHandler;
	private Blaubot mBlaubot;
    private IBlaubotState currentSstate;
    private ScheduledExecutorService scheduledThreadPoolExecutor;
    private volatile boolean initalized = false;

    /**
     * Hacky way to update the state of the ServerConnector.
     */
    private Runnable updateUiTask = new Runnable() {
        @Override
        public void run() {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(!initalized) {
                        return;
                    }
                    if(mBlaubot != null) {
                        final BlaubotServerConnector serverConnector = mBlaubot.getServerConnector();
                        final IBlaubotConnection serverConnection = serverConnector != null ? serverConnector.getServerConnection() : null;
                        final BlaubotKingdomConnection currentlyUsedServerConnection = mBlaubot.getServerConnectionManager().getCurrentlyUsedServerConnection();

                        // gather data
                        boolean inKingState = currentSstate != null && currentSstate instanceof KingState;
                        boolean connectorIsSet = serverConnector != null;
                        boolean connectorIsActive = serverConnector != null && serverConnector.getDoConnect();
                        boolean hasConnection = serverConnector != null && serverConnection != null;
                        boolean hasUsedConnection = currentlyUsedServerConnection != null;

                        // if in kingstate, visualize which connection we are using to speak to the server
                        if (inKingState) {
                            final List<IBlaubotConnection> connectionsToServer = mBlaubot.getServerConnectionManager().getConnectionsToServer();
                            final int connectionsToServerCount = connectionsToServer.size();
                            mConnectionView.clearConnections();
                            mConnectionView.addConnections(connectionsToServer);

                            StringBuilder sb = new StringBuilder("<html><b>Using server connection:</b><br>");
                            if(hasUsedConnection) {
                                sb.append(currentlyUsedServerConnection.toString());
                            } else {
                                sb.append("No connection to server :-(");
                            }
                            mConnectionInfoTextView.setText(Html.fromHtml(sb.toString()));
                            mConnectionInfoTextView.setVisibility(VISIBLE);
                            mServerConnectionInfoContainer.setVisibility(VISIBLE);
                        } else {
                            mConnectionInfoTextView.setVisibility(GONE);
                            mServerConnectionInfoContainer.setVisibility(GONE);
                        }


                        // update ui
                        mToggleButton.setEnabled(true);
                        mToggleButton.setChecked(connectorIsActive);
                        mHasServerConnectionCheckBox.setChecked(hasConnection);
                    } else {
                        mHasServerConnectionCheckBox.setChecked(false);
                        mConnectionInfoTextView.setText("");
                        mToggleButton.setEnabled(false);
                        mToggleButton.setChecked(false);
                    }
                }
            });
        }
    };


    public ServerConnectorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context, attrs);
	}

	public ServerConnectorView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView(context, attrs);
	}


	private void initView(Context context, AttributeSet attrs) {
        this.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {

            @Override
            public void onViewAttachedToWindow(View v) {
                // start updates
                scheduledThreadPoolExecutor = Executors.newSingleThreadScheduledExecutor();
                scheduledThreadPoolExecutor.scheduleAtFixedRate(updateUiTask, (long) 0, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                // stop updates
                if (scheduledThreadPoolExecutor != null) {
                    scheduledThreadPoolExecutor.shutdown();
                    scheduledThreadPoolExecutor = null;
                }
            }
        });

        View view = inflate(getContext(), R.layout.blaubot_serverconnector_view, null);
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.StateView,
                    0, 0);
            try {
//                mShowOwnUniqueId = a.getBoolean(R.styleable.StateView_showOwnUniqueId, mShowOwnUniqueId);
            } finally {
                a.recycle();
            }
        }
        mConnectionInfoTextView = (TextView) view.findViewById(R.id.serverConnectorConnectionInfoTextView);
        mToggleButton = (ToggleButton) view.findViewById(R.id.serverConnectorToggleButton);
        mHasServerConnectionCheckBox = (CheckBox) view.findViewById(R.id.hasServerConnectionCheckBox);
        mServerConnectionInfoContainer = view.findViewById(R.id.serverConnectionInfoContainer);
        mConnectionView = (ConnectionView) view.findViewById(R.id.connectionView);
        mHasServerConnectionCheckBox.setEnabled(false);
        mToggleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBlaubot != null) {
                    BlaubotServerConnector serverConnector = mBlaubot.getServerConnector();
                    if (serverConnector != null) {
                        serverConnector.setDoConnect(mToggleButton.isChecked());
                    } else {
                        Toast.makeText(getContext(), "Can not activate: No server connector attached!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
		addView(view);
		mUiHandler = new Handler(Looper.getMainLooper());
        initalized = true;
	}


    private IBlaubotConnectionStateMachineListener connectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
        @Override
        public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
            currentSstate = newState;
        }

        @Override
        public void onStateMachineStopped() {

        }

        @Override
        public void onStateMachineStarted() {

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
        this.mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(connectionStateMachineListener);
	}

    @Override
    public void unregisterBlaubotInstance() {
        if(mBlaubot != null) {
            this.mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(connectionStateMachineListener);
            this.mBlaubot = null;
        }

    }

}