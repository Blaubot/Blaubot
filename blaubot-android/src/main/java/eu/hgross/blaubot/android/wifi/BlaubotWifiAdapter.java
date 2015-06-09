package eu.hgross.blaubot.android.wifi;

import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotAdapterConfig;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;

/**
 * Using ap mode for the acceptor/connector.
 */
public class BlaubotWifiAdapter implements IBlaubotAdapter {
    private static final int KING_TIMEOUT_WITHOUT_PEASANTS = 35000;
    private static final int CROWNING_PREPARATION_TIME_FACTOR = 3;
    private final ConnectionStateMachineConfig connectionStateMachineConfig;
    private final BlaubotAdapterConfig adapterConfig;
    private final BlaubotWifiAcceptor acceptor;
    private final BlaubotWifiConnector connector;
    private Blaubot blaubot;

    public BlaubotWifiAdapter(IBlaubotDevice ownDevice, BlaubotUUIDSet uuidSet, int acceptorPort, WifiManager wifiManager, ConnectivityManager connectivityManager) {
        this.acceptor = new BlaubotWifiAcceptor(this, ownDevice, uuidSet, wifiManager, connectivityManager, acceptorPort);
        this.connector = new BlaubotWifiConnector(this, ownDevice, uuidSet, wifiManager, connectivityManager);

        // config
        this.adapterConfig = new BlaubotAdapterConfig();
        this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
        this.connectionStateMachineConfig.setCrowningPreparationTimeout(CROWNING_PREPARATION_TIME_FACTOR * adapterConfig.getKeepAliveInterval());
        this.connectionStateMachineConfig.setKingWithoutPeasantsTimeout(KING_TIMEOUT_WITHOUT_PEASANTS);
        ConnectionStateMachineConfig.validateTimeouts(connectionStateMachineConfig, adapterConfig);
    }


    @Override
    public IBlaubotConnector getConnector() {
        return this.connector;
    }

    @Override
    public IBlaubotConnectionAcceptor getConnectionAcceptor() {
        return this.acceptor;
    }

    @Override
    public void setBlaubot(Blaubot blaubotInstance) {
        this.blaubot = blaubotInstance;
    }

    @Override
    public Blaubot getBlaubot() {
        return this.blaubot;
    }

    @Override
    public ConnectionStateMachineConfig getConnectionStateMachineConfig() {
        return connectionStateMachineConfig;
    }

    @Override
    public BlaubotAdapterConfig getBlaubotAdapterConfig() {
        return adapterConfig;
    }

}
