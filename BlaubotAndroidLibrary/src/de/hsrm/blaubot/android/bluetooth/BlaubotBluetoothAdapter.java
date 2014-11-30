package de.hsrm.blaubot.android.bluetooth;

import java.util.concurrent.Semaphore;

import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.BlaubotAdapterConfig;
import de.hsrm.blaubot.core.BlaubotUUIDSet;
import de.hsrm.blaubot.core.ConnectionStateMachineConfig;
import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.acceptor.discovery.TimeoutList;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;

/**
 * BlaubotAdapter-Bluetooth implementation for android. 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotBluetoothAdapter implements IBlaubotAdapter {
	private static final int NO_PEASANTS_KING_TIMEOUT = 11000;
	private static final int CROWNING_PREPARATIN_TIMEOUT = 2500;
	private static final long NEGATIVE_LIST_TIMEOUT = 1300;  

	private BlaubotUUIDSet uuidSet;
	private IBlaubotBeaconInterface beaconInterface;
	private IBlaubotConnectionAcceptor acceptor;
	private IBlaubotConnector connector;
	protected TimeoutList<IBlaubotDevice> notAvailableDevices = new TimeoutList<IBlaubotDevice>(NEGATIVE_LIST_TIMEOUT);
	protected Semaphore bluetoothAdapterSempaphore;
	private ConnectionStateMachineConfig connectionStateMachineConfig;
	private Blaubot blaubot;
	private BlaubotAdapterConfig adapterConfig;

	
	public BlaubotBluetoothAdapter(BlaubotUUIDSet uuidSet) {
		this.uuidSet = uuidSet;
		this.beaconInterface = new BlaubotBluetoothBeacon(this);
		this.connector = new BlaubotBluetoothConnector(this);
		this.acceptor = new BlaubotBluetoothConnectionAcceptor(this);
		this.bluetoothAdapterSempaphore = new Semaphore(1);
		this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
		this.connectionStateMachineConfig.setCrowningPreparationTimeout(CROWNING_PREPARATIN_TIMEOUT);
		this.connectionStateMachineConfig.setKingWithoutPeasantsTimeout(NO_PEASANTS_KING_TIMEOUT);
		this.adapterConfig = new BlaubotAdapterConfig();
		ConnectionStateMachineConfig.validateTimeouts(connectionStateMachineConfig, adapterConfig);
	}
	
	/**
	 * Sould be called by the connector if a connection to the given device failed.
	 * @param device the device
	 */
	protected void onConnectionToDeviceFailed(IBlaubotDevice device) {
		notAvailableDevices.report(device);
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
	public IBlaubotBeaconInterface getBeaconInterface() {
		return beaconInterface;
	}

	public BlaubotUUIDSet getUUIDSet() {
		return uuidSet;
	}

	@Override
	public IBlaubotDevice getOwnDevice() {
		return OwnAndroidBluetoothDevice.getInstance();
	}

	@Override
	public void setBlaubot(Blaubot blaubotInstance) {
		this.blaubot = blaubotInstance;
	}
	
	protected Blaubot getBlaubot() {
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
