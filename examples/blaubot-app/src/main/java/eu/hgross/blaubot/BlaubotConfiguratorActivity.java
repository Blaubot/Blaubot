package eu.hgross.blaubot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.bluetooth.BlaubotBluetoothAdapter;
import eu.hgross.blaubot.android.bluetooth.BlaubotBluetoothBeacon;
import eu.hgross.blaubot.android.nfc.BlaubotNFCBeacon;
import eu.hgross.blaubot.android.views.DebugView;
import eu.hgross.blaubot.android.views.ExpandableHeightListView;
import eu.hgross.blaubot.android.wifi.BlaubotWifiAdapter;
import eu.hgross.blaubot.android.wifip2p.BlaubotWifiP2PBeacon;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.ethernet.BlaubotBonjourBeacon;
import eu.hgross.blaubot.ethernet.BlaubotEthernetAdapter;
import eu.hgross.blaubot.ethernet.BlaubotEthernetMulticastBeacon;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.websocket.BlaubotWebsocketAdapter;

/**
 * Blaubot configurator to dynamically create a blaubot instance with different beacons and adapters
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class BlaubotConfiguratorActivity extends Activity {
    private static final String LOG_TAG = "BlaubotConfiguratorActivity";

    /**
     * The app's uuid.
     * It has to be unique for your specific application. Blaubot will separate different
     * UUIDs to ensure that multiple apps don't interfere.
     */
    private static final UUID APP_UUID = DemoConstants.APP_UUID_CONFIGURATOR;
    private static final int DEFAULT_ACCEPTOR_PORT = 17171;
    private static final int DEFAULT_WEBSOCKET_ACCEPTOR_PORT = 8080;
    private static final int DEFAULT_BONJOUR_BEACON_PORT = 17172;
    private static final int DEFAULT_WIFI_P2P_BEACON_PORT = 17174;
    private static final int DEFAULT_MULTICAST_BEACON_PORT = 17173;
    private static final int DEFAULT_MULTICAST_BEACON_BROADCAST_PORT = 17175;

    /**
     * Max waiting time for the Blaubot instance to stop (seconds).
     */
    private static final long STOP_TIMEOUT = 5;

    /**
     * The tab tag for the config view
     */
    private static final String TAB_CONFIG = "Config";

    /**
     * The tab tag for the debug view
     */
    private static final String TAB_DEBUG_VIEW = "DebugView";

    private DebugView mDebugView;
    private Handler mUiHandler;
    private BlaubotAndroid mBlaubot;
    private List<IBlaubotBeacon> beacons;
    private List<IBlaubotAdapter> adapters;
    private WifiManager.MulticastLock mMulticastLock;
    private WifiP2pManager.Channel mWifiP2PAcceptorChannel;
    private WifiP2pManager.Channel mWifiP2PBeaconChannel;
    private ExpandableHeightListView mBeaconsListView;
    private Spinner mAdapterSpinner;
    private BlaubotBeaconArrayAdapter mBeaconsAdapter;
    private BlaubotAdapterArrayAdapter mAdaptersAdapter;
    private Button mCreateBlaubotInstanceButton;
    private View mConfiguratorContainer;
    private View mDebugViewContainer;
    private TabHost mTabHost;
    private View mDebugViewDisabledMessage;
    private Button mDestroyBlaubotInstanceButton;
    /**
     * If anything goes wrong initializing adapters/beacons/... the exception will be stored here.
     */
    private RuntimeException mCreationFailureException;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.configurator_activity);
        this.mUiHandler = new Handler(Looper.getMainLooper());
        this.mDebugView = (DebugView) findViewById(R.id.debugView);
        this.mBeaconsListView = (ExpandableHeightListView) findViewById(R.id.beaconsList);
        this.mBeaconsListView.setExpanded(true);
        this.mAdapterSpinner = (Spinner) findViewById(R.id.adapterSpinner);
        this.mCreateBlaubotInstanceButton = (Button) findViewById(R.id.createBlaubotInstanceButton);
        this.mDestroyBlaubotInstanceButton = (Button) findViewById(R.id.destroyBlaubotInstanceButton);
        this.mConfiguratorContainer = findViewById(R.id.configuratorContainer);
        this.mDebugViewContainer = findViewById(R.id.debugViewContainer);
        this.mDebugViewDisabledMessage = findViewById(R.id.debugViewDisabledMessage);
        this.mTabHost = (TabHost) findViewById(R.id.tabHost);
        this.mTabHost.setup();


        // create tabspecs
        TabHost.TabSpec configTabSpec = this.mTabHost.newTabSpec(TAB_CONFIG);
        configTabSpec.setContent(R.id.configuratorContainer);
        configTabSpec.setIndicator("Config");
        TabHost.TabSpec debugViewTabSpec = this.mTabHost.newTabSpec(TAB_DEBUG_VIEW);
        debugViewTabSpec.setContent(R.id.debugViewContainer);
        debugViewTabSpec.setIndicator("DebugView");
        this.mTabHost.addTab(configTabSpec);
        this.mTabHost.addTab(debugViewTabSpec);


        // initally don't show debug view
        disableDebugView();
        this.mDestroyBlaubotInstanceButton.setEnabled(false);

        // wire and set up everything 
        try {
            setUp();
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Creation failed.", e);
            e.printStackTrace();
            mCreationFailureException = e;
        }
    }

    /**
     * Set up all beacons, adapters and views
     *
     * @throws RuntimeException if something goes wrong setting all the things up.
     */
    private void setUp() throws RuntimeException {
        // get system services
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        WifiP2pManager wifiP2pManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);

        // lock for multicast and bonjour beacon
        if (wifiManager != null) {
            mMulticastLock = wifiManager.createMulticastLock("BlaubotMulticastLock");
            mMulticastLock.setReferenceCounted(true);
            mMulticastLock.acquire();
        }

        // channels for wifip2p
        this.mWifiP2PBeaconChannel = wifiP2pManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.e(LOG_TAG, "onChannelDisconnected for beacon channel");
            }
        });
        this.mWifiP2PAcceptorChannel = wifiP2pManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.e(LOG_TAG, "onChannelDisconnected for acceptor channel");
            }
        });


        // create beacons and adapters
        final IBlaubotDevice ownDevice = new BlaubotDevice();
        final BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(APP_UUID);
        final InetAddress localInetAddress = BlaubotFactory.getLocalIpAddress();
        final String hostAddress = localInetAddress.getHostAddress();

        // create all adapters
        BlaubotBluetoothAdapter blaubotBluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
        BlaubotEthernetAdapter blaubotEthernetAdapter = new BlaubotEthernetAdapter(ownDevice, DEFAULT_ACCEPTOR_PORT, localInetAddress);
        BlaubotWifiAdapter blaubotWifiAdapter = new BlaubotWifiAdapter(ownDevice, uuidSet, DEFAULT_ACCEPTOR_PORT, wifiManager, connectivityManager);
        BlaubotWebsocketAdapter websocketAdapter = new BlaubotWebsocketAdapter(ownDevice, hostAddress, DEFAULT_WEBSOCKET_ACCEPTOR_PORT);

        // create all beacons
        BlaubotBluetoothBeacon bluetoothBeacon = new BlaubotBluetoothBeacon();
        BlaubotBonjourBeacon bonjourBeacon = new BlaubotBonjourBeacon(localInetAddress, DEFAULT_BONJOUR_BEACON_PORT);
        BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();
        BlaubotEthernetMulticastBeacon multicastBeacon = new BlaubotEthernetMulticastBeacon(DEFAULT_MULTICAST_BEACON_PORT, DEFAULT_MULTICAST_BEACON_BROADCAST_PORT);
        BlaubotWifiP2PBeacon wifiP2PBeacon = new BlaubotWifiP2PBeacon(wifiP2pManager, mWifiP2PBeaconChannel, DEFAULT_WIFI_P2P_BEACON_PORT);

        // fill lists
        this.beacons = Arrays.asList(bluetoothBeacon, bonjourBeacon, nfcBeacon, multicastBeacon, wifiP2PBeacon);
        this.adapters = Arrays.asList(blaubotBluetoothAdapter, blaubotEthernetAdapter, blaubotWifiAdapter, websocketAdapter);

        this.mBeaconsAdapter = new BlaubotBeaconArrayAdapter(this.beacons);
        this.mAdaptersAdapter = new BlaubotAdapterArrayAdapter(this.adapters);

        this.mBeaconsListView.setAdapter(this.mBeaconsAdapter);
        this.mBeaconsListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        this.mBeaconsListView.setItemChecked(0, true);
        this.mAdapterSpinner.setAdapter(this.mAdaptersAdapter);


        // Bind create
        mCreateBlaubotInstanceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                IBlaubotAdapter selectedAdapter = (IBlaubotAdapter) mAdapterSpinner.getSelectedItem();
                Set<IBlaubotBeacon> selectedBeacons = mBeaconsAdapter.getSelectedBeacons();

                // validate first
                if (selectedBeacons.isEmpty()) {
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(BlaubotConfiguratorActivity.this, "Please select at least one beacon.", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                boolean stopped = stopAndDestroyBlaubotInstance();
                if (!stopped) {
                    return;
                }

                Log.d(LOG_TAG, "Creating blaubot from configurator.");
                Log.d(LOG_TAG, "Selected beacons: " + selectedBeacons);
                Log.d(LOG_TAG, "Chosen adapter: " + selectedAdapter);

                // construct blaubot
                final ArrayList<IBlaubotBeacon> beacons = new ArrayList<>(selectedBeacons);
                final ArrayList<IBlaubotAdapter> adapters = new ArrayList<>();
                adapters.add(selectedAdapter);
                mBlaubot = new BlaubotAndroid(ownDevice, uuidSet, adapters, beacons);

                // call lifecycle events
                mBlaubot.registerReceivers(BlaubotConfiguratorActivity.this);
                mBlaubot.onResume(BlaubotConfiguratorActivity.this);

                // regsiter with debug view
                mDebugView.registerBlaubotInstance(mBlaubot);

                // enable destroy button
                mDestroyBlaubotInstanceButton.setEnabled(true);

                // show the debug view
                enableDebugView();
                mTabHost.setCurrentTabByTag(TAB_DEBUG_VIEW);
            }
        });

        // Bind destroy
        mDestroyBlaubotInstanceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBlaubot == null) {
                    return;
                }
                boolean destroyed = stopAndDestroyBlaubotInstance();
                if (destroyed) {
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(BlaubotConfiguratorActivity.this, "Successfully stopped and destroyed the Blaubot instance.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    /**
     * @return true, iff the blaubot instance was stopped and destroyed successfully or there wasn't an instance after all
     */
    private boolean stopAndDestroyBlaubotInstance() {
        if (mBlaubot != null) {
            if (mBlaubot.isStarted()) {
                final CountDownLatch latch = new CountDownLatch(1);
                mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(new IBlaubotConnectionStateMachineListener() {
                    @Override
                    public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {

                    }

                    @Override
                    public void onStateMachineStopped() {
                        latch.countDown();
                    }

                    @Override
                    public void onStateMachineStarted() {

                    }
                });
                mBlaubot.stopBlaubot();
                try {
                    boolean timedOut = !latch.await(STOP_TIMEOUT, TimeUnit.SECONDS);
                    if (timedOut) {
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(BlaubotConfiguratorActivity.this, "Error: The blaubot instance did not stop fast enough.", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return false;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            mDebugView.unregisterBlaubotInstance();
            mBlaubot.onPause(BlaubotConfiguratorActivity.this);
            mBlaubot.unregisterReceivers(BlaubotConfiguratorActivity.this);
            try {
                mBlaubot.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mBlaubot = null;
            disableDebugView();
            mDestroyBlaubotInstanceButton.setEnabled(false);
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(LOG_TAG, "LifeCycle.onNewIntent(" + intent + ")");
        if (mBlaubot != null && mBlaubot instanceof BlaubotAndroid) {
            BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.onNewIntent(intent);
        }
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "LifeCycle.onResume");
        if (mCreationFailureException != null) {
            // stop on error
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.drawable.ic_stopped);
            builder.setTitle("Creation error");
            builder.setMessage("Could not create Blaubot. Is Wi-Fi/Bluetooth/... turned on? Exception: " + mCreationFailureException + "")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            BlaubotConfiguratorActivity.this.finish();
                        }
                    });
            builder.create().show();
            super.onResume();
            return;
        }
        if (mBlaubot != null && mBlaubot instanceof BlaubotAndroid) {
            BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.setContext(this);
            blaubotAndroid.registerReceivers(this);
            blaubotAndroid.onResume(this);
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "LifeCycle.onPause");
        super.onPause();
        if (mBlaubot != null && mBlaubot instanceof BlaubotAndroid) {
            BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.unregisterReceivers(this);
            blaubotAndroid.onResume(this);
        }
    }

    @Override
    protected void onStop() {
        Log.d(LOG_TAG, "LifeCycle.onStop");
        if (mBlaubot != null && mBlaubot instanceof BlaubotAndroid) {
            BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.stopBlaubot();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "LifeCycle.onDestroy");
        super.onDestroy();
    }

    private void enableDebugView() {
        mDebugView.setVisibility(View.VISIBLE);
        mDebugViewDisabledMessage.setVisibility(View.GONE);
    }

    private void disableDebugView() {
        mDebugView.setVisibility(View.INVISIBLE);
        mDebugViewDisabledMessage.setVisibility(View.VISIBLE);
    }


    private class BlaubotBeaconArrayAdapter extends ArrayAdapter<IBlaubotBeacon> {
        private Set<IBlaubotBeacon> selectedBeacons = Collections.newSetFromMap(new HashMap<IBlaubotBeacon, Boolean>());

        public BlaubotBeaconArrayAdapter(List<IBlaubotBeacon> beacons) {
            super(BlaubotConfiguratorActivity.this, R.layout.beacon_list_item, R.id.name, beacons);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final IBlaubotBeacon beacon = getItem(position);
            boolean reused = convertView != null;
            View view = reused ? convertView : View.inflate(this.getContext(), R.layout.beacon_list_item, null);
            TextView nameTextView = (TextView) view.findViewById(R.id.name);
            ImageView iconImageView = (ImageView) view.findViewById(R.id.icon);
            final CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkbox);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        selectedBeacons.add(beacon);
                    } else {
                        selectedBeacons.remove(beacon);
                    }
                }
            });
            if (reused) {
                // if reused, maintain checkbox state
                checkBox.setSelected(selectedBeacons.contains(beacon));
            }
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkBox.toggle();
                }
            });
            iconImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_scout));
            nameTextView.setText(beacon.getClass().getSimpleName() + "");
            return view;
        }

        /**
         * The list of selectedBeacons
         *
         * @return
         */
        public Set<IBlaubotBeacon> getSelectedBeacons() {
            return selectedBeacons;
        }
    }

    /**
     * Shows list
     */
    private class BlaubotAdapterArrayAdapter extends ArrayAdapter<IBlaubotAdapter> {
        public BlaubotAdapterArrayAdapter(List<IBlaubotAdapter> adapters) {
            super(BlaubotConfiguratorActivity.this, R.layout.adapter_list_item, R.id.name, adapters);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        private View getCustomView(int position, View convertView, ViewGroup parent) {
            final IBlaubotAdapter adapter = getItem(position);
            View view = convertView != null ? convertView : View.inflate(this.getContext(), R.layout.adapter_list_item, null);
            TextView nameTextView = (TextView) view.findViewById(R.id.name);
            ImageView iconImageView = (ImageView) view.findViewById(R.id.icon);
            iconImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_merge));
            nameTextView.setText(adapter.getClass().getSimpleName() + "");
            return view;
        }
    }
}
