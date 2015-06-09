package eu.hgross.blaubot.android.wifip2p;

import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;

/**
 * Adapter pattern for the IBlaubotWifiDirectEventListener
 */
public class BlaubotWifiDirectEventListenerAdapter implements BlaubotWifiP2PBroadcastReceiver.IBlaubotWifiDirectEventListener {
    @Override
    public void onP2PWifiEnabled() {

    }

    @Override
    public void onP2PWifiDisabled() {

    }

    @Override
    public void onDiscoveryStarted() {

    }

    @Override
    public void onDiscoveryStopped() {

    }

    @Override
    public void onListOfPeersChanged(WifiP2pDeviceList deviceList) {

    }

    @Override
    public void onConnectivityChanged(WifiP2pInfo p2pInfo, NetworkInfo networkInfo, WifiP2pGroup group) {

    }
}
