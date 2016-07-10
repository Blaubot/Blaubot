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

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.ArrayList;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import eu.hgross.blaubot.core.statemachine.states.KingState;
import eu.hgross.blaubot.core.statemachine.states.PrinceState;
import eu.hgross.blaubot.core.statemachine.states.StoppedState;
import eu.hgross.blaubot.ui.IBlaubotDebugView;
import eu.hgross.blaubot.util.PingMeasurerResult;

/**
 * Android view to display results of the PingMeasurer
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class PingMeasureResultView extends LinearLayout {
    private static final String LOG_TAG = "PingMeasureResultView";
    private Handler mUiHandler;
    /**
     * Contains the whole view and all sub elements.
     */
    private LinearLayout mMainView;

    public PingMeasureResultView(Context context) {
        this(context, null);
    }
    
    public PingMeasureResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public PingMeasureResultView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        mUiHandler = new Handler(Looper.getMainLooper());
        mMainView = (LinearLayout) inflate(getContext(), R.layout.blaubot_ping_measure_result, this);
    }

    /**
     * Updates the whole ui 
     */
    private void updateUI(final PingMeasurerResult result) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView min = (TextView) mMainView.findViewById(R.id.minRtt);
                TextView max = (TextView) mMainView.findViewById(R.id.maxRtt);
                TextView avg = (TextView) mMainView.findViewById(R.id.avgRtt);
                TextView count = (TextView) mMainView.findViewById(R.id.count);
                TextView size = (TextView) mMainView.findViewById(R.id.size);
                
                min.setText(""+ result.getMinRtt());
                max.setText("" + result.getMaxRtt());
                avg.setText("" + result.getAvgRtt());
                count.setText("" + result.getNumberOfPings());
                size.setText("" + result.getPingMessageSize());
            }
        });
    }

    /**
     * Applies a ping measurere result to this view.
     *
     * @param result the result
     */
    public void setPingMeasureResult(PingMeasurerResult result) {
        updateUI(result);
    }

}