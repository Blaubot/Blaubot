package eu.hgross.blaubot.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.BlaubotServer;

/**
 * Created by henna on 30.04.15.
 */
public class SwingDebugView extends JPanel implements IBlaubotDebugView {
    private final KingdomCensusPanel mKingdomCensusPanel;
    private final StateViewPanel mStateViewPanel;
    private final BeaconViewPanel mBeaconViewPanel;
    private final ChannelPanel mChannelPanel;
    private final LifeCycleViewPanel mLifeCycleViewPanel;
    private final PingPanel mPingPanel;

    private final List<Component> allViews;
    private Blaubot blaubot;

    public SwingDebugView() {
        super();
        this.mKingdomCensusPanel = new KingdomCensusPanel();
        this.mStateViewPanel = new StateViewPanel();
        this.mBeaconViewPanel = new BeaconViewPanel();
        this.mLifeCycleViewPanel = new LifeCycleViewPanel();
        this.mChannelPanel = new ChannelPanel();
        this.mPingPanel = new PingPanel();

        allViews = Arrays.asList(new Component[]{mKingdomCensusPanel, mStateViewPanel, mBeaconViewPanel, mLifeCycleViewPanel, mChannelPanel, mPingPanel});
        for(Component debugView : allViews) {
            this.add(debugView);
        }
    }



    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        if (this.blaubot != null) {
            unregisterBlaubotInstance();
        }
        this.blaubot = blaubot;
        for (Component view : allViews) {
            if(view instanceof IBlaubotDebugView) {
                ((IBlaubotDebugView)view).registerBlaubotInstance(blaubot);
            }
        }
    }

    @Override
    public void unregisterBlaubotInstance() {
        if (this.blaubot != null) {
            for (Component view : allViews) {
                if(view instanceof IBlaubotDebugView) {
                    ((IBlaubotDebugView)view).unregisterBlaubotInstance();
                }
            }
        }
        this.blaubot = null;
    }


    /**
     * Creates the gui for the blaubot instance and displays the returned JFrame in a new Thread.
     *
     * @param blaubot the blaubot instance
     * @return the JFrame that is shown.
     */
    public static JFrame createAndShowGui(final Blaubot blaubot) {
        FutureTask<JFrame> futureTask = new FutureTask<JFrame>(new Callable<JFrame>() {
            @Override
            public JFrame call() throws Exception {
                SwingDebugView debugView = new SwingDebugView();
                debugView.registerBlaubotInstance(blaubot);

                JFrame frame = new JFrame();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setMinimumSize(new Dimension(1024, 500));
                frame.add(debugView);
                frame.pack();
                frame.setVisible(true);
                frame.setTitle("Blaubot DebugView");
                return frame;
            }
        });
        new Thread(futureTask).start();
        try {
            return futureTask.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        UUID appUUid = UUID.randomUUID();
        final Blaubot b1 = BlaubotFactory.createEthernetBlaubot(appUUid, 17171, 17172, 17173, BlaubotFactory.getLocalIpAddress());
        final Blaubot b2 = BlaubotFactory.createEthernetBlaubot(appUUid, 17174, 17175, 17173, BlaubotFactory.getLocalIpAddress());


        new Thread(new Runnable() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {

                        SwingDebugView sdb = new SwingDebugView();
                        sdb.registerBlaubotInstance(b1);

                        JFrame frame = new JFrame();
                        frame.add(sdb);
                        frame.pack();
                        frame.setVisible(true);

                    }
                });
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {

                        SwingDebugView sdb = new SwingDebugView();
                        sdb.setVisible(true);
                        sdb.registerBlaubotInstance(b2);

                        JFrame frame = new JFrame();
                        frame.add(sdb);
                        frame.pack();
                        frame.setVisible(true);
                    }
                });
            }
        }).start();
    }
}
