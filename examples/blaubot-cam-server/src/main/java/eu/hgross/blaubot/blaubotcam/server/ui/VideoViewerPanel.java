package eu.hgross.blaubot.blaubotcam.server.ui;

import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.blaubotcam.server.model.ImageMessage;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.CensusMessage;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;

/**
 * Displays ImageMessages.
 * Attach it to the channel from where you receive the ImageMessages.
 */
public class VideoViewerPanel extends JScrollPane implements IBlaubotAdminMessageListener, IBlaubotMessageListener {
    private static final String LOG_TAG = "VideoViewerPanel";
    private final JPanel mMainPanel;
    private final JLabel mNoCamsLabel;
    
    /**
     * Maps the unique device id to the last received image messages for that device
     */
    private ConcurrentHashMap<String, ImageMessage> mLastImageMessages;
    /**
     * Maps the unique device id to the view, displaying the last image
     */
    private ConcurrentHashMap<String, VideoViewerItemPane> mViews;
    /**
     * The last census message. May be null.
     */
    private CensusMessage mLastCensusMessage;

    public VideoViewerPanel() {
        mViews = new ConcurrentHashMap<>();
        mLastImageMessages = new ConcurrentHashMap<>();
        mNoCamsLabel = new JLabel("<html>" +
                    "<h1>Not receiving data</h1>" +
                    "<p>A kingdom is connected to the server but no device is sending pictures.<br> Click the \"<i>Toggle camera</i>\" button on the devices to start the transmission of camera data.<p>" +
                "</html>");
        mMainPanel = new JPanel();
        mMainPanel.setLayout(new WrapLayout());
        mMainPanel.add(mNoCamsLabel);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setViewportView(mMainPanel);
        addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                final Dimension size = e.getComponent().getSize();
//                mMainPanel.setSize(size);
//                mMainPanel.setPreferredSize(size);
//                mMainPanel.setMaximumSize(size);
//                mMainPanel.setMinimumSize(size);
            }

            @Override
            public void componentMoved(ComponentEvent e) {

            }

            @Override
            public void componentShown(ComponentEvent e) {

            }

            @Override
            public void componentHidden(ComponentEvent e) {

            }
        });
    }

    /**
     * updates the ui
     */
    private void updateViews() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final Collection<ImageMessage> values = mLastImageMessages.values();
                mNoCamsLabel.setVisible(values.isEmpty());
                for (ImageMessage message : values) {
                    final String uniqueDeviceId = message.getUniqueDeviceId();
                    boolean added = mViews.putIfAbsent(uniqueDeviceId, new VideoViewerItemPane()) == null;
                    final VideoViewerItemPane videoViewerItemPane = mViews.get(uniqueDeviceId);
                    State lastKnownState = null;
                    if (mLastCensusMessage != null) {
                        lastKnownState = mLastCensusMessage.getDeviceStates().get(uniqueDeviceId);
                    }
                    videoViewerItemPane.setImageMessage(message, lastKnownState);

                    if (added) {
                        mMainPanel.add(videoViewerItemPane);
                    }
                }
            }
        });
    }

    @Override
    public void onMessage(BlaubotMessage blaubotMessage) {
        final ImageMessage imageMessage = new ImageMessage(blaubotMessage.getPayload());
        mLastImageMessages.put(imageMessage.getUniqueDeviceId(), imageMessage);
        updateViews();
    }

    @Override
    public void onAdminMessage(AbstractAdminMessage adminMessage) {
        if (adminMessage instanceof CensusMessage) {
            mLastCensusMessage = (CensusMessage) adminMessage;
        }
    }
}
