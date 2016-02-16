package eu.hgross.blaubot.ui;

import java.awt.FlowLayout;
import java.util.Date;

import javax.swing.JLabel;
import javax.swing.JPanel;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;

/**
 * Created by henna on 30.04.15.
 */
public class BeaconViewPanel extends JPanel implements IBlaubotDebugView {
    public static final String LOG_TAG = "BeaconViewPanel";

    private final JLabel mLastDiscoveryEventTextView;
    private Blaubot mBlaubot;

    public BeaconViewPanel() {
        super();
        setLayout(new FlowLayout());
        mLastDiscoveryEventTextView = new JLabel("never got a discovery event");

        add(mLastDiscoveryEventTextView);
    }


    private IBlaubotDiscoveryEventListener discoveryEventListener = new IBlaubotDiscoveryEventListener() {
        @Override
        public void onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
            mLastDiscoveryEventTextView.setText((new Date()).toString()+ ": "+discoveryEvent + "");
            updateUI();
        }
    };


    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        if (this.mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        blaubot.getConnectionStateMachine().getBeaconService().addDiscoveryEventListener(discoveryEventListener);
    }

    @Override
    public void unregisterBlaubotInstance() {
        if (this.mBlaubot != null) {
            mBlaubot.getConnectionStateMachine().getBeaconService().removeDiscoveryEventListener(discoveryEventListener);
        }
        this.mBlaubot = null;
    }
}
