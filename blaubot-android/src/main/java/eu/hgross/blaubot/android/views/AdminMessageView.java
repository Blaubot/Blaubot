package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.ArrayList;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.RelayAdminMessage;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.ui.IBlaubotDebugView;

/**
 * Hooks into the admin messages and shows them (oldest first). 
 */
public class AdminMessageView extends LinearLayout implements IBlaubotDebugView {
    private static final int MAX_MESSAGES_DEFAULT = 30;
    private static final String NO_MESSAGES_TEXT = "No admin messages received so far";
    private Blaubot mBlaubot;
    private Handler mUiHandler;
    private CircularFifoQueue<AbstractAdminMessage> mLastMessages;
    private final Object queueMonitor = new Object();
    private int mMaxMessages = MAX_MESSAGES_DEFAULT;
    private AttributeSet mAttrs;
    private Context mContext;
    private boolean mShowRelayMessages = false;
    private ToggleButton mToggleRelayAdminMessagesButton;


    public AdminMessageView(Context context) {
        super(context);
        initView(context, null);
    }

    public AdminMessageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        mContext = context;
        mAttrs = attrs;
        if(mAttrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.AdminMessageView,
                    0, 0);
            try {
                mMaxMessages = a.getInt(R.styleable.AdminMessageView_maxMessages, mMaxMessages);
                mShowRelayMessages = a.getBoolean(R.styleable.AdminMessageView_showRelayMessages, mShowRelayMessages);
            } finally {
                a.recycle();
            }
        }
        mLastMessages = new CircularFifoQueue<>(mMaxMessages);
        mUiHandler = new Handler(Looper.getMainLooper());
        mToggleRelayAdminMessagesButton = new ToggleButton(getContext(), null);
        mToggleRelayAdminMessagesButton.setTextOn("Showing RelayMessages");
        mToggleRelayAdminMessagesButton.setTextOff("Not showing RelayMessages");
        mToggleRelayAdminMessagesButton.setChecked(mShowRelayMessages);
        mToggleRelayAdminMessagesButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mShowRelayMessages = isChecked;
            }
        });
    }

    private void clear() {
        if(mBlaubot != null) {
            mBlaubot.getChannelManager().removeAdminMessageListener(adminMessageListener);
            mBlaubot = null;
        }
        removeAllViews();
        initView(mContext, mAttrs);
    }

    private void updateUI() {
        final ArrayList<AbstractAdminMessage> data;
        synchronized (queueMonitor) {
            data = new ArrayList<>(mLastMessages);
        }
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
                addView(mToggleRelayAdminMessagesButton);

                final Drawable iconDrawable = getResources().getDrawable(android.R.drawable.ic_menu_send);
                int i = 0, size = data.size();
                for(AbstractAdminMessage msg : data) {
                    final View view = inflate(getContext(), R.layout.blaubot_adminmessage_view_list_item, null);
                    final TextView msgView = (TextView) view.findViewById(R.id.messageContent);
                    final ImageView icon = (ImageView) view.findViewById(R.id.icon);

                    icon.setImageDrawable(iconDrawable);
                    msgView.setText(msg.toString());

                    if(i++ == size-1) {
                        view.findViewById(R.id.divider).setVisibility(INVISIBLE);
                    }

                    addView(view);
                }


                if(data.isEmpty()) {
                    TextView tv = new TextView(getContext());
                    tv.setText(NO_MESSAGES_TEXT);
                    addView(tv);
                }
            }
        });

    }

    private IBlaubotAdminMessageListener adminMessageListener = new IBlaubotAdminMessageListener() {
        @Override
        public void onAdminMessage(AbstractAdminMessage adminMessage) {
            if (adminMessage instanceof RelayAdminMessage) {
                if (!mShowRelayMessages) {
                    return; // skip relay admin messages if wished
                }
            }
            synchronized (queueMonitor) {
                mLastMessages.add(adminMessage);
            }
            updateUI();
        }
    };

    /**
     * Set if RelayAdminMessages should be rendered.
     * Note that this can cause heavy load on the ui thread.
     *
     * @param mShowRelayMessages if true, relay admin messages are rendered
     */
    public void setShowRelayMessages(boolean mShowRelayMessages) {
        this.mShowRelayMessages = mShowRelayMessages;
    }

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
        blaubot.getChannelManager().addAdminMessageListener(adminMessageListener);

        // force some updates
        updateUI();
    }

    @Override
    public void unregisterBlaubotInstance() {
        clear();
    }
}
