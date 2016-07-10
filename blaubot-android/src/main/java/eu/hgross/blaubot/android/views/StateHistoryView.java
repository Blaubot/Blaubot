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
import org.w3c.dom.Text;

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

/**
 * Android view to display informations about the StateMachine's history of states.
 * Add this view to a blaubot instance like this: stateView.registerBlaubotInstance(blaubot);
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class StateHistoryView extends LinearLayout implements IBlaubotDebugView {
    private static final String LOG_TAG = "StateHistoryView";
    private Handler mUiHandler;
    private Blaubot mBlaubot;
    private final Object queueMonitor = new Object();
    private CircularFifoQueue<StateEntry> stateHistoryQueue;
    private int mMaxStates = 20;
    /**
     * Contains the whole view and all sub elements.
     */
    private LinearLayout mMainView;
    /**
     * Holds just the symbols of the states in order
     */
    private LinearLayout mStateContainer;
    /**
     * Contains a more detailed view then the state container.
     * Is not visible by default - is made visible by tapping on the stateContainer
     */
    private LinearLayout mDetailedStateContainer;

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
        mMainView = (LinearLayout) inflate(getContext(), R.layout.blaubot_state_history_view, this);
        mStateContainer = (LinearLayout) mMainView.findViewById(R.id.stateHistoryIconsContainer);
        mDetailedStateContainer = (LinearLayout) mMainView.findViewById(R.id.detailsContainer);
//        addView(mMainView);
    }

    /**
     * Toggles the detailed view's visibility
     */
    private final OnClickListener mToggleDetailViewOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // toggle simple view
            final boolean stateViewVisible = mStateContainer.getVisibility() == VISIBLE;
            mStateContainer.setVisibility(stateViewVisible ? GONE : VISIBLE);

            // toggle detailed view
            final boolean detailedViewVisible = mDetailedStateContainer.getVisibility() == VISIBLE;
            mDetailedStateContainer.setVisibility(detailedViewVisible ? GONE : VISIBLE);
        }
    };

    private IBlaubotConnectionStateMachineListener mBlaubotConnectionListener = new IBlaubotConnectionStateMachineListener() {
        @Override
        public void onStateChanged(IBlaubotState oldState, final IBlaubotState state) {
            synchronized (queueMonitor) {
                StateEntry entry = new StateEntry(state, System.currentTimeMillis());
                stateHistoryQueue.add(entry);
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
        if (mStateContainer.getOrientation() == HORIZONTAL) {
            newMaxStates = w / drawable_w;
        } else {
            newMaxStates = h / drawable_h;
        }

        if (newMaxStates != mMaxStates) {
            mMaxStates = newMaxStates;
            // re-create the queue
            synchronized (queueMonitor) {
                CircularFifoQueue<StateEntry> newQueue = new CircularFifoQueue<>(newMaxStates);
                newQueue.addAll(stateHistoryQueue);
                stateHistoryQueue = newQueue;
            }
            // update the ui
            updateUI();
        }

        super.onSizeChanged(w, h, oldw, oldh);
    }

    /**
     * Updates the whole ui (removing all, creating it all again)
     */
    private void updateUI() {
        // get the states in a short synched block
        final ArrayList<StateEntry> states = new ArrayList<>();
        synchronized (queueMonitor) {
            states.addAll(stateHistoryQueue);
        }

        // pre-ceate the simpleViews
        final ArrayList<ImageView> simpleViews = new ArrayList<>(states.size());
        final ArrayList<View> detailedViews = new ArrayList<>(states.size());
        StateEntry prevEntry = null;
        for (StateEntry entry : states) {
            // create the simple view
            Drawable d = ViewUtils.getDrawableForBlaubotState(getContext(), entry.getState());
            ImageView iv = new ImageView(getContext());
            iv.setImageDrawable(d);
            iv.setOnClickListener(mToggleDetailViewOnClickListener);

            // the more detailed view
            final View detailedView = createHistoryItem(getContext(), entry, prevEntry);
            detailedView.setOnClickListener(mToggleDetailViewOnClickListener);

            // add them to the list
            simpleViews.add(iv);
            detailedViews.add(detailedView);
            prevEntry = entry;
        }

        // add them to their containers on the ui thread
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mStateContainer.removeAllViews();
                mDetailedStateContainer.removeAllViews();

                // the main view
                for (ImageView iv : simpleViews) {
                    mStateContainer.addView(iv);
                }

                // the detailed view
                for (View view : detailedViews) {
                    mDetailedStateContainer.addView(view);
                }
            }
        });
    }

    /**
     * Creates a view visualizing a state with some more details (when, order, ...)
     *
     * @param ctx           the context
     * @param stateEntry    the state entry
     * @param previousEntry the previous entry or null, if it is the first entry
     * @return
     */
    private static View createHistoryItem(Context ctx, StateEntry stateEntry, StateEntry previousEntry) {
        final boolean isFirst = previousEntry == null;
        final View item = inflate(ctx, R.layout.blaubot_state_history_view_item, null);
        final ImageView imageView = (ImageView) item.findViewById(R.id.stateIcon);
        final View preContainer = item.findViewById(R.id.preContainer);
        final TextView timeDifferenceTextView = (TextView) item.findViewById(R.id.timeDifferenceTextView);
        final TextView text = (TextView) item.findViewById(R.id.text);
        final TextView text2 = (TextView) item.findViewById(R.id.text2);


        preContainer.setVisibility(isFirst ? GONE : VISIBLE);
        if (!isFirst) {
            long diff = stateEntry.getTime() - previousEntry.getTime();
            timeDifferenceTextView.setText(diff + " ms");
        }
        final IBlaubotState state = stateEntry.getState();
        Drawable d = ViewUtils.getDrawableForBlaubotState(ctx, state);
        imageView.setImageDrawable(d);
        text.setText(state.getClass().getSimpleName());
        if (state instanceof IBlaubotSubordinatedState) {
            text2.setText("King: " + ((IBlaubotSubordinatedState) state).getKingUniqueId());
        } else if (state instanceof FreeState) {
            text2.setText("Alone, searching a kingdom ...");
        } else if (state instanceof StoppedState) {
            text2.setText("Relaxing ...");
        } else if (state instanceof PrinceState) {
            text2.setText("Waiting for the king to die ...");
        } else if (state instanceof KingState) {
            text2.setText("Pulling all the strings ...");
        }
        return item;
    }

    /**
     * Holds a state and some meta data to be visualized
     */
    private class StateEntry {
        private final IBlaubotState state;
        private final long time;

        /**
         * @param state the state
         * @param time  the time at which the state was first used
         */
        public StateEntry(IBlaubotState state, long time) {
            this.state = state;
            this.time = time;
        }

        public IBlaubotState getState() {
            return state;
        }

        public long getTime() {
            return time;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StateEntry that = (StateEntry) o;

            if (time != that.time) return false;
            return state.equals(that.state);

        }

        @Override
        public int hashCode() {
            int result = state.hashCode();
            result = 31 * result + (int) (time ^ (time >>> 32));
            return result;
        }
    }

    /**
     * Register this view with the given blaubot instance
     *
     * @param blaubot the blaubot instance to connect with
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
        if (mBlaubot != null) {
            this.mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(mBlaubotConnectionListener);
            this.mBlaubot = null;
        }
        // force some updates
        updateUI();
    }

}