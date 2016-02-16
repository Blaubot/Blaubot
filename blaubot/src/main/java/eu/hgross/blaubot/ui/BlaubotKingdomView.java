package eu.hgross.blaubot.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.core.BlaubotKingdom;

/**
 * The view for a BlaubotKingdom.
 */
public class BlaubotKingdomView extends JPanel implements IBlaubotKingdomDebugView {

    private final KingdomCensusPanel mKingdomCensusPanel;
    private final List<IBlaubotKingdomDebugView> allViews;
    private final LifeCycleViewPanel mLifecycleViewPanel;
    private final ChannelPanel mChannelPanel;
    private final JLabel mHeadlineLabel;
    private final JButton mDisconnectButton;
    private final PingPanel mPingPanel;

    private BlaubotKingdom blaubotKingdom;

    public BlaubotKingdomView() {
        super();
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        this.mKingdomCensusPanel = new KingdomCensusPanel();
        this.mLifecycleViewPanel = new LifeCycleViewPanel();
        this.mChannelPanel = new ChannelPanel();
        this.mPingPanel = new PingPanel();
        this.allViews = Arrays.asList(new IBlaubotKingdomDebugView[]{mPingPanel, mKingdomCensusPanel, mChannelPanel, mLifecycleViewPanel});


        this.mHeadlineLabel = new JLabel("Kingdom of ...");
        this.mDisconnectButton = new JButton("Disconnect kingdom");


        add(mHeadlineLabel, c);
        add(mDisconnectButton, c);

        for(IBlaubotKingdomDebugView debugView : allViews) {
            if(debugView instanceof Component) {
                final Component spaceY = Box.createRigidArea(new Dimension(0, 7));
                this.add(spaceY, c);
                this.add((Component) debugView, c);
            }
        }

        this.mDisconnectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(blaubotKingdom != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            blaubotKingdom.disconnectKingdom();
                        }
                    }).start();
                }
            }
        });
    }


    @Override
    public void registerBlaubotKingdomInstance(final BlaubotKingdom blaubotKingdom) {
        if (this.blaubotKingdom != null) {
            unregisterBlaubotKingdomInstance();
        }
        this.blaubotKingdom = blaubotKingdom;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                mHeadlineLabel.setText("<html><h3>Kingdom of " + blaubotKingdom.getKingDevice().getUniqueDeviceID()+ "</h3></html>");
            }
        });
        for (IBlaubotKingdomDebugView v : allViews) {
            v.registerBlaubotKingdomInstance(blaubotKingdom);
        }
    }

    @Override
    public void unregisterBlaubotKingdomInstance() {
        if (this.blaubotKingdom != null) {
            for (IBlaubotKingdomDebugView view : allViews) {
                view.unregisterBlaubotKingdomInstance();
            }
        }
        this.blaubotKingdom = null;
    }

}
