package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.concurrent.atomic.AtomicInteger;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.ui.IBlaubotDebugView;

/**
 * A dedicated view to show events from the IBlaubotLifeCycleListener.
 */
public class LifeCycleView extends LinearLayout implements IBlaubotDebugView {
    private static final int MAX_EVENTS_DEFAULT = 50;
    private static final String NO_EVENTS_SO_FAR_TEXT = "No events so far.";

    private class ViewEntry {
        String labelText;
        int drawableResourceId;
    }

    private Blaubot mBlaubot;
    private CircularFifoQueue<ViewEntry> mLastEvents;
    private int mMaxEvents = MAX_EVENTS_DEFAULT;
    private Handler mUiHandler;
    private final Object queueMonitor = new Object();

    public LifeCycleView(Context context) {
        super(context);
        initView(context, null);
    }

    public LifeCycleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        if(attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.LifeCycleView,
                    0, 0);
            try {
                mMaxEvents= a.getInt(R.styleable.LifeCycleView_maxEvents, mMaxEvents);
            } finally {
                a.recycle();
            }
        }
        mLastEvents = new CircularFifoQueue<>(mMaxEvents);
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Counts the number of devices (own inclusive) in the network based on the life cycle events
     */
    private AtomicInteger networkSizeCounter = new AtomicInteger();
    private ILifecycleListener mLifeCycleListener = new ILifecycleListener() {
        @Override
        public void onConnected() {
            networkSizeCounter.incrementAndGet();
            addToQueue("onConnected()", android.R.drawable.presence_online);
        }

        @Override
        public void onDisconnected() {
            networkSizeCounter.decrementAndGet();
            addToQueue("onDisconnected()", android.R.drawable.presence_busy);
        }

        @Override
        public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
            networkSizeCounter.incrementAndGet();
            addToQueue("onDeviceJoined("+blaubotDevice+")", android.R.drawable.ic_menu_add);
        }

        @Override
        public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
            networkSizeCounter.decrementAndGet();
            addToQueue("onDeviceLeft("+blaubotDevice+")", android.R.drawable.ic_menu_close_clear_cancel);
        }

        @Override
        public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
            addToQueue("onPrinceDeviceChanged(" + oldPrince + ", " + newPrince + ")", android.R.drawable.ic_menu_edit);
        }

        @Override
        public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
            addToQueue("onKingDeviceChanged(" + oldKing + ", " + newKing + ")", android.R.drawable.ic_menu_edit);
        }

        /**
         * Adds an event to the lastEvents ring buffer
         * @param eventText the text to add
         * @param drawableResourceId the resource id for the icon to show
         */
        private void addToQueue(String eventText, int drawableResourceId) {
            final ViewEntry p = new ViewEntry();
            p.labelText = eventText;
            p.drawableResourceId = drawableResourceId;
            synchronized (queueMonitor) {
                mLastEvents.add(p);
            }
            upateUI();
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

        // register listeners
        blaubot.addLifecycleListener(mLifeCycleListener);

        // force some updates
        upateUI();
    }

    @Override
    public void unregisterBlaubotInstance() {
        if(this.mBlaubot != null) {
            mBlaubot.removeLifecycleListener(mLifeCycleListener);
            mBlaubot = null;
        }
        upateUI();
    }

    private void upateUI() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                final int height = getHeight(), width = getWidth();
                if(getOrientation() == VERTICAL) {
                    setMinimumHeight(height);
                } else {
                    setMinimumWidth(width);
                }
                removeAllViews();

                // Number of devices
                TextView numberOfDevicesTextView = new TextView(getContext());
                numberOfDevicesTextView.setText("Blaubot network size: " + networkSizeCounter);
                addView(numberOfDevicesTextView);

                synchronized (queueMonitor) {
                    if(!mLastEvents.isEmpty()) {
                        for(ViewEntry eventPair : mLastEvents) {
                            final View view = inflate(getContext(), R.layout.blaubot_lifecycle_view_list_item, null);
                            final ImageView imageView = (ImageView) view.findViewById(R.id.icon);
                            final TextView label = (TextView) view.findViewById(R.id.uniqueDeviceIdLabel);
                            final Drawable icon = getResources().getDrawable(eventPair.drawableResourceId);

                            imageView.setImageDrawable(icon);
                            label.setText(eventPair.labelText);

                            addView(view);
                        }
                    } else {
                        TextView v = new TextView(getContext());
                        v.setText(NO_EVENTS_SO_FAR_TEXT);
                        addView(v);
                    }
                }


            }
        });
    }
}