package eu.hgross.blaubot.blaubotcam.server.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.blaubotcam.server.model.ImageMessage;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.ui.Util;

/**
 * Displays an Image received from another blaubot device.
 */
public class VideoViewerItemPane extends JLayeredPane {
    private static final int RECEIVED_DATE_LABEL_HEIGHT = 20;
    private static final Color TRANSPARENT_COLOR = new Color(0,0,0,0);
    private static final Color OVERLAY_BACKGROUND = new Color(255,255,255,55);
    private static final Dimension STATE_IMAGE_SIZE = new Dimension(40, 40);
    /**
     * The max height for the displayed images
     */
    public static final int DEFAULT_IMAGE_MAX_HEIGHT = 300;
    private JLabel uniqueDeviceIdAndStateLabel;
    private JLabel stateImageContainer;
    private JLabel receivedDateLabel;
    /**
     * Holds the uniqueDeviceID and state image of the device where the image was received from
     */
    private JPanel overlayPanel;
    /**
     * The actual received image
     */
    private JLabel imageContainer;
    /**
     * Last state, that we set for the image container
     */
    private State lastSetState;
    /**
     * Max height of received images.
     * Images with greater height will be scaled down.
     */
    private int maxImageHeight = DEFAULT_IMAGE_MAX_HEIGHT;

    public VideoViewerItemPane() {
        uniqueDeviceIdAndStateLabel = new JLabel();
        stateImageContainer = new JLabel();
        stateImageContainer.setBackground(TRANSPARENT_COLOR);
        stateImageContainer.setSize(STATE_IMAGE_SIZE);
        stateImageContainer.setPreferredSize(STATE_IMAGE_SIZE);
        stateImageContainer.setMaximumSize(STATE_IMAGE_SIZE);
        receivedDateLabel = new JLabel();
        imageContainer = new JLabel();

        overlayPanel = new JPanel();
        overlayPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        overlayPanel.setBackground(OVERLAY_BACKGROUND);
        overlayPanel.add(stateImageContainer);
        overlayPanel.add(uniqueDeviceIdAndStateLabel);


        add(overlayPanel);
        add(receivedDateLabel);
        add(imageContainer);
    }


    public VideoViewerItemPane(ImageMessage imageMessage, State lastKnownState) {
        this();
        setImageMessage(imageMessage, lastKnownState);
    }

    /**
     * The max height for received images.
     * Images with greater height will be downscaled.
     * @param maxImageHeight the max height
     */
    public void setMaxImageHeight(int maxImageHeight) {
        this.maxImageHeight = maxImageHeight;
    }

    /**
     * Sets the image message to be displayed.
     * @param imageMessage the image message
     * @param lastKnownState the last known state of the sender of the imageMessage
     */
    public void setImageMessage(ImageMessage imageMessage, State lastKnownState) {
        updateViews(imageMessage, lastKnownState);
    }


    private void updateViews(final ImageMessage imageMessage, final State lastKnownState) {
        ImageIcon icon = imageMessage.toImageIcon();
        final Dimension iconDimensions;// = new Dimension(icon.getIconWidth(), icon.getIconHeight());
        final int maxImageHeight = this.maxImageHeight;
        if (icon.getIconHeight() > maxImageHeight) {
            double factor = ((double) maxImageHeight) / ((double) icon.getIconHeight());
            iconDimensions = new Dimension((int) (icon.getIconWidth() * factor), (int) (icon.getIconHeight() * factor));
            Image img = icon.getImage();
            Image scaledImg = img.getScaledInstance((int)iconDimensions.getWidth(), (int)iconDimensions.getHeight(), Image.SCALE_FAST);
            icon.setImage(scaledImg);
        } else {
            iconDimensions = new Dimension(icon.getIconWidth(), icon.getIconHeight());
        }
        final ImageIcon fIcon = icon;
        final Dimension overlayPanelDimensions = new Dimension((int)iconDimensions.getWidth(), STATE_IMAGE_SIZE.height + 10);

        // pre-process stateIcon
        final boolean stateChanged = this.lastSetState != null ? !this.lastSetState.equals(lastKnownState) : lastKnownState != null;
        final ImageIcon stateIcon;
        if (stateChanged) {
            Image imageForState = Util.getImageForState(lastKnownState);
            imageForState = imageForState.getScaledInstance(STATE_IMAGE_SIZE.width, STATE_IMAGE_SIZE.height, Image.SCALE_DEFAULT);
            stateIcon = new ImageIcon(imageForState);
            lastSetState = lastKnownState;
        } else {
            stateIcon = null;
        }

        // pre-process unique device id icons
        StringBuilder sb = new StringBuilder("<html>");
        if (lastKnownState != null) {
            sb.append(lastKnownState.name());
            sb.append("<br>");
        }
        sb.append("<small>");
        sb.append(imageMessage.getUniqueDeviceId());
        sb.append("</small></html>");
        final String labelText = sb.toString();

        // update the pre-processed values
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (imageMessage == null) {
                    return;
                }
                receivedDateLabel.setText("<html><small>" + imageMessage.getTime().toString() + "</small></html>");

                // dimensions of components
                setSize(iconDimensions);
                setPreferredSize(iconDimensions);
                setMinimumSize(iconDimensions);
                setMaximumSize(iconDimensions);
                overlayPanel.setSize(overlayPanelDimensions);
                overlayPanel.setPreferredSize(overlayPanelDimensions);
                overlayPanel.setMinimumSize(overlayPanelDimensions);
                overlayPanel.setMaximumSize(overlayPanelDimensions);
                imageContainer.setSize(iconDimensions);
                imageContainer.setPreferredSize(iconDimensions);
                imageContainer.setMinimumSize(iconDimensions);
                imageContainer.setMaximumSize(iconDimensions);

                // positioning
                receivedDateLabel.setBounds(5, ((int)iconDimensions.getHeight()) - RECEIVED_DATE_LABEL_HEIGHT, (int)iconDimensions.getWidth(), RECEIVED_DATE_LABEL_HEIGHT);

                // maintain the senders name
                uniqueDeviceIdAndStateLabel.setText(labelText);

                // the sender's state icon
                if (stateChanged && stateIcon != null) {
                    stateImageContainer.setIcon(stateIcon);
                }

                // the actual image
                imageContainer.setIcon(fIcon);
            }
        });
    }

}
