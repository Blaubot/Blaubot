package eu.hgross.blaubot.mock;

import java.util.List;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotAdapterConfig;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;

public class AdapterMock implements IBlaubotAdapter
{
	private BlaubotConnectorMock connector;
	private BlaubotConnectionAcceptorMock acceptor;
	private List<IBlaubotBeacon> beacons;
	private IBlaubotDevice ownDevice = new BlaubotDeviceMock("OWN_DEVICE____");
	private Blaubot blaubot;
	private ConnectionStateMachineConfig connectionStateMachineConfig;
	private BlaubotAdapterConfig adapterConfig;

	public AdapterMock() {
		this.connector = new BlaubotConnectorMock(this);
		this.acceptor = new BlaubotConnectionAcceptorMock(this);
        this.beacons.add(new BlaubotBeaconMock());
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
