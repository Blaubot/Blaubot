package eu.hgross.blaubot.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotKingdom;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.util.ChannelSubscriptionListener;

/**
 * Shows all channels that are in use
 */
public class ChannelPanel extends JPanel implements IBlaubotDebugView, IBlaubotKingdomDebugView {
    private Blaubot mBlaubot;
    private BlaubotKingdom mBlaubotKingdom;
    private ChannelSubscriptionListener mChannelSubscriptionListener;
    private JPanel mContentContainer;

    public ChannelPanel() {
        super();
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);
        this.mContentContainer = new JPanel();
        this.mContentContainer.setLayout(new FlowLayout());
        this.mContentContainer.setAlignmentX(LEFT_ALIGNMENT);
        this.mChannelSubscriptionListener = new ChannelSubscriptionListener();
        this.mChannelSubscriptionListener.addSubscriptionChangeListener(subscriptionChangeListener);
    }


    private void updateViews() {
        final Set<Short> channels = mChannelSubscriptionListener.getChannels();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                removeAll();
                mContentContainer.removeAll();

                add(new JLabel("Channels and subscriptions:"));
                if (channels.isEmpty()) {
                    add(new JLabel("No subscriptions at all."));
                }
                add(Box.createRigidArea(new Dimension(0, 5)));
                add(mContentContainer);
                for(final short channelId : channels) {
                    Set<String> subscribers = mChannelSubscriptionListener.getSubscribersOfChannel(channelId);

                    JLabel comp = new JLabel("<html>Channel #" + channelId + "<small><br>" + subscribers.size() + " known subscribers</small></html>");


                    JButton subscribeButton = new JButton("subscribe");
                    subscribeButton.setBorder(BorderFactory.createRaisedBevelBorder());
                    subscribeButton.setToolTipText("subscribe");
                    subscribeButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (mBlaubotKingdom != null) {
                                IBlaubotChannel chan = mBlaubotKingdom.getChannelManager().createOrGetChannel(channelId);
                                chan.subscribe();
                            }
                        }
                    });
                    JButton unsubscribeButton = new JButton("unsubscribe");
                    unsubscribeButton.setToolTipText("unsubscribe");
                    unsubscribeButton.setBorder(BorderFactory.createRaisedBevelBorder());
                    unsubscribeButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (mBlaubotKingdom != null) {
                                IBlaubotChannel chan = mBlaubotKingdom.getChannelManager().createOrGetChannel(channelId);
                                chan.unsubscribe();
                            }
                        }
                    });

                    JPanel buttonPanel = new JPanel();
                    buttonPanel.setLayout(new FlowLayout());
                    subscribeButton.setMargin(new java.awt.Insets(1, 1, 1, 1));
                    unsubscribeButton.setMargin(new java.awt.Insets(1, 1, 1, 1));
                    buttonPanel.add(subscribeButton);
                    buttonPanel.add(unsubscribeButton);

                    JPanel panel = new JPanel();
                    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                    panel.add(Box.createRigidArea(new Dimension(5, 0)));
                    panel.add(comp);
                    panel.add(buttonPanel);
                    panel.setToolTipText("Subscribers: " + subscribers);
                    mContentContainer.add(panel);
                }
                updateUI();

            }
        });
    }

    private ChannelSubscriptionListener.SubscriptionChangeListener subscriptionChangeListener = new ChannelSubscriptionListener.SubscriptionChangeListener() {
        @Override
        public void onSubscriptionChanged(short channelId) {
            updateViews();
        }

        @Override
        public void onUnsubscribed(short channelId, String unqiueDeviceId) {
        }

        @Override
        public void onSubscribed(short channelId, String uniqueDeviceId) {
        }
    };

    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        if(this.mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        blaubot.getChannelManager().addAdminMessageListener(mChannelSubscriptionListener);
        blaubot.addLifecycleListener(mChannelSubscriptionListener);
    }

    @Override
    public void unregisterBlaubotInstance() {
        if(this.mBlaubot != null) {
            this.mBlaubot.getChannelManager().removeAdminMessageListener(mChannelSubscriptionListener);
            this.mBlaubot.removeLifecycleListener(mChannelSubscriptionListener);
        }
        this.mBlaubot = null;
    }

    @Override
    public void registerBlaubotKingdomInstance(BlaubotKingdom blaubotKingdom) {
        if(this.mBlaubotKingdom != null) {
            unregisterBlaubotKingdomInstance();
        }
        this.mBlaubotKingdom = blaubotKingdom;
        blaubotKingdom.getChannelManager().addAdminMessageListener(mChannelSubscriptionListener);
        blaubotKingdom.addLifecycleListener(mChannelSubscriptionListener);
    }

    @Override
    public void unregisterBlaubotKingdomInstance() {
        if (this.mBlaubotKingdom != null) {
            mBlaubotKingdom.getChannelManager().removeAdminMessageListener(mChannelSubscriptionListener);
            mBlaubotKingdom.removeLifecycleListener(mChannelSubscriptionListener);
        }
        this.mBlaubotKingdom = null;

    }
}
