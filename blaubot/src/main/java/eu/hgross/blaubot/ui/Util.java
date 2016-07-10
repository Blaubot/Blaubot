package eu.hgross.blaubot.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import eu.hgross.blaubot.core.State;

/**
 * UI utilities for the debug views.
 */
public class Util {
    //blaubotblue: #50ACFF

    /**
     * Retrieves an image associated with this state.
     * @param state the state
     * @return the image for the given state
     */
    public static BufferedImage getImageForState(State state) {
        final String fileName;
        if (State.Free.equals(state)) {
            fileName = "ic_free72x72.png";
        } else if (State.Peasant.equals(state)) {
            fileName = "ic_peasant72x72.png";
        } else if (State.Prince.equals(state)) {
            fileName = "ic_prince72x72.png";
        } else if (State.Stopped.equals(state)) {
            fileName = "ic_stopped72x72.png";
        } else if (State.King.equals(state)) {
            fileName = "ic_king72x72.png";
        } else {
            throw new RuntimeException("Unknown state");
        }
        try {
            final URL resource = state.getClass().getResource("/images/" + fileName);
            if (resource == null) {
                throw new NullPointerException("Missing resource: " + fileName);
            }
            BufferedImage bg = ImageIO.read(resource);
            return bg;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Given a blaubot state, creates a Swing panel containing the state's icon.
     * 
     * @param state the state
     * @param detailText a detail text like a uniqueDeviceId or anything related to describe this instance
     * @return set up Swing panel
     */
    public static JPanel createIcon(State state, String detailText) {
        final BufferedImage icon = Util.getImageForState(state);
        final Image scaledInstance = icon.getScaledInstance(30, -1, Image.SCALE_SMOOTH);

        JLabel picLabel = new JLabel(new ImageIcon(scaledInstance));
        JLabel comp = new JLabel("<html>" + state.toString() + "<small><br>" + detailText + "</small></html>");
        JPanel panel = new JPanel();
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(picLabel);
        panel.add(Box.createRigidArea(new Dimension(5, 0)));
        panel.add(comp);
        return panel;
    }
}
