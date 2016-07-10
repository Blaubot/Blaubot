package eu.hgross.blaubot.test;

import org.junit.Test;

import java.util.UUID;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotDevice;

/**
 * Tests the create methods of included dependencies like blaubot-websockets, blaubot-jsr82, ... of the BlaubotFactory
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class BlaubotFactoryTest {
    private static final UUID appUuid = UUID.randomUUID();
    private static final IBlaubotDevice ownDevice = new BlaubotDevice();
    private static final String DEFAULT_INTERFACE = "0.0.0.0";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT_1 = 17171;

    @Test
    public void testBlaubotWebsocket() throws ClassNotFoundException {
        BlaubotFactory.createBlaubotWebsocketAdapter(ownDevice, DEFAULT_INTERFACE, DEFAULT_PORT_1);
        BlaubotFactory.createWebSocketMetaDataDTO(DEFAULT_HOST, "/blaubot", DEFAULT_PORT_1);
        BlaubotFactory.createWebSocketServerConnector("localhost", DEFAULT_PORT_1, "/blaubot", ownDevice, UUID.randomUUID().toString());
        BlaubotFactory.createBlaubotWebsocketServer(ownDevice);
        BlaubotFactory.createBlaubotWebsocketServer(ownDevice, DEFAULT_PORT_1);
        BlaubotFactory.createBlaubotWebsocketServer(ownDevice, DEFAULT_INTERFACE, DEFAULT_PORT_1);
    }

    @Test
    public void testJSR82() throws ClassNotFoundException {
        BlaubotFactory.createJsr82Adapter(new BlaubotUUIDSet(appUuid), ownDevice);
        BlaubotFactory.createJsr82BluetoothBlaubot(appUuid);
    }

}
