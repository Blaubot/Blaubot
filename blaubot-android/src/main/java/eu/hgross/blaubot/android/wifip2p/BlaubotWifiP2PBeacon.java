package eu.hgross.blaubot.android.wifip2p;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.Vibrator;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import eu.hgross.blaubot.android.IBlaubotAndroidComponent;
import eu.hgross.blaubot.android.IBlaubotBroadcastReceiver;
import eu.hgross.blaubot.android.wifip2p.BlaubotWifiP2PBroadcastReceiver.IBlaubotWifiDirectEventListener;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.IUnidentifiedBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.ExchangeStatesTask;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.ethernet.BlaubotEthernetConnection;
import eu.hgross.blaubot.ethernet.BlaubotEthernetUtils;
import eu.hgross.blaubot.ethernet.EthernetBeaconAcceptThread;
import eu.hgross.blaubot.ethernet.IEthernetBeacon;
import eu.hgross.blaubot.util.KingdomCensusLifecycleListener;
import eu.hgross.blaubot.util.Log;

/**
 * We are able to signal some defined strings (see http://upnp.org/specs/dm/UPnP-dm-BasicManagement-v1-Service.pdf)
 * over the upnp framework, so we pick one of them to indicate that there is a blaubot instance running.
 * 
 * The beacons then search for this upnp string/service and connect to them to probe if this devices
 * are really blaubot instances. If we find a blaubot instance, they exchange the beaconUUID, and if their beaconIds match
 * the usual {@link BeaconMessage} to exchange their states.
 * 
 * Sadly we can not communicate additional attributes via upnp service requests (which we should be according to the upnp standard)
 * on android so we have to actively ask for this attributes (beaconId, state) by connecting to the device.
 * see: https://code.google.com/p/android/issues/detail?id=40003
 * 
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class BlaubotWifiP2PBeacon implements IBlaubotBeacon, IBlaubotBroadcastReceiver, IEthernetBeacon, Closeable, IBlaubotAndroidComponent {
    private static final String LOG_TAG = "BlaubotWifiP2PBeacon";


    /**
     * Debug switch.
     * If set to true, the beacon connects to EVERY found wifi direct peer.
     * Note that we can get stuck in a connected group, since we assume that the not group-owning
     * side closes the group as soon as it has completed the state exchange.
     * It also occasionally reboots my TV ;-)
     */
    private static final boolean CONNECT_TO_ALL_AVAILABLE_PEERS = false;

    /**
     * This is the Blaubot service type string used for Bonjour service discovery and advertisement.
     */
    private static final String BONJOUR_SERVICE_TYPE = "_blaubot._tcp";

    /**
     * The string representation of the upnp device urn as per UPnP Device Architecture1.1 format
     * http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf
     */
    private static final String UPNP_DEVICE_URN = "urn:schemas-upnp-org:device:MediaServer:1";
    /**
     * The string representation of the upnp service urn for this beacon as per UPnP Device Architecture1.1 format
     * http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf
     */
    private static final String UPNP_SERVICE_URN = "urn:schemas-upnp-org:service:ContentDirectory:1";

    /**
     * Bonjour TXT record for the unique device id
     */
    private static final String TXT_RECORD_UNIQUE_DEVICE_ID_KEY = "ID";

    /**
     * BroadcastReceiver that recieves all relevant WiFiP2P-Events and dispatches it to
     * appended listeners
     */
    private final BlaubotWifiP2PBroadcastReceiver wifiP2pBroadcastReceiver;

    /**
     * The wifi p2p channel used for beacon interactions
     */
    private final Channel wifiP2pBeaconChannel;

    /**
     * The WiFiP2PManager
     */
    private final WifiP2pManager wifiP2pManager;

    /**
     * The android vibrator service, if available. May be null.
     */
    private Vibrator vibratorService;

    /**
     * The port to be used for beacon connections, once a p2p group is formed.
     * The EthernetBeaconAcceptThread uses this port in conjunction with the
     * IEthernetBeacon interface.
     */
    private final int beaconPort;

    /**
     * A listener that keeps track of the devices currently forming a blaubot network via a
     * ILifeCycleEventListener to be used in the beaconScanner to reduce the device list that
     * needs to be scanned.
     */
    private KingdomCensusLifecycleListener kingdomCensusLifeCycleListener;

    /**
     * The accept thread accepting connections on 0.0.0.0 and the specified beaconPort.
     * Is recreated on each stop/start cycle.
     * Has access to this very member variable to kill himself, if itself does not match
     * this instance.
     */
    private volatile EthernetBeaconAcceptThread ethernetBeaconAcceptThread;

    private IBlaubotListeningStateListener listeningStateListener;
    private IBlaubotIncomingConnectionListener incomingConnectionListener;
    private IBlaubotDiscoveryEventListener discoveryEventListener;
    private volatile boolean isStarted;
    private volatile boolean discoveryActivated;

    /**
     * A list of devices discovered in the past via bonjour events.
     * It is filled by the bonjour listener and later used by the beaconScanner to build
     * a list of devices to connect to in a continuous loop to check for state changes.
     */
    private Set<BlaubotWifiP2PDevice> knownActiveDevices;

    /**
     * A scanner that sequentially loops over the knownActive devices filtered by connected
     * devices to retrieve states by creating a WifiDirect group to the devices and exchanging
     * the states over sockets.
     * The scanner is stopped/started with each startListening or stopListening call.
     */
    private WifiP2PBeaconScanner beaconScanner;

    /**
     * Private listener class that is attached to the wifi p2p manager to receive UPnP discovery
     * events over WifiDirect without any group connection
     */
    private final UPNPListener upnpListener;

    /**
     * Private listener class that is attached to the wifi p2p manager to receive bonjour discovery
     * events over WiFiDirect without any group connection.
     */
    private BonjourListener bonjourListener;

    /**
     * The blaubot instance global beacon store to get and store connection metadata discovered
     * from all the beacons.
     */
    private IBlaubotBeaconStore beaconStore;

    /**
     * The Blaubot top level instance to traverse the blaubot components.
     */
    private Blaubot blaubot;

    /**
     * This beacon's uuid to discriminate between multiple Blaubot instances.
     */
    private UUID beaconUuid;

    /**
     * ExecutorService to process some async started tasks in a single threaded manner.
     */
    private volatile ExecutorService executorService;

    /**
     * This Blaubot instance's own device (and uniqueDeviceId).
     */
    private IBlaubotDevice ownDevice;

    /**
     * The last known state of this Blaubot instance.
     */
    private IBlaubotState currentState;

    /**
     * The string that we exploit to pre-filter upnp devices before we probe them if they are really a
     * blaubot beacon.
     * @param wifiP2pManager android's wifi p2p manager service 
     * @param beaconChannel the wifi p2p channel to be used for this beacon
     * @param beaconPort the tcp port for this beacon to listen on
     */

    public BlaubotWifiP2PBeacon(WifiP2pManager wifiP2pManager, Channel beaconChannel, int beaconPort) {
        this.beaconPort = beaconPort;
        this.knownActiveDevices = Collections.newSetFromMap(new ConcurrentHashMap<BlaubotWifiP2PDevice, Boolean>());
        this.wifiP2pManager = wifiP2pManager;
        this.wifiP2pBeaconChannel = beaconChannel;
        this.wifiP2pBroadcastReceiver = new BlaubotWifiP2PBroadcastReceiver(wifiP2pManager, beaconChannel);
        this.wifiP2pBroadcastReceiver.addEventListener(wifiDirectEventListener);
        this.bonjourListener = new BonjourListener();
        this.upnpListener = new UPNPListener();
        this.wifiP2pManager.setDnsSdResponseListeners(beaconChannel, bonjourListener, bonjourListener);
        this.wifiP2pManager.setUpnpServiceResponseListener(wifiP2pBeaconChannel, upnpListener);
    }


    @Override
    public Thread getAcceptThread() {
        return ethernetBeaconAcceptThread;
    }

    @Override
    public int getBeaconPort() {
        return beaconPort;
    }

    @Override
    public void close() throws IOException {
        if (wifiP2pManager != null && wifiP2pBeaconChannel != null) {
            wifiP2pManager.cancelConnect(wifiP2pBeaconChannel, null);
            wifiP2pManager.clearServiceRequests(wifiP2pBeaconChannel, null);
            wifiP2pManager.clearLocalServices(wifiP2pBeaconChannel, null);
            wifiP2pManager.stopPeerDiscovery(wifiP2pBeaconChannel, null);
            wifiP2pManager.removeGroup(wifiP2pBeaconChannel, null);
        }
    }

    @Override
    public void setCurrentContext(Context context) {
        this.vibratorService = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void onResume(Activity context) {

    }

    @Override
    public void onPause(Activity context) {

    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    /**
     * Listener for upnp discovery events over WifiDirect
     */
    private class UPNPListener implements WifiP2pManager.UpnpServiceResponseListener {
        @Override
        public void onUpnpServiceAvailable(List<String> uniqueServiceNames, WifiP2pDevice srcDevice) {
            Log.d(LOG_TAG, "onUpnpServiceAvailable(" + uniqueServiceNames + ", " + srcDevice.deviceName + ")");

            // check if one of the received record matches the beacon uuid
            final String beaconUUidStr = beaconUuid.toString();
            final String beaconUuidWithUPnPPrefix = "uuid:" + beaconUUidStr; // the beacon uuid
            boolean deviceHasValidBeacon = false;
            for (String uniqueServiceName : uniqueServiceNames) {
                if (uniqueServiceName.startsWith(beaconUuidWithUPnPPrefix)) {
                    deviceHasValidBeacon = true;
                    break;
                }
            }
            if (!deviceHasValidBeacon) {
                if (Log.logWarningMessages()) {
                    Log.w(LOG_TAG, "Received an UPnP record but was not our beacon uuid (" + beaconUUidStr + ") -> ignored.");
                }
                // abort, not relevant
                return;
            }

            // TODO: NTH extract the uniqueDeviceId? How?

            // we have found a device with a running blaubot instance, we store the device without the unique device id
            // and try to make a state exchange by connecting to the device
            final BlaubotWifiP2PDevice blaubotWifiP2PDevice = new UnidentifiedWifiP2pBlaubotDevice(srcDevice);
            knownActiveDevices.add(blaubotWifiP2PDevice);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Added " + blaubotWifiP2PDevice + ", which has a valid BlaubotWifiP2PBeacon running, to the list of known devices.");
            }

        }
    }

    /**
     * Listener for Bonjour discovery events over WifiDirect
     */
    private class BonjourListener implements DnsSdServiceResponseListener, DnsSdTxtRecordListener {

        @Override
        public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, final WifiP2pDevice srcDevice) {
            Log.d(LOG_TAG, "onDnsSdTxtRecordAvailable(" + fullDomainName + ", " + txtRecordMap + ", " + srcDevice.deviceName + ")");

            // check if the received record matches the beacon uuid
            final String beaconUUidStr = beaconUuid.toString();
            if (!fullDomainName.startsWith(beaconUUidStr)) {
                if (Log.logWarningMessages()) {
                    Log.w(LOG_TAG, "Received a DnsSdTxt record but was not our beacon uuid (" + beaconUUidStr + ") -> ignored: " + fullDomainName);
                }
                // abort, if irrelevant
                return;
            }

            // extract the unique id
            final String uniqueDeviceId = txtRecordMap.get(TXT_RECORD_UNIQUE_DEVICE_ID_KEY);
            if (uniqueDeviceId == null) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Could not extract the uniqueDeviceId from the bonjour TXT record");
                }
                return;
            }

            // we have found a device with a running blaubot instance, we store the uniqueId to WifiP2pDevice
            // mapping and try to make a state exchange by connecting to the device
            final BlaubotWifiP2PDevice blaubotWifiP2PDevice = new BlaubotWifiP2PDevice(uniqueDeviceId, srcDevice);
            knownActiveDevices.add(blaubotWifiP2PDevice);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Added " + blaubotWifiP2PDevice + ", which has a valid BlaubotWifiP2PBeacon running, to the list of known devices.");
            }
        }

        @Override
        public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {
            Log.d(LOG_TAG, "onDnsSdServiceAvailable(" + instanceName + ", " + registrationType + ", " + srcDevice + ")");
        }

    }

    private class UnidentifiedWifiP2pBlaubotDevice extends BlaubotWifiP2PDevice implements IUnidentifiedBlaubotDevice {
        public UnidentifiedWifiP2pBlaubotDevice(WifiP2pDevice device) {
            super("UnidentifiedWifiP2pBlaubotDevice", device);
        }

        @Override
        public void setUniqueDeviceId(String uniqueDeviceId) {
            this.uniqueDeviceId = uniqueDeviceId;
        }
    }

    private class UnidentifiedBlaubotDevice extends BlaubotDevice implements IUnidentifiedBlaubotDevice {
        public UnidentifiedBlaubotDevice() {
            super("UnidentifiedBlaubotDeviceFrom" + BlaubotWifiP2PBeacon.this);
        }

        @Override
        public void setUniqueDeviceId(String uniqueDeviceId) {
            this.uniqueDeviceId = uniqueDeviceId;
        }
    }

    /**
     * Only for logging
     */
    private IBlaubotWifiDirectEventListener wifiDirectEventListener = new IBlaubotWifiDirectEventListener() {
        @Override
        public void onP2PWifiEnabled() {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG + ".WifiDirectEventListener", "onP2PWifiEnabled()");
            }
        }

        @Override
        public void onP2PWifiDisabled() {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG + ".WifiDirectEventListener", "onP2PWifiDisabled()");
            }
        }

        @Override
        public void onListOfPeersChanged(WifiP2pDeviceList deviceList) {
            if (Log.logDebugMessages()) {
                List<String> deviceNames = new ArrayList<>();
                for (WifiP2pDevice device : deviceList.getDeviceList()) {
                    deviceNames.add(device.deviceName);
                }
                Log.d(LOG_TAG + ".WifiDirectEventListener", "onListOfPeersChanged(" + deviceNames + ")");
            }
            availablePeers = new ArrayList<>(deviceList.getDeviceList());
        }


        @Override
        public void onDiscoveryStopped() {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG + ".WifiDirectEventListener", "onDiscoveryStopped()");
            }
            peerDiscoveryActive.set(false);
        }

        @Override
        public void onDiscoveryStarted() {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG + ".WifiDirectEventListener", "onDiscoveryStarted()");
            }
            peerDiscoveryActive.set(true);
        }

        @Override
        public void onConnectivityChanged(final WifiP2pInfo p2pInfo, final NetworkInfo networkInfo, final WifiP2pGroup group) {
            if (Log.logDebugMessages()) {
//                Log.d(LOG_TAG + ".WifiDirectEventListener", "onConnectivityChanged(" + p2pInfo + ", " + networkInfo + ", " + group + ")");
            }
        }
    };

    @Override
    public IBlaubotAdapter getAdapter() {
        return null;
    }

    @Override
    public synchronized void startListening() {
        if (isStarted) {
            stopListening();
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Creating executor ...");
        }
        executorService = Executors.newSingleThreadExecutor();

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Starting EthernetBeaconAcceptThread ...");
        }
        ethernetBeaconAcceptThread = new EthernetBeaconAcceptThread(incomingConnectionListener, this);
        ethernetBeaconAcceptThread.start();

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Starting WifiP2PBeaconScanner ...");
        }
        beaconScanner = new WifiP2PBeaconScanner();
        beaconScanner.start();

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Setting up search for beacon services ...");
        }

        // start advertising
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                advertise();
            }
        });

        // start discovery of other devices that are advertising
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                createServiceRequestsAndStartDiscovery();
            }
        });


        isStarted = true;
        if (listeningStateListener != null) {
            listeningStateListener.onListeningStarted(this);
        }
    }

    @Override
    public synchronized void stopListening() {
        if (!isStarted()) {
            return;
        }
        androidHardwareWorkaround();
        disconnectPeer();
        androidHardwareWorkaround();
        wifiP2pManager.cancelConnect(wifiP2pBeaconChannel, null);
        androidHardwareWorkaround();
        wifiP2pManager.stopPeerDiscovery(wifiP2pBeaconChannel, null);
        androidHardwareWorkaround();
        wifiP2pManager.clearLocalServices(wifiP2pBeaconChannel, null);
        androidHardwareWorkaround();
        wifiP2pManager.clearServiceRequests(wifiP2pBeaconChannel, null);

        // TODO: stop discovery and listening
        if (beaconScanner != null && beaconScanner.isAlive()) {
            Log.d(LOG_TAG, "Stopping WifiP2PBeaconScanner ...");
            beaconScanner.interrupt();
            try {
                beaconScanner.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (executorService != null) {
            Log.d(LOG_TAG, "Shutting down executor");
            executorService.shutdown();
            try {
                while (!executorService.awaitTermination(100, TimeUnit.MILLISECONDS)) ;
            } catch (InterruptedException e) {
            } finally {
                executorService = null;
            }
        }

        wifiP2pManager.clearLocalServices(wifiP2pBeaconChannel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(LOG_TAG, "P2P -> Cleared local services");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(LOG_TAG, "P2P -> Failed to clear local services. Reason: " + actionListenerFailureReasonToString(reason));
            }
        });

        if (ethernetBeaconAcceptThread != null && ethernetBeaconAcceptThread.isAlive()) {
            try {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Waiting for beacon accept thread to finish ...");
                }
                ethernetBeaconAcceptThread.interrupt();
                ethernetBeaconAcceptThread.join();
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Beacon accept thread to finished ...");
                }
            } catch (InterruptedException e) {
                if (Log.logWarningMessages()) {
                    Log.w(LOG_TAG, e);
                }
            }
            ethernetBeaconAcceptThread = null;
        }

        isStarted = false;
        if (listeningStateListener != null) {
            listeningStateListener.onListeningStopped(this);
        }
    }

    @Override
    public boolean isStarted() {
        return this.isStarted;
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
        // TODO: maybe beacons should not derive from acceptors anymore
        return null;
    }

    @Override
    public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
        this.discoveryEventListener = discoveryEventListener;
    }

    /**
     * Creates a search request for the blaubot service to get notified via broadcast receivers
     * and starts a discovery
     */
    private void createServiceRequestsAndStartDiscovery() {
        // bonjour
        androidHardwareWorkaround();
        final CountDownLatch addBonjourServiceRequestLatch = new CountDownLatch(1);
        final WifiP2pDnsSdServiceRequest bonjourSearchRequest = WifiP2pDnsSdServiceRequest.newInstance(beaconUuid.toString(), BONJOUR_SERVICE_TYPE);
        wifiP2pManager.addServiceRequest(wifiP2pBeaconChannel, bonjourSearchRequest, new ActionListener() {

            @Override
            public void onSuccess() {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Bonjour service search request added: " + bonjourSearchRequest);
                }
                addBonjourServiceRequestLatch.countDown();
            }

            @Override
            public void onFailure(int reason) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Failed to add bonjour service search request. Reason: " + actionListenerFailureReasonToString(reason));
                }
                addBonjourServiceRequestLatch.countDown();
            }
        });

        try {
            addBonjourServiceRequestLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // UPnP
        androidHardwareWorkaround();
        final CountDownLatch addUPNPServiceRequestLatch = new CountDownLatch(1);
        final WifiP2pUpnpServiceRequest upnpServiceSearchRequest = WifiP2pUpnpServiceRequest.newInstance("ssdp:all");
        wifiP2pManager.addServiceRequest(wifiP2pBeaconChannel, upnpServiceSearchRequest, new ActionListener() {

            @Override
            public void onSuccess() {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "UPnP service search request added: " + upnpServiceSearchRequest);
                }
                addUPNPServiceRequestLatch.countDown();
            }

            @Override
            public void onFailure(int reason) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Failed to add bonjour service search request. Reason: " + actionListenerFailureReasonToString(reason));
                }
                addBonjourServiceRequestLatch.countDown();
            }
        });

        try {
            addBonjourServiceRequestLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // after adding, start discovery
        androidHardwareWorkaround();
        final CountDownLatch startServiceDiscoveryLatch = new CountDownLatch(1);
        wifiP2pManager.discoverServices(wifiP2pBeaconChannel, new ActionListener() {
            @Override
            public void onSuccess() {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Service discovery (UPnP & Bonjour) started");
                }
                startServiceDiscoveryLatch.countDown();
            }

            @Override
            public void onFailure(int reason) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Failed to start service discovery (UPnP & Bonjour), reason: " + actionListenerFailureReasonToString(reason));
                }
                startServiceDiscoveryLatch.countDown();
            }
        });

        try {
            startServiceDiscoveryLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * android hardware .... Many devices will not be ready in the onSuccess without time to do something (!!!)
     * So we sleep a defined amount of time
     */
    private void androidHardwareWorkaround() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts advertising our beacon over wifi direct service discovery
     */
    private void advertise() {
        // Add the bonjour local service
        /**
         * Attention:
         *    readTxtData(...) -> https://android.googlesource.com/platform/frameworks/base/+/cd92588/wifi/java/android/net/wifi/p2p/nsd/WifiP2pDnsSdServiceResponse.java
         *    as well as: Page 10 http://files.dns-sd.org/draft-cheshire-dnsext-dns-sd.txt
         *    => txt keys can only have 2 bytes; values 255 bytes!
         *    => The total size of a typical DNS-SD TXT record is intended to be small -- 200 bytes or less.
         *
         */
        Map<String, String> txtRecordsMap = new HashMap<>();
        txtRecordsMap.put(TXT_RECORD_UNIQUE_DEVICE_ID_KEY, ownDevice.getUniqueDeviceID());
        final WifiP2pServiceInfo bonjourServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(beaconUuid.toString(), BONJOUR_SERVICE_TYPE, txtRecordsMap);
        final CountDownLatch addLocalBonjourServiceLatch = new CountDownLatch(1);
        final AtomicBoolean result = new AtomicBoolean(false);
        androidHardwareWorkaround();
        wifiP2pManager.addLocalService(wifiP2pBeaconChannel, bonjourServiceInfo, new ActionListener() {

            @Override
            public void onSuccess() {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Added Bojour-local service: " + bonjourServiceInfo);
                }
                result.set(true);
                addLocalBonjourServiceLatch.countDown();

                // we need to constantly discover peers to be visible (meh), see: https://code.google.com/p/android/issues/detail?id=37425
                // so manager.discoverPeers() has to be called somewhere -> BeaconScanner does that
            }

            @Override
            public void onFailure(int reason) {
                if (Log.logDebugMessages()) {
                    Log.e(LOG_TAG, "Adding Bonjour-LocalService failed: " + actionListenerFailureReasonToString(reason));
                }
                addLocalBonjourServiceLatch.countDown();
            }

        });
        try {
            addLocalBonjourServiceLatch.await();
        } catch (InterruptedException e) {
        }

        // now add the upnp local service
        final CountDownLatch addLocalUpnpServiceLatch = new CountDownLatch(1);
        final WifiP2pServiceInfo upnpServiceInfo = WifiP2pUpnpServiceInfo.newInstance(beaconUuid.toString(), UPNP_DEVICE_URN, Arrays.asList(UPNP_SERVICE_URN));
        wifiP2pManager.addLocalService(wifiP2pBeaconChannel, upnpServiceInfo, new ActionListener() {
            @Override
            public void onSuccess() {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Added UPnP-local service: " + upnpServiceInfo);
                }
                result.set(true);
                addLocalUpnpServiceLatch.countDown();

                // we need to constantly discover peers to be visible (meh), see: https://code.google.com/p/android/issues/detail?id=37425
                // so manager.discoverPeers() has to be called somewhere -> BeaconScanner does that
            }

            @Override
            public void onFailure(int reason) {
                if (Log.logDebugMessages()) {
                    Log.e(LOG_TAG, "Adding UPnP-LocalService failed: " + actionListenerFailureReasonToString(reason));
                }
                addLocalUpnpServiceLatch.countDown();
            }
        });

        try {
            addLocalUpnpServiceLatch.await();
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void onConnectionStateMachineStateChanged(IBlaubotState state) {
        this.currentState = state;
    }

    @Override
    public void setDiscoveryActivated(boolean active) {
        this.discoveryActivated = active;
    }


    /**
     * Semaphore preventing multiple beacon scanners to run concurrently.
     */
    private final Semaphore beaconScannerSemaphore = new Semaphore(1);

    /**
     * Set by the broadcast receiver listeners to check, if the discovery is currently active
     */
    private AtomicBoolean peerDiscoveryActive = new AtomicBoolean(false);

    /**
     * the last received list of peers
     */
    private volatile ArrayList<WifiP2pDevice> availablePeers = new ArrayList<>();

    /**
     * TODO: implement properly
     * The whole Bonjour and UPNP disvoery mechanisms just explore some possibly running Blaubot instances.
     * This scanner runs through a list of discovered devices, filters out the already connected devices
     * and creates WiFiP2p connections followed by socket connections to these devices sequentially to
     * do the state exchange using the generalized tasks.
     */
    class WifiP2PBeaconScanner extends Thread {
        private String LOG_TAG = "BlaubotWifiP2PBeacon.WifiP2PBeaconScanner";
        /**
         * Sleep time (ms) between probes to other devices
         */
        public static final long PER_DEVICE_TIMEOUT = 5000;


        /**
         * Creates the list of devices to be scanned.
         *
         * @return list of devices to scan without connected kingdom devices and ordered descending by uniqueDeviceId
         */
        private ArrayList<BlaubotWifiP2PDevice> getDevicesToScan() {
            ArrayList<BlaubotWifiP2PDevice> devicesToScan = new ArrayList<BlaubotWifiP2PDevice>();

            if(CONNECT_TO_ALL_AVAILABLE_PEERS) {
                // all known peers will be scanned - there are quite some dangers here, so warn the user
                Log.w(LOG_TAG, "CONNECT_TO_ALL_AVAILABLE_PEERS is activated! Strange things can happen, I hope you know what you're doing.");
                for(WifiP2pDevice d : availablePeers) {
                    UnidentifiedWifiP2pBlaubotDevice blaubotDevice = new UnidentifiedWifiP2pBlaubotDevice(d);
                    blaubotDevice.setUniqueDeviceId("UnidentifiedWifiP2pBlaubotDevice for P2pDevice " + d.deviceName);
                    devicesToScan.add(blaubotDevice);
                }
            }

            // We only check devices that are bonded and not already connected to our network to minimize
            // the expensive lookups and connectivity traffic.
            Set<String> blaubotNetworkDevices = kingdomCensusLifeCycleListener.getConnectedUniqueIds();

            for (BlaubotWifiP2PDevice d : knownActiveDevices) {
                // filter connected devices
                if (!blaubotNetworkDevices.contains(d.getUniqueDeviceID())) {
                    devicesToScan.add(d);
                }
            }
            // After filtering we sort the devices descending by their unique id
            Collections.sort(devicesToScan);
            Collections.reverse(devicesToScan);
            return devicesToScan;
        }


        /**
         * This latch awaits a connect of the wifi direct p2p connection to another device.
         * Our happy path lifecycle is as follows:
         * - BeaconScanner starts a WifiDirect P2P group connect to the other device
         * - The connect method adds the device and it's uniqueDeviceId to the connectingDevices map, so that we know in the broadcast receiver listener, which events are interesting for us
         * - From the connect method, we know if the connect attempt was started or failed before that
         * - If started, this latch is counted down on receive of an event on the wifiDirectListener of the scanner
         * - If not, the latch is immediately counted down based on the negative result of connect()
         * - The beacon scanner then awaits this latch, which will be counted down on a disconnect event received for this device on the wifiDirectListener
         */
        private volatile CountDownLatch groupChangeLatch = new CountDownLatch(0); // initially set to avoid possible nullpointers on startup


        private volatile boolean interrupted = false;
        @Override
        public void interrupt() {
            super.interrupt();
            interrupted = true;
        }

        @Override
        public void run() {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "BeaconScanner started.");
            }
            try {
                beaconScannerSemaphore.acquire();
            } catch (InterruptedException e) {
                return;
            }

            // add listener
            wifiP2pBroadcastReceiver.addEventListener(wifiDirectListener);

            // start service discovery to get informend on new devices and start peer discovery to be visible
            wifiP2pManager.discoverPeers(wifiP2pBeaconChannel, null);

            while (!isInterrupted() && beaconScanner == Thread.currentThread() && !interrupted) {
                final ArrayList<BlaubotWifiP2PDevice> devicesToScan = getDevicesToScan();
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Scanning devices: " + devicesToScan);
                }

                // we need have peer discovery running all the time -> http://stackoverflow.com/a/23850036/1142790
                if (!peerDiscoveryActive.get()) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "PeerDiscovery is not active, activating.");
                    }
                    final CountDownLatch latch = new CountDownLatch(1);
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            androidHardwareWorkaround();
                            wifiP2pManager.discoverPeers(wifiP2pBeaconChannel, new ActionListener() {
                                @Override
                                public void onSuccess() {
                                    if (Log.logDebugMessages()) {
                                        Log.d(LOG_TAG, "(re)started peer discovery");
                                    }
                                    latch.countDown();
                                }

                                @Override
                                public void onFailure(int reason) {
                                    if (Log.logErrorMessages()) {
                                        Log.e(LOG_TAG, "Failed to (re)start peer discovery, reason: " + actionListenerFailureReasonToString(reason));
                                    }
                                    latch.countDown();
                                }
                            });
                        }
                    });
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


                if ((devicesToScan.isEmpty() || availablePeers.isEmpty() || !discoveryActivated)) {
                    // Do not connect to peers, if there are no peers available, no service records were found or the connection is explicitly deactivated.
                    // also, if we have devices in deviceToScan (which are discovered over bonjour, but no availablePeers, the connection will fail:
                    Log.w(LOG_TAG, "Not connecting to peers. BeaconDiscoveryActivated: " + discoveryActivated + ", Available peers: " + availablePeers.size() + ", PeersFoundByBonjourOrUPnP: " + knownActiveDevices.size() + ", BonjourPeersAfterFilter: " + devicesToScan + ", PeerDiscoveryActive: " + peerDiscoveryActive.get());
                    if (!discoveryActivated) {
                        wifiP2pManager.discoverServices(wifiP2pBeaconChannel, new ActionListener() {
                            @Override
                            public void onSuccess() {
                                if (Log.logDebugMessages()) {
                                    Log.d(LOG_TAG, "(Re)initiated service discovery");
                                }
                            }

                            @Override
                            public void onFailure(int reason) {
                                if (Log.logErrorMessages()) {
                                    Log.e(LOG_TAG, "Could not (re)initiate service discovery, reason: " + actionListenerFailureReasonToString(reason));
                                }
                            }
                        });
                    }
                } else {
                    Deque<BlaubotWifiP2PDevice> scanQueue = new ArrayDeque<>(getDevicesToScan());
                    while (!scanQueue.isEmpty()) {
                        final BlaubotWifiP2PDevice device = scanQueue.poll();
                        // 1. create wifi p2p connection
                        // 2. create tcp connection
                        // 3. do the state exchange

                        final String uniqueDeviceID = device.getUniqueDeviceID();
                        final WifiP2pDevice wifiP2pDevice = device.getWifiP2pDevice();

                        // ensure that there is no group formed at the time of connect
                        final AtomicReference<WifiP2pGroup> groupInfo = new AtomicReference<>();
                        int i = 0;
                        do {
                            if(i++>0) {
                                if(Log.logDebugMessages()) {
                                    Log.d(LOG_TAG, "Still have a group (" + groupInfo.get().getNetworkName() + "). Will wait until our channel is free again.");
                                }
                            }

                            final CountDownLatch latch = new CountDownLatch(1);
                            androidHardwareWorkaround();
                            wifiP2pManager.requestGroupInfo(wifiP2pBeaconChannel, new WifiP2pManager.GroupInfoListener() {
                                @Override
                                public void onGroupInfoAvailable(WifiP2pGroup group) {
                                    groupInfo.set(group);
                                    latch.countDown();
                                }
                            });

                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } while (!(groupInfo.get() == null));


                        if (groupInfo.get() != null) {
                            Log.w(LOG_TAG, "group status: " + groupInfo.get());
                            continue;
                        }


                        // connect wifi direct
                        androidHardwareWorkaround();
                        ConnectInitiationResult connectResult = connectToPeer(uniqueDeviceID, wifiP2pDevice);
                        if (connectResult != ConnectInitiationResult.INITIATED) {
                            if (Log.logWarningMessages()) {
                                Log.w(LOG_TAG, "WifiP2pConnection to device " + device + " failed.");
                            }
                        } else if(connectResult == ConnectInitiationResult.BUSY) {
                            if (Log.logWarningMessages()) {
                                Log.w(LOG_TAG, "The wifi direct adapter was too busy to connect. Going to sleep for some time and retrying then.");
                                try {
                                    // put the device back to the queue's head
                                    scanQueue.addFirst(device);
                                    // but sleep a little longer than usual
                                    Thread.sleep(PER_DEVICE_TIMEOUT*2);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            if (Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "Awaiting connectivity callback from android's wifi p2p broadcast receiver");
                            }
                        }

                        // tcp connection and state exchagne will happen in onConnectivityChanged of this.wifiP2pConnectionListener

                        try {
                            Thread.sleep(PER_DEVICE_TIMEOUT);
                        } catch (InterruptedException e) {
                            interrupted = true;
                            break;
                        }
                    }
                }


                try {
                    Thread.sleep(PER_DEVICE_TIMEOUT);
                } catch (InterruptedException e) {
                    interrupted = true;
                    break;
                }
            }

            // remove listener
            wifiP2pBroadcastReceiver.removeEventListener(wifiDirectListener);

            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "BeaconScanner finished.");
            }
            beaconScannerSemaphore.release();
        }

        /**
         * A map containing the devices we are connecting to at the moment and their uniqueDeviceIds
         */
        private Map<WifiP2pDevice, String> connectingDevices = new ConcurrentHashMap();


        /**
         * Is added on scanner start and removed on scanner end.
         * Listens to connected groups.
         * If the client list of a formed group contains one of the devices in this.connectingDevices,
         * the listener tries to open a socket connection for a state exchange with the other beacon's
         * accept thread.
         */
        private IBlaubotWifiDirectEventListener wifiDirectListener = new BlaubotWifiDirectEventListenerAdapter() {

            @Override
            public void onConnectivityChanged(final WifiP2pInfo p2pInfo, NetworkInfo networkInfo, WifiP2pGroup group) {
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "onConnectivityChanged: " + p2pInfo + "; " + networkInfo + "; " + group);
                }
                if (!networkInfo.isConnectedOrConnecting() && networkInfo.getState() == NetworkInfo.State.DISCONNECTED) {
                    Log.w(LOG_TAG, "State is not connceted or connecting. ignoring onConnectivityChanged event");
                }

                if (p2pInfo.groupFormed) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "The group was formed");
                    }
                    if(vibratorService != null) {
                        // notify with haptic feedback
                        vibratorService.vibrate(350);
                    }
                    if (!p2pInfo.isGroupOwner) {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Other side is the owner, so we have to connect to " + p2pInfo.groupOwnerAddress + ":" + beaconPort);
                        }
                        boolean ownerIsInConnectingDevices = connectingDevices.keySet().contains(group.getOwner());
                        if (ownerIsInConnectingDevices) {
                            if (Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "The owner is in the connecting devices list");
                            }
                        }

                        final NetworkInfo.State networkState = networkInfo.getState();
                        if (networkState != NetworkInfo.State.CONNECTED) {
                            throw new IllegalStateException();
                        }

                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                if (Log.logDebugMessages()) {
                                    Log.d(LOG_TAG, "Initiating beacon state exchange via ethernet socket ...");
                                }
                                androidHardwareWorkaround();
                                IUnidentifiedBlaubotDevice device = new UnidentifiedBlaubotDevice();
                                // receive the group owners ip and connect to the beacon, then disconnect
                                final InetAddress groupOwnerAddress = p2pInfo.groupOwnerAddress;
                                // try to connect, then exchange states via tcp/ip
                                Socket clientSocket = null;
                                try {
                                    clientSocket = new Socket(groupOwnerAddress, beaconPort);
                                    if (Log.logDebugMessages()) {
                                        Log.d(LOG_TAG, "Connecting to " + clientSocket + " for Beacon messaging");
                                    }
                                    BlaubotEthernetUtils.sendOwnUniqueIdThroughSocket(ownDevice, clientSocket);
                                    BlaubotEthernetConnection connection = new BlaubotEthernetConnection(device, clientSocket);
                                    final List<ConnectionMetaDataDTO> ownAcceptorsMetaDataList = BlaubotAdapterHelper.getConnectionMetaDataList(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
                                    ExchangeStatesTask exchangeStatesTask = new ExchangeStatesTask(ownDevice, connection, currentState, ownAcceptorsMetaDataList, beaconStore, discoveryEventListener);
                                    exchangeStatesTask.run();
                                } catch (IOException e) {
                                    if (Log.logWarningMessages()) {
                                        Log.w(LOG_TAG, "Connection to " + device + "'s beacon failed: " + e.getMessage());
                                    }
                                } finally {
                                    // close socket and connection
                                    if (clientSocket != null) {
                                        try {
                                            clientSocket.shutdownInput();
                                            clientSocket.shutdownOutput();
                                        } catch (IOException e) {
                                            // don't care
                                        }
                                        try {
                                            clientSocket.close();
                                        } catch (IOException e) {
                                            // couldn't care less
                                        }
                                    }

                                    // finally wait some time for the android hardware, then disconnect
                                    executorService.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Thread.sleep(1000);
                                                disconnectPeer();
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });


                                }
                            }
                        });

                    } else {
                        final Collection<WifiP2pDevice> clientList;
                        if (group != null) {
                            clientList = group.getClientList();
                        } else {
                            if (Log.logErrorMessages()) {
                                Log.e(LOG_TAG, "Inconsitent state from android event! groupFormed is true but no group was given (null)");
                            }
                            clientList = new ArrayList<>();
                        }
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "We are group owner. The other side has to connect. Awaiting message or timeout ... Group client list: " + clientList);
                        }
                        Set<WifiP2pDevice> clientSet = new HashSet<>(clientList);
                        clientSet.retainAll(connectingDevices.keySet());
                        Log.w(LOG_TAG, "Devices that are connected and interesting for us: " + clientSet);

                        // -- we are group owner and therefore have to wait for the other side to connect
                        // since this is a bi-directional conversation, we don't actually need to contact
                        // the other side and can live with the information they will provide for us so
                        // we don't bother to connect to the non-group owner.
                        // the other side will close the connection after a state exchange

                        // TODO: theoretically we have to disconnect the remote connection in cases it is harmful by not creating a socket to communicate and closing the group. But that is out of scope for now
                    }
                } else {
                    // group could not be formed
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "Group was not formed");
                    }
                }
            }
        };


        /**
         * Initiates a connection to the given srcDevice via WifiDirect
         *
         * @param uniqueDeviceId the uniqueDeviceId found via bonjour
         * @param srcDevice      the device info
         * @return INITIATED, if successful
         */
        private ConnectInitiationResult connectToPeer(String uniqueDeviceId, final WifiP2pDevice srcDevice) {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean result = new AtomicBoolean(false);
            final AtomicInteger errorResult = new AtomicInteger(0);
            connectingDevices.put(srcDevice, uniqueDeviceId);
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = srcDevice.deviceAddress;
            if (Log.logDebugMessages())
                Log.d(LOG_TAG, "connecting to " + srcDevice.deviceName + " ...");
            wifiP2pManager.connect(wifiP2pBeaconChannel, config, new ActionListener() {

                @Override
                public void onSuccess() {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "connect.onSuccess() - we initiated the connect process to " + srcDevice.deviceName);
                    }
                    result.set(true);
                    latch.countDown();
                }

                @Override
                public void onFailure(int reason) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "connect.onFailure(" + reason + ") Failed to connect to " + srcDevice.deviceName + ". Reason: " + actionListenerFailureReasonToString(reason));
                    }
                    errorResult.set(reason);
                    result.set(false);
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (result.get()) {
                return ConnectInitiationResult.INITIATED;
            } else {
                final int errorCode = errorResult.get();
                if (errorCode == WifiP2pManager.ERROR)
                    return ConnectInitiationResult.ERROR;
                else if (errorCode == WifiP2pManager.BUSY)
                    return ConnectInitiationResult.BUSY;
                else if (errorCode == WifiP2pManager.NO_SERVICE_REQUESTS)
                    return ConnectInitiationResult.NO_SERVICE_REQUESTS;
                else if (errorCode == WifiP2pManager.P2P_UNSUPPORTED)
                    return ConnectInitiationResult.P2P_UNSUPPORTED;
                else
                    return ConnectInitiationResult.UNKNOWN;
            }
        }

    }

    /**
     * Corresponds to the return codes of connectToPeer
     */
    private enum ConnectInitiationResult {
        /**
         * If the connection was successfully initiated
         */
        INITIATED,
        ERROR,
        BUSY,
        P2P_UNSUPPORTED,
        NO_SERVICE_REQUESTS,
        UNKNOWN;
    }

    /**
     * Disconnects the current peer connection which is uniquely bound to the (single) channel used
     * by the beacon.
     *
     * @return true iff removing the peer succeeded
     */
    private boolean disconnectPeer() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG + ".disconnectPeer()", "Disconnecting from peer ...");
        }
        final AtomicBoolean out = new AtomicBoolean(false);
        if (wifiP2pManager != null && wifiP2pBeaconChannel != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG + ".disconnectPeer()", "Requesting group info on channel " + wifiP2pBeaconChannel);
            }
            wifiP2pManager.requestGroupInfo(wifiP2pBeaconChannel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG + ".disconnectPeer()", "Got group info to disconnect from peer: " + group);
                    }
                    if (group != null) {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG + ".disconnectPeer()", "Removing group to disconnect from peer. Group: " + group);
                        }
                        wifiP2pManager.removeGroup(wifiP2pBeaconChannel, new ActionListener() {
                            @Override
                            public void onSuccess() {
                                if (Log.logDebugMessages()) {
                                    Log.d(LOG_TAG + ".disconnectPeer()", "removeGroup onSuccess");
                                }
                                out.set(true);
                                latch.countDown();
                            }

                            @Override
                            public void onFailure(int reason) {
                                if (Log.logErrorMessages()) {
                                    Log.e(LOG_TAG + ".disconnectPeer()", "removeGroup onFailure -" + reason);
                                }
                                out.set(false);
                                latch.countDown();
                            }
                        });
                    } else {
                        if (Log.logWarningMessages()) {
                            Log.w(LOG_TAG + ".disconnectPeer()", "Got no group info while trying to disconnect (group was null, should not be a problem)");
                        }
                        out.set(false);
                        latch.countDown();
                    }
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG + ".disconnectPeer()", "Could not disconnect from peer: Either wifiP2pManager or channel was null");
            }
        }

        return out.get();
    }

    @Override
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
        this.beaconUuid = blaubot.getUuidSet().getBeaconUUID();
        this.ownDevice = blaubot.getOwnDevice();
        this.kingdomCensusLifeCycleListener = new KingdomCensusLifecycleListener(ownDevice);
        this.blaubot.addLifecycleListener(kingdomCensusLifeCycleListener);
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }


    /**
     * The {@link android.net.wifi.p2p.WifiP2pManager.ActionListener}s return error codes for their
     * fail callbacks. This method maps these to human readable strings for logging purposes.
     *
     * @param reason the reason code
     * @return the human readable string
     */
    private static String actionListenerFailureReasonToString(int reason) {
        if (reason == WifiP2pManager.ERROR) {
            return "ERROR";
        }
        if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
            return "P2P_UNSUPPORTED";
        }
        if (reason == WifiP2pManager.BUSY) {
            return "BUSY";
        }
        if (reason == WifiP2pManager.NO_SERVICE_REQUESTS) {
            return "NO_SERVICE_REQUESTS";
        }
        return "UNKNOWN (" + reason + ")";
    }

    @Override
    public BroadcastReceiver getReceiver() {
        return wifiP2pBroadcastReceiver;
    }

    @Override
    public IntentFilter getIntentFilter() {
        return BlaubotWifiP2PBroadcastReceiver.createWifiP2PIntentFilter();
    }

}
