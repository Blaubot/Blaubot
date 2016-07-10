package eu.hgross.blaubot.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.android.bluetooth.BlaubotBluetoothDeviceDiscoveryReceiver;
import eu.hgross.blaubot.android.bluetooth.IBluetoothDiscoveryListener;
import eu.hgross.blaubot.android.bluetooth.views.ACLListView;
import eu.hgross.blaubot.android.views.DebugView;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.DemoConstants;
import eu.hgross.blaubot.R;

/**
 * Blaubot bluetooth activity with NFC beacon
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class BluetoothNFCActivity extends Activity {
    private static final String LOG_TAG = "BluetoothNFCActivity";
    protected static final int DISCOVERABLE_TIME = 0; // seconds

    private Button mDiscoverButton;
    private Button mMakeDiscoverableButton;
    private BlaubotBluetoothDeviceDiscoveryReceiver mDeviceDiscoveryReceiver;

    private DebugView mDebugView;
    private ListView mDevicesListview;
    private ACLListView mACLListView;
    private ArrayList<BluetoothDeviceListItem> mBluetoothDeviceList;
    private Handler mUiHandler;
    private BlaubotAndroid mBlaubot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_nfc_activity);
        this.mUiHandler = new Handler(Looper.getMainLooper());
        this.mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubotWithNFCBeacon(DemoConstants.APP_UUID_BLUETOOTH_NFC);
        this.mDebugView = (DebugView) findViewById(R.id.blaubotDebugView);
        this.mDebugView.registerBlaubotInstance(mBlaubot);

        this.mDiscoverButton = (Button) findViewById(R.id.discoverButton);
        this.mACLListView = (ACLListView) findViewById(R.id.aclListView);
        this.mMakeDiscoverableButton = (Button) findViewById(R.id.makeDiscoverableButton);
        this.mDevicesListview = (ListView) findViewById(R.id.bluetoothDevicesListView);
        this.mBluetoothDeviceList = new ArrayList<>();
        final ArrayAdapter<BluetoothDeviceListItem> adapter = new ArrayAdapter<BluetoothDeviceListItem>(getApplicationContext(), R.layout.bluetooth_list_item, R.id.bluetooth_list_text1, mBluetoothDeviceList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = (TextView) view.findViewById(R.id.bluetooth_list_text1);
                TextView text2 = (TextView) view.findViewById(R.id.bluetooth_list_text2);

                BluetoothDeviceListItem listItem = mBluetoothDeviceList.get(position);
                text1.setText(listItem.device.getName());
                text2.setText(listItem.device.getAddress());
                return view;
            }
        };
        this.mDevicesListview.setAdapter(adapter);
        this.mDeviceDiscoveryReceiver = new BlaubotBluetoothDeviceDiscoveryReceiver(false);
        this.mDeviceDiscoveryReceiver.addBluetoothDiscoveryListener(new IBluetoothDiscoveryListener() {
            private void updateListView(final List<BluetoothDevice> discoveredDevices, final HashMap<BluetoothDevice, List<UUID>> discoveredServices) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.clear();
                        mBluetoothDeviceList.clear();
                        mBluetoothDeviceList.addAll(BluetoothDeviceListItem.convertList(discoveredDevices));
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onDiscoveryStarted() {
                Log.d(LOG_TAG, "Discovery started");
                mDiscoverButton.setEnabled(false);
                mDiscoverButton.setText("Discovering ...");
            }

            @Override
            public void onDiscoveryFinished(List<BluetoothDevice> discoveredDevices, HashMap<BluetoothDevice, List<UUID>> discoveredServices) {
                Log.d(LOG_TAG, "Discovery finished");
                updateListView(discoveredDevices, discoveredServices);
                mDiscoverButton.setEnabled(true);
                mDiscoverButton.setText("Discover devices");
            }

            @Override
            public void onDiscovery(List<BluetoothDevice> discoveredDevices, HashMap<BluetoothDevice, List<UUID>> discoveredServices) {
                updateListView(discoveredDevices, discoveredServices);
            }
        });

        this.mDiscoverButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                BluetoothAdapter.getDefaultAdapter().startDiscovery();
            }
        });

        this.mMakeDiscoverableButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // make visible
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_TIME);
                startActivity(discoverableIntent);
            }
        });

    }


    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(LOG_TAG, "LifeCycle.onNewIntent(" + intent + ")");
        mBlaubot.onNewIntent(intent);
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "LifeCycle.onResume");
        IntentFilter filter = BlaubotBluetoothDeviceDiscoveryReceiver.createBluetoothIntentFilter();
        registerReceiver(mDeviceDiscoveryReceiver, filter);
        registerReceiver(mACLListView.getReceiver(), filter);

        mBlaubot.setContext(this);
        mBlaubot.registerReceivers(this);
        mBlaubot.onResume(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "LifeCycle.onPause");
        super.onPause();
        mBlaubot.unregisterReceivers(this);
        mBlaubot.onResume(this);
    }

    @Override
    protected void onStop() {
        Log.d(LOG_TAG, "LifeCycle.onStop");
        unregisterReceiver(mDeviceDiscoveryReceiver);
        unregisterReceiver(mACLListView.getReceiver());
        mBlaubot.stopBlaubot();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "LifeCycle.onDestroy");
        super.onDestroy();
    }
}
