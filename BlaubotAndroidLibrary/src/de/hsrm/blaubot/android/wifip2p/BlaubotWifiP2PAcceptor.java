package de.hsrm.blaubot.android.wifip2p;

import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import de.hsrm.blaubot.android.wifip2p.BlaubotWifiP2PBroadcastReceiver.IBlaubotWifiDirectEventListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotListeningStateListener;

/**
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotWifiP2PAcceptor implements IBlaubotConnectionAcceptor {
	private final BlaubotWifiP2PAdapter adapter;
	private boolean started = false;
	private IBlaubotListeningStateListener listeningStateListener;
	private IBlaubotIncomingConnectionListener incomingConnectionListener;
	
	protected BlaubotWifiP2PAcceptor(BlaubotWifiP2PAdapter blaubotWifiP2PAdapter) {
		this.adapter = blaubotWifiP2PAdapter;
		this.adapter.getBlaubotWifiP2PBroadcastReceiver().addEventListener(wifiDirectEventListener);
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
		public void onConnectivityChanged(WifiP2pInfo p2pInfo, NetworkInfo networkInfo) {
			// TODO Auto-generated method stub
			
		}
	};

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

}
