package eu.hgross.blaubot.ethernet;

import java.net.InetAddress;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotAdapterConfig;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
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
	private final BlaubotUUIDSet uuidSet;
	private final int acceptorPort;
	private InetAddress ownInetAddress;
	private Blaubot blaubot;
	private ConnectionStateMachineConfig connectionStateMachineConfig;
	private BlaubotAdapterConfig adapterConfig;

	
	/**
	 * Sets up the Adapter.
	 * TODO remove InetAddress dependency.
     *
	 * @param uuidSet
	 * @param acceptorPort
	 * @param ownInetAddr
	 */
	public BlaubotEthernetAdapter(IBlaubotDevice ownDevice, BlaubotUUIDSet uuidSet, int acceptorPort, InetAddress ownInetAddr) {
		this.uuidSet = uuidSet;
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + acceptorPort;
		result = prime * result + ((ownInetAddress == null) ? 0 : ownInetAddress.hashCode());
		result = prime * result + ((uuidSet == null) ? 0 : uuidSet.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BlaubotEthernetAdapter other = (BlaubotEthernetAdapter) obj;
		if (acceptorPort != other.acceptorPort)
			return false;
		if (ownInetAddress == null) {
			if (other.ownInetAddress != null)
				return false;
		} else if (!ownInetAddress.equals(other.ownInetAddress))
			return false;
		if (uuidSet == null) {
			if (other.uuidSet != null)
				return false;
		} else if (!uuidSet.equals(other.uuidSet))
			return false;
		return true;
	}

	@Override
	public BlaubotAdapterConfig getBlaubotAdapterConfig() {
		return adapterConfig;
	}
	
	
}
