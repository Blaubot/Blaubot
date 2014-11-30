package de.hsrm.blaubot.ethernet;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.BlaubotAdapterConfig;
import de.hsrm.blaubot.core.BlaubotUUIDSet;
import de.hsrm.blaubot.core.ConnectionStateMachineConfig;
import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;

/**
 * Ethernet implementation for Blaubot
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotEthernetAdapter implements IBlaubotAdapter {
	private static final int KINGT_TIMEOUT_WITHOUT_PEASANTS = 2500;
	private static final int CROWNING_PREPARATION_TIME_FACTOR = 3;
	private final BlaubotEthernetConnector connector;
	private final BlaubotEthernetAcceptor acceptor;
	private final IBlaubotBeaconInterface beacon;
	private final BlaubotUUIDSet uuidSet;
	private final int acceptorPort;
	private final int beaconPort;
	private InetAddress ownInetAddress;
	private final BlaubotEthernetDevice ownDevice;
	private Blaubot blaubot;
	private ConnectionStateMachineConfig connectionStateMachineConfig;
	private BlaubotAdapterConfig adapterConfig;

	
	/**
	 * Sets up the Adapter for use with the {@link BlaubotEthernetFixedDeviceSetBeacon}.
	 * 
	 * @param uuidSet
	 * @param beaconPort
	 * @param acceptorPort
	 * @param ownInetAddr
	 * @param fixedDevicesSet
	 */
	public BlaubotEthernetAdapter(BlaubotUUIDSet uuidSet, int beaconPort, int acceptorPort, InetAddress ownInetAddr, Set<String> fixedDevicesSet) {
		this.uuidSet = uuidSet;
		this.acceptorPort = acceptorPort;
		this.beaconPort = beaconPort;
		this.ownInetAddress = ownInetAddr;
		this.connector = new BlaubotEthernetConnector(this);
		this.acceptor = new BlaubotEthernetAcceptor(this, acceptorPort);
		this.ownDevice = new BlaubotEthernetDevice(ownInetAddr, acceptorPort, beaconPort, this);
		this.beacon = new BlaubotEthernetFixedDeviceSetBeacon(this, createFixedDeviceSetInstances(fixedDevicesSet), beaconPort);
		this.adapterConfig = new BlaubotAdapterConfig();
		this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
		this.connectionStateMachineConfig.setCrowningPreparationTimeout(CROWNING_PREPARATION_TIME_FACTOR * adapterConfig.getKeepAliveInterval());
		this.connectionStateMachineConfig.setKingWithoutPeasantsTimeout(KINGT_TIMEOUT_WITHOUT_PEASANTS);
		ConnectionStateMachineConfig.validateTimeouts(connectionStateMachineConfig, adapterConfig);
	}
	
	/**
	 * Transforms a set of uniqueId {@link String} into a {@link Set} of {@link IBlaubotDevice} instances.
	 * @param fixedDevicesSet
	 * @return
	 */
	private Set<IBlaubotDevice> createFixedDeviceSetInstances(Set<String> fixedDevicesSet) {
		HashSet<IBlaubotDevice> deviceInstances = new HashSet<IBlaubotDevice>();
		for(String uniqueId : fixedDevicesSet){
			IBlaubotDevice blaubotDevice = connector.createRemoteDevice(uniqueId);
			deviceInstances.add(blaubotDevice);
		}
		return deviceInstances;
	}

	/**
	 * Sets up the Adapter for use with the {@link BlaubotEthernetMulticastBeacon}.
	 * 
	 * @param uuidSet
	 * @param beaconPort
	 * @param beaconBroadcastPort
	 * @param acceptorPort
	 * @param ownInetAddr
	 */
	public BlaubotEthernetAdapter(BlaubotUUIDSet uuidSet, int beaconPort, int beaconBroadcastPort, int acceptorPort, InetAddress ownInetAddr) {
		this.uuidSet = uuidSet;
		this.acceptorPort = acceptorPort;
		this.beaconPort = beaconPort;
		this.ownInetAddress = ownInetAddr;
		this.connector = new BlaubotEthernetConnector(this);
		this.acceptor = new BlaubotEthernetAcceptor(this, acceptorPort);
		this.beacon = new BlaubotEthernetMulticastBeacon(this, beaconPort, beaconBroadcastPort);
		this.ownDevice = new BlaubotEthernetDevice(ownInetAddr, acceptorPort, beaconPort,  this);
		this.adapterConfig = new BlaubotAdapterConfig();
		this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
		this.connectionStateMachineConfig.setCrowningPreparationTimeout(CROWNING_PREPARATION_TIME_FACTOR * adapterConfig.getKeepAliveInterval());
		this.connectionStateMachineConfig.setKingWithoutPeasantsTimeout(KINGT_TIMEOUT_WITHOUT_PEASANTS);
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
	public IBlaubotBeaconInterface getBeaconInterface() {
		return beacon;
	}

	@Override
	public IBlaubotDevice getOwnDevice() {
		return ownDevice;
	}

	protected BlaubotUUIDSet getUuidSet() {
		return uuidSet;
	}
	
	protected int getAcceptorPort() {
		return this.acceptorPort;
	}

	protected int getBeaconPort() {
		return this.beaconPort;
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + acceptorPort;
		result = prime * result + beaconPort;
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
		if (beaconPort != other.beaconPort)
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
