package eu.hgross.blaubot.android.wifip2p;

import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;

import java.util.HashSet;
import java.util.Set;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotAdapterConfig;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;

/**
 * Adapter implementation for WIFI Direct on android.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlaubotWifiP2PAdapter implements IBlaubotAdapter {
	private BlaubotUUIDSet uuidSet;
    private IBlaubotConnectionAcceptor acceptor;
    private IBlaubotConnector connector;
    private WifiP2pManager wifiP2pManager;
    private Set<BlaubotWifiP2PDevice> knownDevices;
    private Blaubot blaubot;
    private ConnectionStateMachineConfig connectionStateMachineConfig;
    private BlaubotAdapterConfig adapterConfig;
    private Channel acceptorWifiChannel;
    private WifiManager wifiManager;
    private BlaubotWifiP2PBroadcastReceiver blaubotWifiP2PBroadcastReceiver;

    public BlaubotWifiP2PAdapter(BlaubotUUIDSet uuidSet, WifiP2pManager manager, WifiManager wifiManager, Channel acceptorChannel) {
		this.uuidSet = uuidSet;
		this.wifiP2pManager = manager;
		this.wifiManager = wifiManager;
		this.acceptorWifiChannel = acceptorChannel;
		this.knownDevices = new HashSet<>();
		this.blaubotWifiP2PBroadcastReceiver = new BlaubotWifiP2PBroadcastReceiver(manager, acceptorChannel);
        this.acceptor = new BlaubotWifiP2PAcceptor(this);
		this.connector = new BlaubotWifiP2PConnector(this);
		this.connectionStateMachineConfig = new ConnectionStateMachineConfig();
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

	protected BlaubotUUIDSet getUuidSet() {
		return uuidSet;
	}

	protected WifiP2pManager getWifiP2pManager() {
		return wifiP2pManager;
	}

	protected Channel getAcceptorWifiChannel() {
		return acceptorWifiChannel;
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


    public BlaubotWifiP2PBroadcastReceiver getBlaubotWifiP2PBroadcastReceiver() {
        return blaubotWifiP2PBroadcastReceiver;
    }
}
