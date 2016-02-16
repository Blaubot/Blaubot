package eu.hgross.blaubot.ethernet;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.ExchangeStatesTask;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.util.KingdomCensusLifecycleListener;
import eu.hgross.blaubot.util.Log;

/**
 * The bonjour beacon.
 * Exploits the bonjour protocol to send beacon states.
 * <p/>
 * Android note:
 * If you use this on Android, ensure that you acquired a MulticastLock from the WiFiManager!
 * WifiManager wifi = (WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
 * mMulticastLock = wifi.createMulticastLock("BlaubotMulticastLock");
 * mMulticastLock.setReferenceCounted(true);
 * mMulticastLock.acquire();
 * and obviously don't forget to release it later.
 */
public class BlaubotBonjourBeacon implements IBlaubotBeacon, IEthernetBeacon, Closeable {
    private static final long BONJOUR_LIST_DISCOVERY_INETRVAL = 5000;
    public static final int SLEEP_TIME_BETWEEN_BEACON_CONNECTIONS = 100;
    private final InetAddress inetAddress;
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private static final String LOG_TAG = "BlaubotBonjourBeacon";
    public static final String BONJOUR_KEY_BEACON_UUID = "BI"; // only 2 bytes allowed
    public static final String BONJOUR_KEY_UNIQUE_ID = "DI"; // only 2 bytes allowed
    private final int beaconPort;
    private volatile boolean discoveryActivated = true;
    private IBlaubotDevice ownDevice;
    private BlaubotUUIDSet uuidSet;
    private IBlaubotBeaconStore beaconStore;
    private IBlaubotListeningStateListener listeningStateListener;
    private IBlaubotIncomingConnectionListener acceptorListener;
    private IBlaubotDiscoveryEventListener discoveryListener;
    private Blaubot blaubot;

    private JmDNS jmDns;
    private volatile ServiceInfo currentServiceInfo;
    private volatile EthernetBeaconAcceptThread acceptThread;
    private Object startStopMonitor = new Object();
    private volatile IBlaubotState currentState;
    private volatile Timer timer;
    private KingdomCensusLifecycleListener kingdomCensusLifecycleListener;

    public BlaubotBonjourBeacon(InetAddress inetAddress, int beaconPort) {
        this.beaconPort = beaconPort;
        this.inetAddress = inetAddress;
    }

    private final ServiceListener bonjourServiceListener = new ServiceListener() {
        @Override
        public void serviceAdded(ServiceEvent event) {
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "serviceAdded " + event);
            }
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "serviceRemoved " + event);
            }
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "serviceResolved" + event);
            }
            startBeaconExchange(event.getInfo());

        }
    };

    /**
     * Tries to connect to the beacon (if relevant beacon) and starts the state exchange, if connection was successful.
     * @param serviceInfo the jmDNS service info that discovered the device
     */
    private void startBeaconExchange(ServiceInfo serviceInfo) {
        // -- we found another blaubot service running
        String beaconUuid = serviceInfo.getPropertyString(BONJOUR_KEY_BEACON_UUID);
        if (!uuidSet.getBeaconUUID().toString().equals(beaconUuid)) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Received a blaubot service event, but the beacon uuid's didn't match. There are either multiple different apps running on the network or something is wrong with your app's uuid.");
            }
            return;
        }

        // -- same beacon uuid as ours
        final String uniqueDeviceId = serviceInfo.getPropertyString(BONJOUR_KEY_UNIQUE_ID);
        if (uniqueDeviceId.equals(ownDevice.getUniqueDeviceID())) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Received our own service advertisement, ignoring");
            }
            return;
        }

        // -- connect and be happy
        // get inet addr and port
        final Inet4Address[] inet4Addresses = serviceInfo.getInet4Addresses();
        if (inet4Addresses.length < 1) {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not get the inet addr for " + uniqueDeviceId + "'s beacon");
            }
            return;
        }
        final int remoteBeaconPort = serviceInfo.getPort();
        InetAddress remoteDeviceAddr = inet4Addresses[0]; // take first

        // try to connect, then exchange states via tcp/ip
        IBlaubotDevice remoteDevice = new BlaubotDevice(uniqueDeviceId);
        Socket clientSocket;
        try {
            clientSocket = new Socket(remoteDeviceAddr, remoteBeaconPort);
            BlaubotEthernetUtils.sendOwnUniqueIdThroughSocket(ownDevice, clientSocket);
            BlaubotEthernetConnection connection = new BlaubotEthernetConnection(remoteDevice, clientSocket);
            final List<ConnectionMetaDataDTO> ownAcceptorsMetaDataList = BlaubotAdapterHelper.getConnectionMetaDataList(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
            ExchangeStatesTask exchangeStatesTask = new ExchangeStatesTask(ownDevice, connection, currentState, ownAcceptorsMetaDataList, beaconStore, discoveryListener);
            exchangeStatesTask.run();
        } catch (IOException e) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Connection to " + remoteDevice + "'s beacon failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
        this.ownDevice = blaubot.getOwnDevice();
        this.uuidSet = blaubot.getUuidSet();
        this.kingdomCensusLifecycleListener = new KingdomCensusLifecycleListener(ownDevice);
        this.blaubot.addLifecycleListener(kingdomCensusLifecycleListener);
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return null;
    }

    @Override
    public void startListening() {
        synchronized (startStopMonitor) {
            if (isStarted()) {
                return;
            }
            // only on first start, check jdmDns state
            if (this.jmDns == null) {
                try {
                    this.jmDns = JmDNS.create(inetAddress);
                    this.jmDns.addServiceListener(BlaubotConstants.BLAUBOT_BEACON_BONJOUR_SERVICE_NAME, bonjourServiceListener);
                    this.jmDns.addServiceTypeListener(new ServiceTypeListener() {
                        @Override
                        public void serviceTypeAdded(ServiceEvent event) {
                            if(Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "serviceTypeAdded " + event);
                            }
                        }

                        @Override
                        public void subTypeForServiceTypeAdded(ServiceEvent event) {
                            if(Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "subTypeForServiceTypeAdded " + event);
                            }
                        }
                    });
                } catch (IOException e) {
                    // should already be handled by the creation of the inetAddress
                    throw new RuntimeException(e);
                }
            }

            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Beacon is starting to listen for incoming connections on port " + beaconPort);
            }
            acceptThread = new EthernetBeaconAcceptThread(acceptorListener, this);
            acceptThread.start();


            // build new service info
            final HashMap<String, String> values = new HashMap<>();
            values.put(BONJOUR_KEY_BEACON_UUID, uuidSet.getBeaconUUID().toString());
            values.put(BONJOUR_KEY_UNIQUE_ID, ownDevice.getUniqueDeviceID());

            ServiceInfo serviceInfo = ServiceInfo.create(BlaubotConstants.BLAUBOT_BEACON_BONJOUR_SERVICE_NAME, ownDevice.getUniqueDeviceID(), beaconPort, 0, 0, values);

            // register new service info
            try {
                if(Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Registering new Bonjour service entry ...");
                }
                jmDns.registerService(serviceInfo);
                currentServiceInfo = serviceInfo;
                if(Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "New Bonjour service entry registered.");
                }
            } catch (IOException e) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Failed to register serviceInfo for bonjour", e);
                }
                throw new RuntimeException(e);
            }


            if (listeningStateListener != null) {
                listeningStateListener.onListeningStarted(this);
            }
        }
    }

    @Override
    public void stopListening() {
        synchronized (startStopMonitor) {
            if (!isStarted()) {
                return;
            }

            // unregister old
            if (currentServiceInfo != null) {
                if(Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Unregistering old Bonjour service entry ...");
                }
                jmDns.unregisterService(currentServiceInfo);
                if(Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Unregistered old Bonjour service entry.");
                }
            }

            if (acceptThread != null && acceptThread.isAlive()) {
                try {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Waiting for beacon accept thread to finish ...");
                    }
                    acceptThread.interrupt();
                    acceptThread.join();
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Beacon accept thread to finished ...");
                    }
                } catch (InterruptedException e) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, e);
                    }
                }
                acceptThread = null;
            }


            if (listeningStateListener != null) {
                listeningStateListener.onListeningStopped(this);
            }
        }
    }

    @Override
    public boolean isStarted() {
        return acceptThread != null && acceptThread.isAlive();
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
        return null;
    }

    @Override
    public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
        this.discoveryListener = discoveryEventListener;
    }

    @Override
    public void onConnectionStateMachineStateChanged(IBlaubotState state) {
        this.currentState = state;
    }

    @Override
    public void setDiscoveryActivated(boolean active) {
        synchronized (startStopMonitor) {
            if (!active) {
                if (timer != null) {
                    timer.cancel();
                    timer.purge();
                    timer = null;
                }
            } else {
                if (timer == null) {
                    timer = new Timer();
                    timer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            if (jmDns != null && isStarted()) {
                                final ServiceInfo[] list = jmDns.list(BlaubotConstants.BLAUBOT_BEACON_BONJOUR_SERVICE_NAME);
                                for (ServiceInfo serviceInfo : createRelevantDevicesList(list)) {
                                    startBeaconExchange(serviceInfo);
                                    try {
                                        Thread.sleep(SLEEP_TIME_BETWEEN_BEACON_CONNECTIONS);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }, 0, BONJOUR_LIST_DISCOVERY_INETRVAL);
                }
            }
            this.discoveryActivated = active;
        }
    }

    /**
     * Based on the bonjour informations, filters the list of service infos to the most relevant
     * devices (filter out connected devices, ...)
     * @param serviceInfos the bonjour data
     * @return the filtered list of serviceInfo objects
     */
    private List<ServiceInfo> createRelevantDevicesList(ServiceInfo[] serviceInfos) {
        final Set<String> connectedUniqueIds = kingdomCensusLifecycleListener.getConnectedUniqueIds();
        ArrayList<ServiceInfo> filtered = new ArrayList<>();
        for(ServiceInfo serviceInfo : Arrays.asList(serviceInfos)) {
            final String beaconUUID = serviceInfo.getPropertyString(BONJOUR_KEY_BEACON_UUID);
            final String uniqueDeviceID = serviceInfo.getPropertyString(BONJOUR_KEY_UNIQUE_ID);
            if(beaconUUID == null || uniqueDeviceID == null) {
//                System.out.println("skip: " + serviceInfo);
                continue;
            }
            if(beaconUUID.toString().equals(beaconUUID) && !connectedUniqueIds.contains(uniqueDeviceID)) {
                filtered.add(serviceInfo);
            } else {
//                System.out.println("skip: " + serviceInfo);
            }

        }
        return filtered;
    }

    @Override
    public Thread getAcceptThread() {
        return acceptThread;
    }

    @Override
    public int getBeaconPort() {
        return beaconPort;
    }

    @Override
    public void close() throws IOException {
        if (this.executorService != null) {
            this.executorService.shutdown();
        }
        if (this.jmDns != null) {
            this.jmDns.unregisterAllServices();
            this.jmDns.close();
        }
    }
}
