package eu.hgross.blaubot.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

import eu.hgross.blaubot.core.State;

/**
 * Created by henna on 01.05.15.
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
}
