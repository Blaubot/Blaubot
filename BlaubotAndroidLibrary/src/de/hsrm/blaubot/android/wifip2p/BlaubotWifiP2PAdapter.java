package de.hsrm.blaubot.android.wifip2p;

import java.util.HashSet;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import de.hsrm.blaubot.android.IBlaubotBroadcastReceiver;
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
 * Adapter implementation for WIFI Direct on android.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotWifiP2PAdapter implements IBlaubotAdapter, IBlaubotBroadcastReceiver {
	private BlaubotUUIDSet uuidSet;
	private IBlaubotBeaconInterface beaconInterface;
	private IBlaubotConnectionAcceptor acceptor;
	private IBlaubotConnector connector;
	private WifiP2pManager wifiP2pManager;
	private Channel beaconWifiChannel;
	private Set<BlaubotWifiP2PDevice> knownDevices;
	private BlaubotWifiP2PBroadcastReceiver wifiP2PBroadcastReceiver;
	private Blaubot blaubot;
	private ConnectionStateMachineConfig connectionStateMachineConfig;
	private BlaubotAdapterConfig adapterConfig;
	private Channel acceptorWifiChannel;
	private WifiManager wifiManager;
	private BlaubotWifiP2PDevice ownDevice;
	
	public BlaubotWifiP2PAdapter(BlaubotUUIDSet uuidSet, WifiP2pManager manager, WifiManager wifiManager, Channel beaconChannel, Channel acceptorChannel) {
		this.uuidSet = uuidSet;
		this.wifiP2pManager = manager;
		this.wifiManager = wifiManager;
		this.beaconWifiChannel = beaconChannel;
		this.acceptorWifiChannel = acceptorChannel;
		createOwnDevice();
		this.wifiP2PBroadcastReceiver = new BlaubotWifiP2PBroadcastReceiver(manager, beaconChannel);
		this.knownDevices = new HashSet<BlaubotWifiP2PDevice>();
		this.beaconInterface = new BlaubotWifiP2PBeacon(this);
		this.acceptor = new BlaubotWifiP2PAcceptor(this);
		this.connector = new BlaubotWifiP2PConnector(this);
		this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
		this.adapterConfig = new BlaubotAdapterConfig();
		ConnectionStateMachineConfig.validateTimeouts(connectionStateMachineConfig, adapterConfig);
	}
	
	private void createOwnDevice() {
		WifiInfo info = wifiManager.getConnectionInfo();
		WifiP2pDevice dev = new WifiP2pDevice();
		dev.deviceAddress = info.getMacAddress();
		BlaubotWifiP2PDevice own = new BlaubotWifiP2PDevice(this, dev);
		this.ownDevice = own;
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

	@Override
	public IBlaubotDevice getOwnDevice() {
		return ownDevice;
	}

	protected BlaubotUUIDSet getUuidSet() {
		return uuidSet;
	}

	protected WifiP2pManager getWifiP2pManager() {
		return wifiP2pManager;
	}

	protected Channel getBeaconWifiChannel() {
		return beaconWifiChannel;
	}
	
	protected Channel getAcceptorWifiChannel() {
		return acceptorWifiChannel;
	}

	protected Set<BlaubotWifiP2PDevice> getKnownDevices() {
		return knownDevices;
	}

	@Override
	public BroadcastReceiver getReceiver() {
		return wifiP2PBroadcastReceiver;
	}

	@Override
	public IntentFilter getIntentFilter() {
		return BlaubotWifiP2PBroadcastReceiver.createWifiP2PIntentFilter();
	}
	
	protected BlaubotWifiP2PBroadcastReceiver getBlaubotWifiP2PBroadcastReceiver() {
		return wifiP2PBroadcastReceiver;
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
