package eu.hgross.blaubot.android.wifip2p;

import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;

import eu.hgross.blaubot.android.wifip2p.BlaubotWifiP2PBroadcastReceiver.IBlaubotWifiDirectEventListener;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;

/**
 * 
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlaubotWifiP2PAcceptor implements IBlaubotConnectionAcceptor {
	private final BlaubotWifiP2PAdapter adapter;
	private boolean started = false;
	private IBlaubotListeningStateListener listeningStateListener;
	private IBlaubotIncomingConnectionListener incomingConnectionListener;
    private IBlaubotBeaconStore beaconStore;

    protected BlaubotWifiP2PAcceptor(BlaubotWifiP2PAdapter blaubotWifiP2PAdapter) {
		this.adapter = blaubotWifiP2PAdapter;
		this.adapter.getBlaubotWifiP2PBroadcastReceiver().addEventListener(wifiDirectEventListener);
	}

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

	private IBlaubotWifiDirectEventListener wifiDirectEventListener = new IBlaubotWifiDirectEventListener() {
		
		@Override
		public void onP2PWifiEnabled() {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onP2PWifiDisabled() {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onListOfPeersChanged(WifiP2pDeviceList deviceList) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onDiscoveryStopped() {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onDiscoveryStarted() {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onConnectivityChanged(WifiP2pInfo p2pInfo, NetworkInfo networkInfo, WifiP2pGroup group) {
			// TODO Auto-generated method stub
			
		}
	};

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
    }

    @Override
	public void startListening() {
		if(this.started)
			return;
		this.started = true;
		if(listeningStateListener != null) {
			listeningStateListener.onListeningStarted(this);
		}
	}

	@Override
	public void stopListening() {
		if(!this.started)
			return;
		this.started = false;
		if(listeningStateListener != null) {
			listeningStateListener.onListeningStopped(this);
		}
	}

	@Override
	public boolean isStarted() {
		return started;
	}

	@Override
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
		this.listeningStateListener = stateListener;
	}

	@Override
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
		this.incomingConnectionListener = acceptorListener;
	}

    @Override
    public ConnectionMetaDataDTO getConnectionMetaData() {
        // TODO: find out what is really necessary here
        return null;
    }

}
