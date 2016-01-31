package eu.hgross.blaubot.ethernet;

import java.net.InetAddress;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotAdapterConfig;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;

/**
 * Ethernet implementation for Blaubot
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotEthernetAdapter implements IBlaubotAdapter {
	private static final int KING_TIMEOUT_WITHOUT_PEASANTS = 2500;
	private static final int CROWNING_PREPARATION_TIME_FACTOR = 3;
	private final BlaubotEthernetConnector connector;
	private final BlaubotEthernetAcceptor acceptor;
	private final int acceptorPort;
	private InetAddress ownInetAddress;
	private Blaubot blaubot;
	private ConnectionStateMachineConfig connectionStateMachineConfig;
	private BlaubotAdapterConfig adapterConfig;

	
	/**
	 * Sets up the Adapter.
     *
	 * @param acceptorPort
	 * @param ownInetAddr
	 */
	public BlaubotEthernetAdapter(IBlaubotDevice ownDevice, int acceptorPort, InetAddress ownInetAddr) {
		// TODO remove InetAddress dependency.
		this.acceptorPort = acceptorPort;
		this.ownInetAddress = ownInetAddr;
		this.connector = new BlaubotEthernetConnector(this, ownDevice);
		this.acceptor = new BlaubotEthernetAcceptor(this, ownDevice, ownInetAddress, acceptorPort);
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

	protected int getAcceptorPort() {
		return this.acceptorPort;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlaubotEthernetAdapter that = (BlaubotEthernetAdapter) o;

        if (acceptorPort != that.acceptorPort) return false;
        return !(ownInetAddress != null ? !ownInetAddress.equals(that.ownInetAddress) : that.ownInetAddress != null);

    }

    @Override
    public int hashCode() {
        int result = acceptorPort;
        result = 31 * result + (ownInetAddress != null ? ownInetAddress.hashCode() : 0);
        return result;
    }

    @Override
	public BlaubotAdapterConfig getBlaubotAdapterConfig() {
		return adapterConfig;
	}
	
	
}
