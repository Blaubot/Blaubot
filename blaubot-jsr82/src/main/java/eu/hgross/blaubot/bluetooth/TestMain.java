package eu.hgross.blaubot.bluetooth;

import java.util.UUID;

import javax.bluetooth.BluetoothStateException;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.ui.SwingDebugView;

public class TestMain {
    public static void main(String[] args) throws BluetoothStateException, ClassNotFoundException {
        UUID appUuid = UUID.fromString("d547e810-b7bf-11e4-a71e-12e3f512a338");
        Blaubot blaubot = BlaubotFactory.createJsr82BluetoothBlaubot(appUuid);
        blaubot.addLifecycleListener(new ILifecycleListener() {
            @Override
            public void onConnected() {
                System.out.println("onConnected");
            }

            @Override
            public void onDisconnected() {
                System.out.println("onDisconnected");
            }

            @Override
            public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
                System.out.println("onDeviceJoined");
            }

            @Override
            public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
                System.out.println("onDeviceLeft");
            }

            @Override
            public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
                System.out.println("onPrinceDeviceChanged");
            }

            @Override
            public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
                System.out.println("onKingDeviceChanged");
            }
        });

        SwingDebugView.createAndShowGui(blaubot);

    }
}
