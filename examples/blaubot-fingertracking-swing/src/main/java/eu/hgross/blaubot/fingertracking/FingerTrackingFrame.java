package eu.hgross.blaubot.fingertracking;

import com.google.gson.Gson;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JFrame;
import javax.swing.JLabel;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.fingertracking.model.Finger;
import eu.hgross.blaubot.fingertracking.model.FingerMessage;


public class FingerTrackingFrame extends JFrame implements ILifecycleListener, IBlaubotConnectionStateMachineListener {
    private final BlauBotTickle blauBotTickle;
    private final FingerField fingerField;
    private final JLabel stateLabel;
    private IBlaubotState currentState;
    private Random randomGenerator = new Random();
    private int myRandomColor = getRandomColor();
    private final Gson gson = new Gson();
    private final UUID clientUUID = UUID.randomUUID();

    /**
     * @param channel the channel to communicate with
     */
    public FingerTrackingFrame(final IBlaubotChannel channel) {
        super("Blaubot FingerTracking Java");
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                System.exit(0);
            }

            public void windowClosing(WindowEvent windowEvent) {
                dispose();
            }
        });

        this.fingerField = new FingerField();
        this.blauBotTickle = new BlauBotTickle(fingerField);
        fingerField.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                final int viewWidth = fingerField.getWidth();
                final int viewHeight = fingerField.getHeight();
                final int x = e.getX();
                final int y = e.getY();
                float fx = (float) x / (float) viewWidth;
                float fy = (float) y / (float) viewHeight;

                Finger f = new Finger();
                f.setX(fx);
                f.setY(fy);
                f.setColor(myRandomColor);
                FingerMessage fm = new FingerMessage();
                fm.setColor(myRandomColor);
                fm.setClientUUID(clientUUID.toString());
                fm.setTimestamp(System.currentTimeMillis());
                fm.setFingers(new Finger[]{f});
                final String message = gson.toJson(fm);
                channel.publish(message.getBytes(BlaubotConstants.STRING_CHARSET));
            }
        });

        channel.addMessageListener(new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                String message = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                blauBotTickle.onMessage(message);
            }
        });

        this.setResizable(false);
        this.setLayout(new BorderLayout());


        stateLabel = new JLabel(" ", JLabel.CENTER);
        updateStateLabel();

        this.setSize(800, 700);
        this.fingerField.setSize(800, 620);
        this.stateLabel.setSize(800, 80);

        this.add(stateLabel, BorderLayout.PAGE_START);
        this.add(fingerField, BorderLayout.CENTER);
    }

    private void updateStateLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<br>");
        sb.append("Status: ");
        sb.append(connected ? "Connected" : "Disconnected");
        sb.append("<br>");
        sb.append("CSM-State: ");
        sb.append(currentState+"");
        sb.append("<br>");
        sb.append("Network size: ");
        sb.append(connectedDevices.get());
        sb.append("<br><br></body></html>");
        stateLabel.setText(sb.toString());
    }

    private AtomicInteger connectedDevices = new AtomicInteger(0);
    private volatile boolean connected = false;

    @Override
    public void onConnected() {
        connected = true;
        connectedDevices.incrementAndGet();
        updateStateLabel();
    }

    @Override
    public void onDisconnected() {
        connected = false;
        connectedDevices.decrementAndGet();
        updateStateLabel();
    }

    @Override
    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
        connectedDevices.incrementAndGet();
        updateStateLabel();
    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
        connectedDevices.decrementAndGet();
        updateStateLabel();
    }

    @Override
    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {

    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {

    }


    @Override
    public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
        this.currentState = newState;
        updateStateLabel();
    }

    @Override
    public void onStateMachineStopped() {

    }

    @Override
    public void onStateMachineStarted() {

    }

    private int getRandomColor() {
        int red = randomGenerator.nextInt(255);
        int green = randomGenerator.nextInt(255);
        int blue = randomGenerator.nextInt(255);
        return new Color(red, green, blue).getRGB();
    }
}
