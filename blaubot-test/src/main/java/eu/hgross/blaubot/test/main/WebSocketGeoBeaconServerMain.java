package eu.hgross.blaubot.test.main;

import java.net.InetAddress;
import java.util.UUID;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.ethernet.BlaubotEthernetAdapter;
import eu.hgross.blaubot.geobeacon.GeoBeaconConstants;
import eu.hgross.blaubot.geobeacon.GeoBeaconServer;
import eu.hgross.blaubot.geobeacon.GeoLocationBeacon;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.ui.SwingDebugView;
import eu.hgross.blaubot.util.Log;

/**
 * Example of how to use the GeoBeaconServer with a Blaubot network of three clients.
 * It will run the GeoBeaconServer on Port 8082 and the clients will use the ethernet adapter implementation.
 * They will occupy the ports 17171, 17172, 17173 for their acceptors and spawn a DebugView instance each.
 */
public class WebSocketGeoBeaconServerMain {
    public static final int WEBSOCKET_ACCEPTOR_PORT = 8082;
    public static double GEO_RADIUS = 0.25d; // 250 metres radius for the beacon

    public static void main(String[] args) throws ClassNotFoundException {
        Log.LOG_LEVEL = Log.LogLevel.DEBUG;

        // Create and start the server
        GeoBeaconServer geoBeaconServer = BlaubotFactory.createWebSocketGeoBeaconServer(WEBSOCKET_ACCEPTOR_PORT, GEO_RADIUS);
        geoBeaconServer.startBeaconServer();

        
        /*
            Now we create the clients using beacons that connect to this geo beacon.
         */
        // create the blaubot instances using the server ...
        final InetAddress localIpAddress = BlaubotFactory.getLocalIpAddress();
        UUID appUUid = UUID.randomUUID();

        // create adapters
        final BlaubotDevice device1 = new BlaubotDevice("Device1");
        final BlaubotDevice device2 = new BlaubotDevice("Device2");
        final BlaubotDevice device3 = new BlaubotDevice("Device3");
        BlaubotEthernetAdapter adapter1 = new BlaubotEthernetAdapter(device1, 17171, localIpAddress);
        BlaubotEthernetAdapter adapter2 = new BlaubotEthernetAdapter(device2, 17172, localIpAddress);
        BlaubotEthernetAdapter adapter3 = new BlaubotEthernetAdapter(device3, 17173, localIpAddress);

        // create static beacon store
        final ConnectionMetaDataDTO beaconServerConnectionData = geoBeaconServer.getAcceptors().get(0).getConnectionMetaData();
        BlaubotBeaconStore beaconStore = new BlaubotBeaconStore();
        beaconStore.putConnectionMetaData(GeoBeaconConstants.GEO_BEACON_SERVER_UNIQUE_DEVICE_ID, beaconServerConnectionData);

        // create beacons
        final IBlaubotConnector connector1 = BlaubotFactory.createBlaubotWebsocketAdapter(device1, "0.0.0.0", 8083).getConnector();
        final GeoLocationBeacon geoLocationBeacon1 = new GeoLocationBeacon(beaconStore, connector1){};
        final IBlaubotConnector connector2 = BlaubotFactory.createBlaubotWebsocketAdapter(device2, "0.0.0.0", 8084).getConnector();
        final GeoLocationBeacon geoLocationBeacon2 = new GeoLocationBeacon(beaconStore, connector2){};
        final IBlaubotConnector connector3 = BlaubotFactory.createBlaubotWebsocketAdapter(device3, "0.0.0.0", 8085).getConnector();
        final GeoLocationBeacon geoLocationBeacon3 = new GeoLocationBeacon(beaconStore, connector3){};
        
        // create blaubot instances
        final Blaubot b1 = BlaubotFactory.createBlaubot(appUUid, device1, adapter1, geoLocationBeacon1);
        final Blaubot b2 = BlaubotFactory.createBlaubot(appUUid, device2, adapter2, geoLocationBeacon2);
        final Blaubot b3 = BlaubotFactory.createBlaubot(appUUid, device3, adapter3, geoLocationBeacon3);

        // start a gui for each blaubot instance
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SwingDebugView.createAndShowGui(b1);
            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SwingDebugView.createAndShowGui(b2);
            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SwingDebugView.createAndShowGui(b3);
            }
        });
    }
}
