package eu.hgross.blaubot.fingertracking;

import android.graphics.Color;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.View;
import android.view.View.OnTouchListener;

import com.google.gson.Gson;

import java.util.Random;
import java.util.UUID;

import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.fingertracking.model.Finger;
import eu.hgross.blaubot.fingertracking.model.FingerMessage;

/**
 * Given a View and a BlaubotChannel, the FingerTracker publishes the onTouch-Events as
 * FingerMessage through the given channel.
 */
public class FingerTracker implements OnTouchListener {
    private static final Gson gson = new Gson();
    private final int randomColor;
    private final PointerCoords pc = new PointerCoords();
    private final UUID clientUUID = UUID.randomUUID();
    private final String clientUUIDString = clientUUID.toString();
    private IBlaubotChannel channel;
    private View mView;
    private boolean activated = false;
    private int min_interval = 0;
    private long lastSentTimestamp = -1;

    /**
     * @param view View which fires the {@link MotionEvent}s
     */
    public FingerTracker(IBlaubotChannel channel, View view) {
        this(channel, view, 0);
    }

    /**
     * Intializes the plugin with a min interval.
     *
     * @param channel
     * @param view
     * @param min_interval the min interval - meaning the minimum time between two messages
     */
    public FingerTracker(IBlaubotChannel channel, View view, int min_interval) {
        this.mView = view;
        this.randomColor = getRandomColor();
        this.channel = channel;
        this.min_interval = min_interval;
        this.mView.setOnTouchListener(this);
    }

    private boolean canSend() {
        long now = System.currentTimeMillis();
        return now - lastSentTimestamp > min_interval;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!activated)
            return true;
        if (!canSend())
            return true;
        int viewWidth = mView.getWidth();
        int viewHeight = mView.getHeight();
        final int pointerCount = event.getPointerCount();
        Finger[] newFingers = new Finger[pointerCount];

        for (int p = 0; p < pointerCount; p++) {
            event.getPointerCoords(p, pc);
            Finger f = new Finger();
            float x = (float) pc.x / (float) viewWidth;
            float y = (float) pc.y / (float) viewHeight;
            f.setX(x);
            f.setY(y);
            f.setColor(randomColor);
            newFingers[p] = f;
        }
        FingerMessage msg = new FingerMessage();
        msg.setClientUUID(clientUUIDString);
        msg.setFingers(newFingers);
        msg.setColor(randomColor);
        String json = gson.toJson(msg);

        this.channel.publish(json.getBytes());
        lastSentTimestamp = System.currentTimeMillis();

        return true;
    }

    private int getRandomColor() {
        Random randomGenerator = new Random();
        int red = randomGenerator.nextInt(255);
        int green = randomGenerator.nextInt(255);
        int blue = randomGenerator.nextInt(255);
        return Color.rgb(red, green, blue);
    }

    public void activate() {
        this.activated = true;
    }

    public void deactivate() {
        this.activated = false;
    }

}