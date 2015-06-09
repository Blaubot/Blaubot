package eu.hgross.blaubot.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotKingdom;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;

/**
 * Created by henna on 30.04.15.
 */
public class LifeCycleViewPanel extends JPanel implements IBlaubotDebugView, IBlaubotKingdomDebugView {
    public static final String LOG_TAG = "LifeCycleViewPanel";

    private JList<String> mLifeCycleEventsList;
    private DefaultListModel<String> mLifeCycleEventsListModel;
    private Blaubot mBlaubot;
    private BlaubotKingdom mBlaubotKingdom;

    public LifeCycleViewPanel() {
        super();
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        mLifeCycleEventsListModel = new DefaultListModel<>();
        mLifeCycleEventsList = new JList<>(mLifeCycleEventsListModel);
        add(new JLabel("Lifecycle events:"), gbc);
        add(mLifeCycleEventsList, gbc);
    }


    private ILifecycleListener mLifeCycleListener = new ILifecycleListener() {
        private AtomicInteger networkSizeCounter = new AtomicInteger();
        @Override
        public void onConnected() {
            networkSizeCounter.incrementAndGet();
            addEvent("onConnected()");
        }

        @Override
        public void onDisconnected() {
            networkSizeCounter.decrementAndGet();
            addEvent("onDisconnected()");
        }

        @Override
        public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
            networkSizeCounter.incrementAndGet();
            addEvent("onDeviceJoined(" + blaubotDevice + ")");
        }

        @Override
        public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
            networkSizeCounter.decrementAndGet();
            addEvent("onDeviceLeft(" + blaubotDevice + ")");
        }

        @Override
        public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
            addEvent("onPrinceDeviceChanged(" + oldPrince + ", " + newPrince + ")");
        }

        @Override
        public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
            addEvent("onKingDeviceChanged(" + oldKing + ", " + newKing + ")");
        }

        private void addEvent(String eventText) {
            mLifeCycleEventsListModel.addElement(eventText);
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
            mBlaubot.removeLifecycleListener(mLifeCycleListener);
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
    }

    @Override
    public void unregisterBlaubotKingdomInstance() {
        if (this.mBlaubotKingdom != null) {
            mBlaubotKingdom.removeLifecycleListener(mLifeCycleListener);
        }
        this.mBlaubotKingdom = null;
    }
}
