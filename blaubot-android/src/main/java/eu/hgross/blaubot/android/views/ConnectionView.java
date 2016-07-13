package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Html;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.ServerConnectionManager;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.ui.IBlaubotDebugView;
import eu.hgross.blaubot.util.Log;

/**
 * A view to visualize a list of IBlaubotConnections beautifully ;-)
 * Implements IBlaubotDebugView but can also be used manually via addConnection, removeConnection and clearConnections.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class ConnectionView extends LinearLayout implements IBlaubotConnectionManagerListener, IBlaubotDebugView {
    private static final String LOG_TAG = "ConnectionView";
    private static final String NO_CONNECTED_DEVICES_TEXT = "No connections.";
    private Blaubot mBlaubot;
    private ArrayList<IBlaubotConnection> mConnections;
    private Handler mUiHandler;
    private Object mMonitor = new Object();
    private boolean mShowId = true;
    private Vibrator mVibrator;
    /**
     * If the user disconnects a visualized connection via tap, we vibrate for this amount of time.
     */
    private long DISCONNECT_VIBRATION_TIME = 100;

    public ConnectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.ConnectionView,
                    0, 0);
            try {
                mShowId = a.getBoolean(R.styleable.ConnectionView_showId, mShowId);
            } finally {
                a.recycle();
            }
        }
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mUiHandler = new Handler(Looper.getMainLooper());
        mConnections = new ArrayList<>();
    }

    public void registerBlaubotInstance(Blaubot blaubot) {
        if (this.mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        this.mBlaubot.getConnectionManager().addConnectionListener(this);
        updateList();
    }

    @Override
    public void unregisterBlaubotInstance() {
        if (this.mBlaubot != null) {
            this.mBlaubot.getConnectionManager().removeConnectionListener(this);
            this.mBlaubot = null;
        }
        clearConnections();
    }

    @Override
    public void onConnectionEstablished(final IBlaubotConnection connection) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Got connection established event - " + connection);
        }
        addConnection(connection);
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Connections: " + mConnections);
        }
        updateList();
    }

    @Override
    public void onConnectionClosed(final IBlaubotConnection connection) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Got connection closed event - " + connection);
        }
        removeConnection(connection);
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Connections: " + mConnections);
        }
        updateList();
    }

    /**
     * Adds a list of mConnections, then updates the view.
     *
     * @param connectionList the list of connections to be added
     */
    public void addConnections(Collection<IBlaubotConnection> connectionList) {
        for (IBlaubotConnection connection : connectionList) {
            synchronized (mMonitor) {
                mConnections.add(connection);
            }
        }
        updateList();
    }

    /**
     * Adds a connection and refreshes the view
     *
     * @param connection the connection to be added
     */
    public void addConnection(IBlaubotConnection connection) {
        synchronized (mMonitor) {
            mConnections.add(connection);
        }
        updateList();
    }

    /**
     * Removes a connection and refreshes the view
     *
     * @param connection the connection to be added
     */
    public void removeConnection(IBlaubotConnection connection) {
        synchronized (mMonitor) {
            mConnections.remove(connection);
        }
        updateList();
    }

    /**
     * Removes all mConnections and refreshes the view
     */
    public void clearConnections() {
        synchronized (mMonitor) {
            mConnections.clear();
        }
        updateList();
    }


    private void updateList() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                removeAllViews();
                ArrayList<IBlaubotConnection> cp = new ArrayList<>(mConnections);
                for (final IBlaubotConnection connection : cp) {
                    View view = inflate(getContext(), R.layout.blaubot_device_list_item, null);
                    TextView id = (TextView) view.findViewById(R.id.blaubot_device_list_item_id);
                    TextView name = (TextView) view.findViewById(R.id.blaubot_device_list_item_name);
                    TextView connectionClass = (TextView) view.findViewById(R.id.blaubot_device_list_connection_class);

                    id.setVisibility(mShowId ? VISIBLE : GONE);
                    id.setText(connection.getRemoteDevice().getUniqueDeviceID());
                    name.setText(connection.getRemoteDevice().getReadableName());

                    if (!(connection instanceof ServerConnectionManager.BlaubotServerRelayConnection)) {
                        connectionClass.setText(connection.getClass().getSimpleName() + "");
                        connectionClass.setMaxLines(1);
                        connectionClass.setSingleLine(true);
                        connectionClass.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    } else {
                        ServerConnectionManager.BlaubotServerRelayConnection relayConnection = (ServerConnectionManager.BlaubotServerRelayConnection) connection;
                        StringBuilder sb = new StringBuilder("<html>");
                        sb.append(connection.getClass().getSimpleName());
                        sb.append("<br>via mediator device<br>");
                        sb.append(relayConnection.getMediatorUniqueDeviceId());
                        sb.append("</html>");
                        connectionClass.setEllipsize(null);
                        connectionClass.setSingleLine(false);
                        connectionClass.setMaxLines(3);
                        connectionClass.setText(Html.fromHtml(sb.toString()));
                    }

                    view.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mUiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mVibrator != null) {
                                        mVibrator.vibrate(DISCONNECT_VIBRATION_TIME);
                                    }
                                    Toast.makeText(getContext(), "Closing connection ...", Toast.LENGTH_SHORT).show();
                                }
                            });
                            // disconnect, but don't lock up the ui.
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    connection.disconnect();
                                }
                            }).start();

                        }
                    });
                    addView(view);
                }
                if (cp.isEmpty()) {
                    TextView tv = new TextView(getContext(), null);
                    tv.setText(NO_CONNECTED_DEVICES_TEXT);
                    addView(tv);
                }
            }
        });
    }

}
