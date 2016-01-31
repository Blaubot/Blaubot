package eu.hgross.blaubot.fingertracking;

import java.util.UUID;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.LifecycleListenerAdapter;
import eu.hgross.blaubot.messaging.BlaubotChannelConfig;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.util.Log;

public class FingerTrackingMain {
    private static final UUID APP_UUID = UUID.fromString("c3c941e0-cfaf-11e3-9c1a-0800200c9a66");
    public static final short FINGER_MESSAGE_CHANNEL_ID = (short) 1;
    private static final int MIN_MESSAGE_RATE_DELAY = 50;


    public static void main(String[] args) {

        Log.LOG_LEVEL = Log.LogLevel.WARNINGS;
        final Blaubot blaubot = BlaubotFactory.createEthernetBlaubot(APP_UUID);
        final IBlaubotChannel channel = blaubot.createChannel(FINGER_MESSAGE_CHANNEL_ID);
        channel.getChannelConfig().setMessagePickerStrategy(BlaubotChannelConfig.MessagePickerStrategy.DISCARD_OLD);
        channel.getChannelConfig().setMessageRateLimit(MIN_MESSAGE_RATE_DELAY);

        blaubot.addLifecycleListener(new LifecycleListenerAdapter() {
            @Override
            public void onConnected() {
                channel.subscribe();
            }
        });

        FingerTrackingFrame frame = new FingerTrackingFrame(channel);
        frame.setVisible(true);
        frame.setTitle("Blaubot FingerTracking Demo");
        blaubot.addLifecycleListener(frame);
        blaubot.getConnectionStateMachine().addConnectionStateMachineListener(frame);


        blaubot.startBlaubot();
    }
}
