package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.ArrayList;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.ui.IBlaubotDebugView;

/**
 * Android view to display informations about the StateMachine's history of states.
 * Add this view to a blaubot instance like this: stateView.registerBlaubotInstance(blaubot);
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class StateHistoryView extends LinearLayout implements IBlaubotDebugView {
    private static final String LOG_TAG = "StateHistoryView";
    private Handler mUiHandler;
    private Blaubot mBlaubot;
    private final Object queueMonitor = new Object();
    private CircularFifoQueue<IBlaubotState> stateHistoryQueue;
    private int mMaxStates= 20;

    public StateHistoryView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public StateHistoryView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}

	private void initView() {
        stateHistoryQueue = new CircularFifoQueue<>(mMaxStates); // will be changed on resize
		mUiHandler = new Handler(Looper.getMainLooper());
	}

	private IBlaubotConnectionStateMachineListener mBlaubotConnectionListener = new IBlaubotConnectionStateMachineListener() {
		@Override
		public void onStateChanged(IBlaubotState oldState, final IBlaubotState state) {
            synchronized (queueMonitor) {
                stateHistoryQueue.add(state);
            }
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // calc the max number of state icons inside the view
        Drawable d = getResources().getDrawable(R.drawable.ic_free);
        final int drawable_w = d.getMinimumWidth();
        final int drawable_h = d.getMinimumHeight();
        d = null;

        final int newMaxStates;
        if(getOrientation() == HORIZONTAL) {
            newMaxStates = w/drawable_w;
        } else {
            newMaxStates = h/drawable_h;
        }

        if(newMaxStates != mMaxStates) {
            mMaxStates = newMaxStates;
            // re-create the queue
            synchronized (queueMonitor) {
                CircularFifoQueue<IBlaubotState> newQueue = new CircularFifoQueue<>(newMaxStates);
                newQueue.addAll(stateHistoryQueue);
                stateHistoryQueue = newQueue;
            }
            // update the ui
            updateUI();
        }

        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void updateUI() {
        // get the states in a short synched block
        final ArrayList<IBlaubotState> states = new ArrayList<>();
        synchronized (queueMonitor) {
             states.addAll(stateHistoryQueue);
        }

        // pre-ceate the views
        final ArrayList<ImageView> views = new ArrayList<>(states.size());
        for(IBlaubotState state : states) {
            Drawable d = ViewUtils.getDrawableForBlaubotState(getContext(), state);
            ImageView iv = new ImageView(getContext());
            iv.setImageDrawable(d);
            views.add(iv);
        }

        // append the views
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                removeAllViews();
                for(ImageView iv : views) {
                    addView(iv);
                }
            }
        });
    }

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
        this.mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(mBlaubotConnectionListener);

        // force some updates
        final IBlaubotState currentState = blaubot.getConnectionStateMachine().getCurrentState();
        mBlaubotConnectionListener.onStateChanged(null, currentState);
    }

    @Override
    public void unregisterBlaubotInstance() {
        if(mBlaubot != null) {
            this.mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(mBlaubotConnectionListener);
            this.mBlaubot = null;
        }
        // force some updates
        updateUI();
    }

}