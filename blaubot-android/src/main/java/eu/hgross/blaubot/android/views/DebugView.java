package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import java.util.Arrays;
import java.util.List;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.ui.IBlaubotDebugView;

/**
 * This is a big generic debug view for Blaubot implementations showing off all kinds of information
 * of what is going on inside of Blaubot.
 */
public class DebugView extends FrameLayout implements IBlaubotDebugView {
    private static final String LOG_TAG = DebugView.class.getName();

    private StateView mBlaubotStateView;
    private ServerConnectorView mServerConnectorView;
    private ConnectionView mConnectionView;
    private KingdomView mKingdomView;
    private ChannelManagerView mChannelManagerView;
    private LifeCycleView mLifeCycleView;
    private AdminMessageView mAdminMessageView;
    private StateHistoryView mStateHistoryView;
    private BeaconView mBeaconView;
    private PingView mPingView;
    private ThroughputView mThroughputView;
    private List<IBlaubotDebugView> mAllViews;

    public DebugView(Context context) {
        super(context);
        initView(context, null);
    }

    public DebugView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        View view = inflate(getContext(), R.layout.blaubot_debug_view, null);
        addView(view);
        this.mBlaubotStateView = (StateView) findViewById(R.id.debug_view_StateView);
        this.mServerConnectorView = (ServerConnectorView) findViewById(R.id.debug_view_ServerConnectorView);
        this.mConnectionView = (ConnectionView) findViewById(R.id.debug_view_ConnectionView);
        this.mKingdomView = (KingdomView) findViewById(R.id.debug_view_KingdomView);
        this.mAdminMessageView = (AdminMessageView) findViewById(R.id.debug_view_AdminMessageView);
        this.mChannelManagerView = (ChannelManagerView) findViewById(R.id.debug_view_channelManagerView);
        this.mLifeCycleView = (LifeCycleView) findViewById(R.id.debug_view_LifeCycleView);
        this.mStateHistoryView = (StateHistoryView) findViewById(R.id.debug_view_StateHistoryView);
        this.mBeaconView = (BeaconView) findViewById(R.id.debug_view_BeaconView);
        this.mPingView = (PingView) findViewById(R.id.debug_view_pingView);
        this.mThroughputView = (ThroughputView) findViewById(R.id.debug_view_throughputView);
        this.mAllViews = Arrays.asList(new IBlaubotDebugView[]{mBlaubotStateView, mServerConnectorView, mConnectionView, mKingdomView, mAdminMessageView, mChannelManagerView, mLifeCycleView, mStateHistoryView, mBeaconView, mPingView, mThroughputView});
    }

    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        for(IBlaubotDebugView view : mAllViews) {
            view.registerBlaubotInstance(blaubot);
        }
    }

    @Override
    public void unregisterBlaubotInstance() {
        for(IBlaubotDebugView view : mAllViews) {
            view.unregisterBlaubotInstance();
        }
    }
}
