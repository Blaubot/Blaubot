package eu.hgross.blaubot.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotKingdom;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.CensusMessage;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;

/**
 * Shows all member of the blaubot network
 */
public class KingdomCensusPanel extends JPanel implements IBlaubotDebugView, IBlaubotKingdomDebugView {
    private Blaubot mBlaubot;
    private BlaubotKingdom mBlaubotKingdom;


    public KingdomCensusPanel() {
        super();
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    }

    private CensusMessage mLastCensusMessage;
    private IBlaubotAdminMessageListener mAdminMessageListener = new IBlaubotAdminMessageListener() {
        @Override
        public void onAdminMessage(AbstractAdminMessage adminMessage) {
            if(adminMessage instanceof CensusMessage) {
                mLastCensusMessage = (CensusMessage) adminMessage;
                updateViews();
            }
        }
    };

    private ILifecycleListener mLifecycleListener = new ILifecycleListener() {
        @Override
        public void onConnected() {
            updateViews();
        }

        @Override
        public void onDisconnected() {
            updateViews();
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

    private void updateViews() {
        if(this.mLastCensusMessage != null) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    removeAll();
                    final Set<Map.Entry<String, State>> entries = mLastCensusMessage.getDeviceStates().entrySet();
                    if (entries.isEmpty()) {
                        add(new JLabel("No members."));
                    } else {
                        add(new JLabel("Kingdom:"));
                        add(Box.createRigidArea(new Dimension(0, 5)));
                    }
                    for(Map.Entry<String, State> entry : entries) {
                        final String uniqueDeviceId = entry.getKey();
                        final State state = entry.getValue();
                        final BufferedImage icon = Util.getImageForState(state);
                        final Image scaledInstance = icon.getScaledInstance(30, -1, Image.SCALE_SMOOTH);


                        JLabel picLabel = new JLabel(new ImageIcon(scaledInstance));
                        JLabel comp = new JLabel("<html>" + state.toString() + "<small><br>" + uniqueDeviceId + "</small></html>");
                        JPanel panel = new JPanel();
                        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
                        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                        panel.add(picLabel);
                        panel.add(Box.createRigidArea(new Dimension(5, 0)));
                        panel.add(comp);
                        add(panel);

                    }
                    updateUI();

                }
            });
        }

    }

    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        if(this.mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        blaubot.getChannelManager().addAdminMessageListener(mAdminMessageListener);
        blaubot.addLifecycleListener(mLifecycleListener);
    }

    @Override
    public void unregisterBlaubotInstance() {
        if(this.mBlaubot != null) {
            this.mBlaubot.getChannelManager().removeAdminMessageListener(mAdminMessageListener);
            this.mBlaubot.removeLifecycleListener(mLifecycleListener);
        }
        this.mBlaubot = null;
    }

    @Override
    public void registerBlaubotKingdomInstance(BlaubotKingdom blaubotKingdom) {
        if(this.mBlaubotKingdom != null) {
            unregisterBlaubotKingdomInstance();
        }
        this.mBlaubotKingdom = blaubotKingdom;
        blaubotKingdom.getChannelManager().addAdminMessageListener(mAdminMessageListener);
        blaubotKingdom.addLifecycleListener(mLifecycleListener);
    }

    @Override
    public void unregisterBlaubotKingdomInstance() {
        if (this.mBlaubotKingdom != null) {
            mBlaubotKingdom.getChannelManager().removeAdminMessageListener(mAdminMessageListener);
            mBlaubotKingdom.removeLifecycleListener(mLifecycleListener);
        }
        this.mBlaubotKingdom = null;

    }
}
