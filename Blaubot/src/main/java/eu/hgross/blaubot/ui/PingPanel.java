package eu.hgross.blaubot.ui;

import com.google.gson.Gson;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotKingdom;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.util.Log;

/**
 * Receives and sends ping messages
 */
public class PingPanel extends JPanel implements IBlaubotDebugView, IBlaubotKingdomDebugView {
    private static final String LOG_TAG = "PingPanel";
    private final JButton mPingButton;
    private Blaubot mBlaubot;
    private BlaubotKingdom mBlaubotKingdom;

    private Date mLastReceivedPingDate;
    private IBlaubotChannel mPingChannel;
    private JLabel mLastReceivedLabel;
    private Gson gson;

    public PingPanel() {
        super();
        gson = new Gson();
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        this.mPingButton = new JButton("Send ping");
        mPingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (mPingChannel != null) {
                    String ownDeviceUnqiqueDeviceId = mBlaubot != null ? mBlaubot.getOwnDevice().getUniqueDeviceID() : (mBlaubotKingdom != null ? mBlaubotKingdom.getOwnDevice().getUniqueDeviceID() : null);
                    PingMessage pingMessage = new PingMessage();
                    pingMessage.setTimestamp(System.currentTimeMillis());
                    pingMessage.setSenderUniqueDeviceId(ownDeviceUnqiqueDeviceId);

                    String msg = gson.toJson(pingMessage);
                    byte[] bytes = msg.getBytes(BlaubotConstants.STRING_CHARSET);
                    mPingChannel.publish(bytes);
                }
            }
        });

        mLastReceivedLabel = new JLabel();
        updateViews();
    }


    private void updateViews() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                removeAll();
                String txt = mLastReceivedPingDate != null ? mLastReceivedPingDate.toString() : "never";
                mLastReceivedLabel.setText("Last received ping: " + txt);
                add(mPingButton);
                add(mLastReceivedLabel);
                updateUI();
            }
        });

    }

    private ILifecycleListener mLifeCycleListener = new ILifecycleListener() {
        @Override
        public void onConnected() {
            if (mPingChannel != null) {
                return;
            }
            final BlaubotChannelManager channelManager;
            if (mBlaubot != null) {
                channelManager = mBlaubot.getChannelManager();
            } else if (mBlaubotKingdom != null) {
                channelManager = mBlaubotKingdom.getChannelManager();
            } else {
                return;
            }
            mPingChannel = channelManager.createOrGetChannel(BlaubotDebugViewConstants.PING_VIEW_CHANNEL_ID);
            mPingChannel.subscribe(new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage blaubotMessage) {
                    long receivedTimeStamp = System.currentTimeMillis();
                    Date receivedDate = new Date();
                    final String msg = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Got a message on the ping channel: " + msg);
                    }
                    // got ping
                    PingMessage pingMessage = gson.fromJson(msg, PingMessage.class);
                    long rtt = receivedTimeStamp - pingMessage.getTimestamp();
                    // TODO display round trip time 
                    mLastReceivedPingDate = receivedDate;
                    updateViews();
                }
            });
        }

        @Override
        public void onDisconnected() {
            mPingChannel = null;
        }

        @Override
        public void onDeviceJoined(IBlaubotDevice blaubotDevice) {

        }

        @Override
        public void onDeviceLeft(IBlaubotDevice blaubotDevice) {

        }

        @Override
        public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {

        }

        @Override
        public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {

        }
    };

    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        if (this.mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        blaubot.addLifecycleListener(mLifeCycleListener);
    }

    @Override
    public void unregisterBlaubotInstance() {
        if (this.mBlaubot != null) {
            this.mBlaubot.removeLifecycleListener(mLifeCycleListener);
        }
        this.mBlaubot = null;
    }

    @Override
    public void registerBlaubotKingdomInstance(BlaubotKingdom blaubotKingdom) {
        if (this.mBlaubotKingdom != null) {
            unregisterBlaubotKingdomInstance();
        }
        this.mBlaubotKingdom = blaubotKingdom;
        blaubotKingdom.addLifecycleListener(mLifeCycleListener);
        mLifeCycleListener.onConnected();
    }

    @Override
    public void unregisterBlaubotKingdomInstance() {
        if (this.mBlaubotKingdom != null) {
            mBlaubotKingdom.removeLifecycleListener(mLifeCycleListener);
        }
        this.mBlaubotKingdom = null;

    }
}
