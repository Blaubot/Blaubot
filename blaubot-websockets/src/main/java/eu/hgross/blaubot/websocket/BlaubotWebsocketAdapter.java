package eu.hgross.blaubot.websocket;

import java.util.Arrays;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotAdapterConfig;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;

public class BlaubotWebsocketAdapter implements IBlaubotAdapter {
    /**
     * The path for the web socket handshake
     */
    public static final String WEBSOCKET_PATH = "/blaubot";

    /**
     * The max allowable websocket frame size. Above this limits, connections will be disconnected.
     * Note: This limit exists because it is a big gate for DoS attacks and is usually much lower by
     * default, then used by blaubot.
     */
    public static final int MAX_WEBSOCKET_FRAME_SIZE = Integer.MAX_VALUE;

    /**
     * The request parameter name for the unique device id when connecting to WEBSOCKET_PATH
     */
    public static final String URI_PARAM_UNIQUEDEVICEID = "uniqueDeviceId";

    private static final int KING_TIMEOUT_WITHOUT_PEASANTS = 10000;
    private static final int CROWNING_PREPARATION_TIME_FACTOR = 3;

    private final BlaubotWebsocketConnector connector;
    private final BlaubotWebsocketAcceptor acceptor;
    private final BlaubotAdapterConfig adapterConfig;
    private final ConnectionStateMachineConfig connectionStateMachineConfig;
    private Blaubot blaubot;

    /**
     *
     * @param ownDevice our own device
     * @param hostAddress the own host address (interfaces to bind to)
     * @param acceptorPort the acceptor port to use (to open the websocket server on)
     */
    public BlaubotWebsocketAdapter(IBlaubotDevice ownDevice, String hostAddress, int acceptorPort) {
        this.connector = new BlaubotWebsocketConnector(this, ownDevice);
        this.acceptor = new BlaubotWebsocketAcceptor(this, hostAddress, acceptorPort);
        this.adapterConfig = new BlaubotAdapterConfig();
        this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
        this.connectionStateMachineConfig.setCrowningPreparationTimeout(CROWNING_PREPARATION_TIME_FACTOR * adapterConfig.getKeepAliveInterval());
        this.connectionStateMachineConfig.setKingWithoutPeasantsTimeout(KING_TIMEOUT_WITHOUT_PEASANTS);
        ConnectionStateMachineConfig.validateTimeouts(connectionStateMachineConfig, adapterConfig);
    }


    @Override
    public IBlaubotConnector getConnector() {
        return connector;
    }

    @Override
    public IBlaubotConnectionAcceptor getConnectionAcceptor() {
        return acceptor;
    }

    @Override
    public void setBlaubot(Blaubot blaubotInstance) {
        this.blaubot = blaubotInstance;
    }

    @Override
    public Blaubot getBlaubot() {
        return blaubot;
    }

    @Override
    public ConnectionStateMachineConfig getConnectionStateMachineConfig() {
        return connectionStateMachineConfig;
    }

    @Override
    public BlaubotAdapterConfig getBlaubotAdapterConfig() {
        return adapterConfig;
    }


    public static void main(String[] args) {
        // server device
        WebsocketConnectionMetaDataDTO serverDto = new WebsocketConnectionMetaDataDTO("localhost", "/websocket", 8080);
        BlaubotDevice serverDevice = new BlaubotDevice("server1");
        BlaubotBeaconStore beaconStore = new BlaubotBeaconStore();
        beaconStore.putConnectionMetaData("server1", Arrays.asList(new ConnectionMetaDataDTO[]{serverDto}));

        String hostAddress = BlaubotFactory.getLocalIpAddress().getHostAddress();
        BlaubotWebsocketAdapter adapter = new BlaubotWebsocketAdapter(serverDevice, hostAddress, 8080);
        IBlaubotConnectionAcceptor connectionAcceptor = adapter.getConnectionAcceptor();
        connectionAcceptor.setAcceptorListener(new IBlaubotIncomingConnectionListener() {
            @Override
            public void onConnectionEstablished(IBlaubotConnection connection) {
                System.out.println("Got connection: " + connection);
            }
        });
        connectionAcceptor.startListening();

        IBlaubotConnector connector = adapter.getConnector();
        IBlaubotConnection connectorResult = connector.connectToBlaubotDevice(serverDevice);

        System.out.println("Connector got connection: " + connectorResult);
    }
}
