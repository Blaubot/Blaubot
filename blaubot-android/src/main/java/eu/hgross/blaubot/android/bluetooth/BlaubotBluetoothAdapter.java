package eu.hgross.blaubot.android.bluetooth;

import java.util.concurrent.Semaphore;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotAdapterConfig;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;

/**
 * BlaubotAdapter-Bluetooth implementation for android. 
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlaubotBluetoothAdapter implements IBlaubotAdapter {
	public static final int NO_PEASANTS_KING_TIMEOUT = 11000;
	public static final int CROWNING_PREPARATION_TIMEOUT = 2500;
    private final IBlaubotDevice ownDevice;
    private BlaubotUUIDSet uuidSet;

    private IBlaubotConnectionAcceptor acceptor;
    private IBlaubotConnector connector;
    protected Semaphore bluetoothAdapterSempaphore;
    private ConnectionStateMachineConfig connectionStateMachineConfig;
    private Blaubot blaubot;
    private BlaubotAdapterConfig adapterConfig;


    public BlaubotBluetoothAdapter(BlaubotUUIDSet uuidSet, IBlaubotDevice ownDevice) {
		this.uuidSet = uuidSet;
        this.ownDevice = ownDevice;
		this.connector = new BlaubotBluetoothConnector(this, ownDevice);
		this.acceptor = new BlaubotBluetoothConnectionAcceptor(this);
		this.bluetoothAdapterSempaphore = new Semaphore(1);
		this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
		this.connectionStateMachineConfig.setCrowningPreparationTimeout(CROWNING_PREPARATION_TIMEOUT);
		this.connectionStateMachineConfig.setKingWithoutPeasantsTimeout(NO_PEASANTS_KING_TIMEOUT);
		this.adapterConfig = new BlaubotAdapterConfig();
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

	public BlaubotUUIDSet getUUIDSet() {
		return uuidSet;
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

}
