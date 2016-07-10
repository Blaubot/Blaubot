package eu.hgross.blaubot.test.main;

import java.net.UnknownHostException;
import java.util.UUID;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;

/**
 * Creates an ethernet Blaubot Kingdom with the multicast beacon and runs indefinitely.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class CreateMulticastBlaubotInstanceMain {

    public static void main(String[] args) throws UnknownHostException {
        UUID appUUid = UUID.fromString("de506eef-d894-4c18-97c3-d877ff26ca38"); // same as the android app
        final Blaubot ethernetBlaubot = BlaubotFactory.createEthernetBlaubot(appUUid);

        ethernetBlaubot.addLifecycleListener(new ILifecycleListener() {
            @Override
            public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
                System.out.println("onPrinceDeviceChanged(" + oldPrince + ", " + newPrince + ")");
            }

            @Override
            public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
                System.out.println("onKingDeviceChanged(" + oldKing + ", " + newKing + ")");
            }

            @Override
            public void onDisconnected() {
                System.out.println("onDisconnected()");
            }

            @Override
            public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
                System.out.println("onDeviceLeft(" + blaubotDevice + ")");
            }

            @Override
            public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
                System.out.println("onDeviceJoined(" + blaubotDevice + ")");
            }

            @Override
            public void onConnected() {
                System.out.println("onConnected()");
            }
        });

        ethernetBlaubot.startBlaubot();
    }
}
