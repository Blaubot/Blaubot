package eu.hgross.blaubot.fingertracking;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JPanel;

import eu.hgross.blaubot.fingertracking.model.Finger;

/**
 *
 */
public class FingerField extends JPanel {
    private static final long REFRESH_INTERVAL = 50;
    private volatile List<Finger> fingers = new ArrayList<>();
    private volatile List<Finger> lastFingers = null;
    private Timer timer;
    private TimerTask timerTask;
    private int height;
    private int width;

    public FingerField() {
        this.timer = new Timer();
        this.timerTask = new TimerTask() {
            @Override
            public void run() {
                if(fingers == lastFingers)
                    return;
                repaint();
            }
        };
        this.timer.scheduleAtFixedRate(timerTask, REFRESH_INTERVAL, REFRESH_INTERVAL);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        this.width = this.getWidth();
        this.height = this.getHeight();
        //g.clearRect(0, 0, width, height);

        lastFingers = fingers;

        g.setColor(Color.black);
        g.fillRect(0, 0, width, height);

        drawFingers(g);

    }


    private void drawFingers(Graphics canvas) {
        for(Finger f : fingers) {
            drawFinger(f, canvas);
        }
    }

    private void drawFinger(Finger f, Graphics canvas) {
        int FINGER_RADIUS = width/20;
        int x = (int)(width * f.getX());
        int y = (int)(height * f.getY());

        // draw shape
        canvas.setColor(new Color(f.getColor(), true));
        canvas.fillArc(x-FINGER_RADIUS/2,y-FINGER_RADIUS/2, FINGER_RADIUS, FINGER_RADIUS, 0,360);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100,200);
    }

    public void setFingers(List<Finger> fingers) {
        this.fingers = fingers;
    }
}
