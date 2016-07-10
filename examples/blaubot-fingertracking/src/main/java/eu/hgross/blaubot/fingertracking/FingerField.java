package eu.hgross.blaubot.fingertracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import eu.hgross.blaubot.fingertracking.model.Finger;

/**
 * Shows some fingers
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class FingerField extends View {
	public static final int REFRESH_INTERVAL = 15; // every x ms refresh
	
	private volatile List<Finger> fingers = new ArrayList<Finger>();
	private volatile List<Finger> lastFingers = null;
	private Timer timer;
	private TimerTask timerTask;
	private float width, height; // cached
	private ShapeDrawable finger;

	public FingerField(Context context, AttributeSet as) {
		super(context, as);
		this.timer = new Timer();
		this.timerTask = new TimerTask() {
			@Override
			public void run() {
				if(fingers == lastFingers)
					return;
				postInvalidate();
			}
		};
		finger = new ShapeDrawable(new OvalShape());
		this.timer.scheduleAtFixedRate(timerTask, REFRESH_INTERVAL, REFRESH_INTERVAL);
	}

	protected void onDraw(Canvas canvas) {
		lastFingers = fingers;
		setBackgroundColor(Color.BLACK);
		width = (float) getWidth();
		height = (float) getHeight();
		drawFingers(canvas);
	}

	private void drawFingers(Canvas canvas) {
		for(Finger f : fingers) {
			drawFinger(f, canvas);
		}
	}
	
	private void drawFinger(Finger f, Canvas canvas) {
		int FINGER_RADIUS = (int)width/20;
		int x = (int)(width * f.getX());
		int y = (int)(height * f.getY());
		finger.getPaint().setColor(f.getColor());
		finger.setBounds(x-FINGER_RADIUS, y-FINGER_RADIUS, x+FINGER_RADIUS, y+FINGER_RADIUS);
		finger.draw(canvas);
	}
	
	public void setFingers(List<Finger> fingers) {
		this.fingers = fingers;
	}
}