package eu.hgross.blaubot.android.wifi;

import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;

import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.ethernet.BlaubotEthernetAcceptor;
import eu.hgross.blaubot.util.Log;

/**
 * An acceptor that creates an access point and then utilizes the EthernetAcceptor to accept the
 * actual connections over socket connections.
 */
public class BlaubotWifiAcceptor implements IBlaubotConnectionAcceptor {
    private static final String LOG_TAG = "BlaubotWifiAcceptor";
    private final BlaubotWifiAdapter adapter;
    private final IBlaubotDevice ownDevice;
    private final BlaubotUUIDSet uuidSet;
    private final ConnectivityManager connectivityManager;
    private final WifiManager wifiManager;
    /**
     * The port to be used to accept connections
     */
    private final int acceptorPort;
    private IBlaubotBeaconStore beaconStore;
    private IBlaubotIncomingConnectionListener acceptorListener;
    private IBlaubotListeningStateListener listeningStateListener;

    /**
     * Lock for the start/stop listening methods.
     */
    private final Object startStopMonitor = new Object();
    private BlaubotEthernetAcceptor currentEthernetAcceptor;


    public BlaubotWifiAcceptor(BlaubotWifiAdapter blaubotWifiAdapter, IBlaubotDevice ownDevice, BlaubotUUIDSet uuidSet, WifiManager wifiManager, ConnectivityManager connectivityManager, int acceptorPort) {
        this.adapter = blaubotWifiAdapter;
        this.ownDevice = ownDevice;
        this.uuidSet = uuidSet;
        this.wifiManager = wifiManager;
        this.connectivityManager = connectivityManager;
        this.acceptorPort = acceptorPort;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void startListening() {
        Log.d(LOG_TAG, "Starting to listen");
        final CountDownLatch startLatch = new CountDownLatch(1);
        synchronized (startStopMonitor) {
            wifiManager.setWifiEnabled(false);
            final WifiApUtil apUtil = WifiApUtil.createInstance(wifiManager);
            final WifiConfiguration wifiApConfiguration = apUtil.getWifiApConfiguration();
            apUtil.setWifiApEnabled(wifiApConfiguration, true);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            final InetAddress ownIp = intToInetAddress(connectionInfo.getIpAddress());

            Log.e(LOG_TAG, "myIp:" + ownIp);
            final BlaubotEthernetAcceptor blaubotEthernetAcceptor = new BlaubotEthernetAcceptor(adapter, ownDevice, ownIp, acceptorPort);
            blaubotEthernetAcceptor.setBeaconStore(beaconStore);
            blaubotEthernetAcceptor.setAcceptorListener(new IBlaubotIncomingConnectionListener() {
                @Override
                public void onConnectionEstablished(IBlaubotConnection connection) {
                    acceptorListener.onConnectionEstablished(connection);
                }
            });
            blaubotEthernetAcceptor.setListeningStateListener(new IBlaubotListeningStateListener() {
                @Override
                public void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor) {
                    startLatch.countDown();
                }

                @Override
                public void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor) {
                }
            });
            blaubotEthernetAcceptor.startListening();
            this.currentEthernetAcceptor = blaubotEthernetAcceptor;
        }

        try {
            startLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        listeningStateListener.onListeningStarted(this);
        Log.d(LOG_TAG, "Started listening");
    }

    @Override
    public void stopListening() {
        Log.d(LOG_TAG, "Stopping to listen");
        final CountDownLatch stopLatch = new CountDownLatch(1);

        synchronized (startStopMonitor) {
            WifiApUtil apUtil = WifiApUtil.createInstance(wifiManager);
            BlaubotEthernetAcceptor acceptor = this.currentEthernetAcceptor;
            if(acceptor != null) {
                this.currentEthernetAcceptor.setListeningStateListener(new IBlaubotListeningStateListener() {
                    @Override
                    public void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor) {

                    }

                    @Override
                    public void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor) {
                        stopLatch.countDown();
                    }
                });
                this.currentEthernetAcceptor.stopListening();
            } else {
                stopLatch.countDown();
            }

            // Disable AP mode
            if(apUtil.isWifiApEnabled()) {
                apUtil.setWifiApEnabled(apUtil.getWifiApConfiguration(), false);
            }
        }

        try {
            stopLatch.await();
            this.currentEthernetAcceptor = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        listeningStateListener.onListeningStopped(this);
        Log.d(LOG_TAG, "Stopped listening");
    }

    @Override
    public boolean isStarted() {
        return false;
    }

    @Override
    public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
        this.listeningStateListener = stateListener;
    }

    @Override
    public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
        this.acceptorListener = acceptorListener;
    }

    @Override
    public ConnectionMetaDataDTO getConnectionMetaData() {
        final WifiApUtil apUtil = WifiApUtil.createInstance(wifiManager);
        final WifiConfiguration wifiApConfiguration = apUtil.getWifiApConfiguration();
        final String ssid = wifiApConfiguration.SSID;
        final String psk = wifiApConfiguration.preSharedKey;
        final String ipAddress = intToInetAddress(wifiManager.getConnectionInfo().getIpAddress()).getHostAddress();
        final String macAddress = wifiManager.getConnectionInfo().getMacAddress();
        final ConnectionMetaDataDTO metaDataDTO = new WifiConnectionMetaDataDTO(ssid, psk, ipAddress, macAddress, acceptorPort);
        return metaDataDTO;
    }

    /**
     * Convert a IPv4 address from an integer to an InetAddress.
     * @param hostAddress an int corresponding to the IPv4 address in network byte order
     */
    private static InetAddress intToInetAddress(int hostAddress) {
        byte[] addressBytes = { (byte)(0xff & hostAddress),
                (byte)(0xff & (hostAddress >> 8)),
                (byte)(0xff & (hostAddress >> 16)),
                (byte)(0xff & (hostAddress >> 24)) };

        try {
            return InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
    }
}
