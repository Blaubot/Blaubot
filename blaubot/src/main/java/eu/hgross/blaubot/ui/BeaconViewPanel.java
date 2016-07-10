package eu.hgross.blaubot.ui;

import java.awt.FlowLayout;
import java.util.Date;

import javax.swing.JLabel;
import javax.swing.JPanel;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;

/**
 * Shows the last beacon message in the ugly toString.
 * TODO: beautify, show more messages (like on android)
 */
public class BeaconViewPanel extends JPanel implements IBlaubotDebugView {
    public static final String LOG_TAG = "BeaconViewPanel";

    private final JLabel mLastDiscoveryEventTextView;
    private Blaubot mBlaubot;

    public BeaconViewPanel() {
        super();
        mLastDiscoveryEventTextView = new JLabel("never got a discovery event");

        add(mLastDiscoveryEventTextView);
    }


    private IBlaubotDiscoveryEventListener discoveryEventListener = new IBlaubotDiscoveryEventListener() {
        @Override
        public void onDeviceDiscoveryEvent(AbstractBlaubotDeviceDiscoveryEvent discoveryEvent) {
            final int width = getSize().width/2;
            String txt = "<html><body><div style=\"width: " + width + "px\"><small>" + new Date().toString()+ ": " + discoveryEvent + "</small></div></body></html>";
            mLastDiscoveryEventTextView.setText(txt);
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
