package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collection;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.util.PingMeasurerResult;

/**
 * Android view to display results of the Subscribers of a channel
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class SubscribersView extends LinearLayout {
    private static final String LOG_TAG = "PingMeasureResultView";
    private Handler mUiHandler;
    /**
     * Contains the whole view and all sub elements.
     */
    private LinearLayout mMainView;

    public SubscribersView(Context context) {
        this(context, null);
    }
    
    public SubscribersView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public SubscribersView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        mUiHandler = new Handler(Looper.getMainLooper());
        mMainView = (LinearLayout) inflate(getContext(), R.layout.blaubot_channel_subscribers, this);
    }

    /**
     * Updates the whole ui 
     * @param subscribers the channel to visualize subscribers for
     */
    private void updateUI(final Collection<String> subscribers) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                LinearLayout s = (LinearLayout) mMainView.findViewById(R.id.subscriberContainer);
                for (String subscriber : subscribers) {
                    TextView tv = new TextView(getContext());
                    tv.setText(subscriber);
                    s.addView(tv);
                }
            }
        });
    }

    /**
     * Applies a list of unique device ids to this view
     *
     * @param subscribers the subscribers
     */
    public void setPingMeasureResult(Collection<String> subscribers) {
        updateUI(subscribers);
    }

}