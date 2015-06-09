package eu.hgross.blaubot.android.wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.connector.IncompatibleBlaubotDeviceException;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.ethernet.BlaubotEthernetConnector;
import eu.hgross.blaubot.util.Log;

/**
 * TODO: remove added wifi configs from android os
 */
public class BlaubotWifiConnector implements IBlaubotConnector {
    private static final List<String> COMPATIBLE_ACCEPTOR_TYPES = Arrays.asList(WifiConnectionMetaDataDTO.ACCEPTOR_TYPE);
    private static final String LOG_TAG = "BlaubotWifiConnector";
    /**
     * The timeout for the arp cache (for getting the ip from it)
     */
    private static final long ARP_TIMEOUT = 5000;
    /**
     * The min time between arp reads.
     */
    private static final long ARP_INTERVAL = 100;
    private final BlaubotWifiAdapter adapter;
    private final IBlaubotDevice ownDevice;
    private final BlaubotUUIDSet uuidSet;
    private final WifiManager wifiManager;
    private final ConnectivityManager connectivityManager;
    private IBlaubotBeaconStore beaconStore;
    private IBlaubotIncomingConnectionListener incomingConnectionListener;
    private Context currentContext;
    /**
     * Will contain all used WifiConnector instances and can later be used to remove the added wifi configurations from the android system
     */
    private final Set<WifiConnector> usedWifiConnectorSet;

    public BlaubotWifiConnector(BlaubotWifiAdapter blaubotWifiAdapter, IBlaubotDevice ownDevice, BlaubotUUIDSet uuidSet, WifiManager wifiManager, ConnectivityManager connectivityManager) {
        this.usedWifiConnectorSet = Collections.newSetFromMap(new ConcurrentHashMap<WifiConnector, Boolean>());
        this.adapter = blaubotWifiAdapter;
        this.ownDevice = ownDevice;
        this.uuidSet = uuidSet;
        this.wifiManager = wifiManager;
        this.connectivityManager = connectivityManager;
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
    public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener acceptorConnectorListener) {
        this.incomingConnectionListener = acceptorConnectorListener;
    }

    /**
     * Private class that overrides some implementation details of the ethernet conncetor to delegate the
     * connection heavy lifting.
     */
    private class WifiEthernetConnector extends BlaubotEthernetConnector {
        public WifiEthernetConnector() {
            super(adapter, ownDevice);
            setBeaconStore(beaconStore);
        }

        @Override
        public List<String> getSupportedAcceptorTypes() {
            final List<String> supportedAcceptorTypes = new ArrayList<>(super.getSupportedAcceptorTypes());
            supportedAcceptorTypes.addAll(COMPATIBLE_ACCEPTOR_TYPES);
            return supportedAcceptorTypes;
        }
    }

    @Override
    public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) {
        final String uniqueDeviceID = blaubotDevice.getUniqueDeviceID();
        List<ConnectionMetaDataDTO> lastKnownConnectionMetaData = beaconStore.getLastKnownConnectionMetaData(uniqueDeviceID);
        if(lastKnownConnectionMetaData == null) {
            if(Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not get connection meta data for unique device id " + uniqueDeviceID);
            }
            return null;
        }

        // take the first supported acceptor, if any
        final List<ConnectionMetaDataDTO> supportedAcceptors = BlaubotAdapterHelper.filterBySupportedAcceptorTypes(lastKnownConnectionMetaData, getSupportedAcceptorTypes());
        if(supportedAcceptors.isEmpty()) {
            if(Log.logErrorMessages()) {
                Log.e(LOG_TAG, "No supported acceptors in meta data to connect to " + uniqueDeviceID + " unfiltered list: " + lastKnownConnectionMetaData);
            }
            throw new IncompatibleBlaubotDeviceException(blaubotDevice + " could not get acceptor meta data for this device.");
        }

        // take first metadata
        ConnectionMetaDataDTO dto = supportedAcceptors.get(0);

        // get connection data
        // -- the metadata has to be of type wifi
        WifiConnectionMetaDataDTO wifiConnectionMetaDataDTO = new WifiConnectionMetaDataDTO(dto);
        final String psk = wifiConnectionMetaDataDTO.getPsk();
        final String ssid = wifiConnectionMetaDataDTO.getSsid();

        // check if ap mode is on
        WifiApUtil apUtil = WifiApUtil.createInstance(wifiManager);
        if(apUtil.isWifiApEnabled()) {
            apUtil.setWifiApEnabled(apUtil.getWifiApConfiguration(), false);
        }

        // now connect to wifi, then to socket
        final AtomicBoolean wifiConnectResult = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        final WifiConnector wc = new WifiConnector(connectivityManager, wifiManager, ssid, psk);
        Log.d(LOG_TAG, "Connecting to wifi with ssid " + ssid + " and psk " + psk);
        wc.connect(new WifiConnector.IWifiConnectorCallback() {
            @Override
            public void onSuccess() {
                Log.d(LOG_TAG, "Connected to wifi with ssid " + ssid + " and psk " + psk);
                wifiConnectResult.set(true);

                // memorize the connector to remove the config later on
                usedWifiConnectorSet.add(wc);

                latch.countDown();
            }

            @Override
            public void onFailure() {
                Log.w(LOG_TAG, "Connection to wifi with ssid " + ssid + " and psk " + psk + " failed");
                wc.removeAddedWifiConfiguration();
                latch.countDown();
            }
        });




        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }

        if(wifiConnectResult.get()) {
            // try to get the ip from the arp cache (hacky but cool) and modify the meta data accordingly
            final String acceptorDeviceMacAddr = wifiConnectionMetaDataDTO.getMacAddress();
            final String hostIp = getHostIpFromArpCache(acceptorDeviceMacAddr, ARP_TIMEOUT);

            if(hostIp == null) {
                Log.d(LOG_TAG, "Failed to get the ip address for mac " + acceptorDeviceMacAddr);
                wc.removeAddedWifiConfiguration();
                return null;
            }

            Log.d(LOG_TAG, "Got ip for mac " + acceptorDeviceMacAddr + " from arp cache: " + hostIp);

            // now modify the meta data
            wifiConnectionMetaDataDTO.setIpAddress(hostIp);


            Log.d(LOG_TAG, "Utilizing specialized EthernetConnector to create socket connection");
            // now connect to ethernet socket with the specialized EthernetConnector implementation
            BlaubotEthernetConnector ethernetConnector = new WifiEthernetConnector();
            final IBlaubotConnection connection = ethernetConnector.connectToBlaubotDevice(blaubotDevice, wifiConnectionMetaDataDTO);
            if(connection == null) {
                wc.removeAddedWifiConfiguration();
            } else {
                // - was successful, add our listener and inform the incoming listener
                // kill the wifi connection and remove the config on disconnection
                connection.addConnectionListener(new IBlaubotConnectionListener() {
                    @Override
                    public void onConnectionClosed(IBlaubotConnection connection) {
                        wc.removeAddedWifiConfiguration();
                    }
                });

                if(incomingConnectionListener != null) {
                    incomingConnectionListener.onConnectionEstablished(connection);
                }
            }
            return connection;
        }

        // -- wifi conncet failed
        Log.w(LOG_TAG, "WifiConnect to " + uniqueDeviceID + " failed.");
        return null;
    }

    @Override
    public List<String> getSupportedAcceptorTypes() {
        return COMPATIBLE_ACCEPTOR_TYPES;
    }

    /**
     * Tries to get the ip based on the host's macAddress by scanning the arp-cache.
     * @param macAddress the host's mac address
     * @param giveUpAfter the amount of time (ms) after which we give up to get an ip address from arp
     * @return the corresponding ip address or null, if not possible
     */
    private String getHostIpFromArpCache(String macAddress, long giveUpAfter) {
        final long start = System.currentTimeMillis();
        String ip = null;
        int i = 0;
        long delta = -1;
        while(ip == null) {
            if(i++ > 0) {
                try {
                    Thread.sleep(ARP_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
            final long now = System.currentTimeMillis();
            delta = now - start;
            if(delta > giveUpAfter) {
                break;
            }
            ip = WifiUtils.getIpByMACFromARP(macAddress);
            Log.d(LOG_TAG, "IP from ARP: " + ip);
        }
        if(ip != null) {
            Log.d(LOG_TAG, "Obtained the IP from the arp cache in " + delta + " ms" );
        }
        return ip;
    }
}
