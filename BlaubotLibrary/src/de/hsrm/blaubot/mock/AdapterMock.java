package de.hsrm.blaubot.mock;

import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.BlaubotAdapterConfig;
import de.hsrm.blaubot.core.ConnectionStateMachineConfig;
import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;

public class AdapterMock implements IBlaubotAdapter
{
	private BlaubotConnectorMock connector;
	private BlaubotConnectionAcceptorMock acceptor;
	private IBlaubotBeaconInterface beacon;
	private IBlaubotDevice ownDevice = new BlaubotDeviceMock("OWN_DEVICE____");
	private Blaubot blaubot;
	private ConnectionStateMachineConfig connectionStateMachineConfig;
	private BlaubotAdapterConfig adapterConfig;

	public AdapterMock() {
		this.connector = new BlaubotConnectorMock(this);
		this.acceptor = new BlaubotConnectionAcceptorMock(this);
		this.beacon = new BlaubotBeaconMock();
		this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
		this.adapterConfig = new BlaubotAdapterConfig();
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
		return beacon;
	}

	@Override
	public IBlaubotDevice getOwnDevice() {
		return ownDevice;
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
