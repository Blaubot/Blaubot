package de.hsrm.blaubot.bluetooth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

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
import de.hsrm.blaubot.R;
import de.hsrm.blaubot.android.BlaubotAndroidFactory;
import de.hsrm.blaubot.android.bluetooth.BlaubotBluetoothDeviceDiscoveryReceiver;
import de.hsrm.blaubot.android.bluetooth.IBluetoothDiscoveryListener;
import de.hsrm.blaubot.android.bluetooth.views.ACLListView;
import de.hsrm.blaubot.android.views.ConnectionView;
import de.hsrm.blaubot.android.views.StateView;
import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.util.Log;

/**
 * Blaubot bluetooth activity
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BluetoothActivity extends Activity {
	private static final String LOG_TAG = "BluetoothActivity";
	private static final UUID APP_UUID = UUID.fromString("8790ba22-47a6-4f1c-86db-a32d7b2b82ba");
	protected static final int DISCOVERABLE_TIME = 0; // seconds

	private Button mDiscoverButton;
	private Button mMakeDiscoverableButton;
	private StateView mBlaubotStateView;
	private ConnectionView mConnectionView;
	private BlaubotBluetoothDeviceDiscoveryReceiver mDeviceDiscoveryReceiver;
	private ListView mDevicesListview;
	private ACLListView mACLListView;
	private ArrayList<BluetoothDeviceListItem> mBluetoothDeviceList;
	private Handler mUiHandler;
	private Blaubot mBlaubot;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bluetooth_activity);
		this.mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubot(APP_UUID);
		this.mACLListView = (ACLListView) findViewById(R.id.aclListView);
		this.mBlaubotStateView = (StateView) findViewById(R.id.blaubotStateView);
		this.mConnectionView = (ConnectionView) findViewById(R.id.connectionView);
		this.mConnectionView.setBlaubotInstance(mBlaubot);
		this.mUiHandler = new Handler(Looper.getMainLooper());
		this.mDiscoverButton = (Button) findViewById(R.id.discoverButton);
		this.mMakeDiscoverableButton = (Button) findViewById(R.id.makeDiscoverableButton);
		this.mDevicesListview = (ListView) findViewById(R.id.bluetoothDevicesListView);
		this.mBluetoothDeviceList = new ArrayList<BluetoothDeviceListItem>();
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
				mDiscoverButton.setText("Discover devices ...");
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

		this.mBlaubotStateView.registerBlaubotInstance(mBlaubot);
	}

	@Override
	protected void onResume() {
		IntentFilter filter = BlaubotBluetoothDeviceDiscoveryReceiver.createBluetoothIntentFilter();
		registerReceiver(mDeviceDiscoveryReceiver, filter);
		registerReceiver(mACLListView.getReceiver(), filter);
		super.onResume();
	}

	@Override
	protected void onStop() {
		unregisterReceiver(mDeviceDiscoveryReceiver);
		unregisterReceiver(mACLListView.getReceiver());
		super.onStop();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

}
