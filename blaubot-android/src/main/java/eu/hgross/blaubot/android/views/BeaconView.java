package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotStateMachineEvent;
import eu.hgross.blaubot.ui.IBlaubotDebugView;

/**
 * Android view to display informations about the beacons registered to the BeaconService.
 * 
 * Add this view to a blaubot instance like this: stateView.registerBlaubotInstance(blaubot);
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class BeaconView extends LinearLayout implements IBlaubotDebugView {
    private static final int MAX_EVENTS_DEFAULT = 5;

    private Handler mUiHandler;
    private Blaubot mBlaubot;
    private Context mContext;

    private Object queueLock = new Object();
    private CircularFifoQueue<EventEntry> lastEvents;
    private int mMaxEvents = MAX_EVENTS_DEFAULT;


    private class EventEntry {
        AbstractBlaubotStateMachineEvent event;
        View view;
        long time;

        public EventEntry(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent, View item, long time) {
            this.event = discoveryEvent;
            this.view = item;
            this.time = time;
        }
    }

    public BeaconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public BeaconView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        this.mContext = context;
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.LifeCycleView,
                    0, 0);
            try {
                mMaxEvents = a.getInt(R.styleable.BeaconView_maxEvents, mMaxEvents);
            } finally {
                a.recycle();
            }
        }
        this.lastEvents = new CircularFifoQueue<>();
        mUiHandler = new Handler(Looper.getMainLooper());
    }


    private IBlaubotDiscoveryEventListener mDiscoveryEventListener = new IBlaubotDiscoveryEventListener() {

        private void updateEntryTimes() {
            final ArrayList<EventEntry> entries = new ArrayList<>(lastEvents);
            Collections.reverse(entries);
            synchronized (queueLock) {
                long firstTime = -1;
                for (EventEntry entry : entries) {
                    TextView whenTextView = (TextView) entry.view.findViewById(R.id.whenText);
                    if (firstTime == -1) {
                        whenTextView.setText(Html.fromHtml("<html>(latest)<br> " + (new Date(entry.time)) + "</html>"));
                        firstTime = entry.time;
                    } else {
                        final long delta = firstTime - entry.time;
                        whenTextView.setText(delta + " ms before");
                    }
                }
            }
        }

        @Override
        public void onDeviceDiscoveryEvent(final AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
            final State state = discoveryEvent.getRemoteDeviceState();
            final String uniqueDeviceID = discoveryEvent.getRemoteDevice().getUniqueDeviceID();
            final Drawable icon = ViewUtils.getDrawableForBlaubotState(mContext, state);
            final List<ConnectionMetaDataDTO> connectionMetaData = discoveryEvent.getConnectionMetaData();

            mUiHandler.post(new Runnable() {

                @Override
                public void run() {
                    final View item = inflate(mContext, R.layout.blaubot_beacon_view_list_item, null);
                    TextView uniqueDeviceIdTextView = (TextView) item.findViewById(R.id.uniqueDeviceIdLabel);
                    TextView connectionMetaDataTextView = (TextView) item.findViewById(R.id.connectionMetaData);
                    TextView stateTextView = (TextView) item.findViewById(R.id.stateLabel);
                    ImageView iconImageView = (ImageView) item.findViewById(R.id.stateIcon);

                    iconImageView.setImageDrawable(icon);
                    uniqueDeviceIdTextView.setText(uniqueDeviceID);
                    stateTextView.setText(state.toString());
                    connectionMetaDataTextView.setText(connectionMetaData.toString());


                    // prepend
                    addView(item, 0);

                    // memorize
                    synchronized (queueLock) {
                        final EventEntry eventEntry = new EventEntry(discoveryEvent, item, System.currentTimeMillis());
                        lastEvents.add(eventEntry);
                    }
                    // remove views, if too much
                    final int childCount = getChildCount();
                    if (childCount > mMaxEvents) {
                        removeViews(mMaxEvents, childCount - mMaxEvents);
                    }

                    updateEntryTimes();
                }

            });
        }
    };

    /**
     * Register this view with the given blaubot instance
     *
     * @param blaubot the blaubot instance to connect with
     */
    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        if (mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        this.mBlaubot.getConnectionStateMachine().getBeaconService().addDiscoveryEventListener(mDiscoveryEventListener);
    }

    @Override
    public void unregisterBlaubotInstance() {
        if (mBlaubot != null) {
            this.mBlaubot.getConnectionStateMachine().getBeaconService().removeDiscoveryEventListener(mDiscoveryEventListener);
        }
    }

}