package eu.hgross.blaubot.android.bluetooth.views;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.android.bluetooth.BlaubotBluetoothDevice;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.util.Log;

/**
 * A view showing the connected clients as a ListView encapsulated in an object to get rid of this ugly android specific
 * boilerplate code for a simple ListView ...
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class ACLListView extends ListView {
	private static final String LOG_TAG = "ACLListView";
	private ArrayList<IBlaubotDevice> devices;
	private Handler uiHandler;
	private BroadcastReceiver receiver;

	public ACLListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		uiHandler = new Handler(Looper.getMainLooper());
		devices = new ArrayList<IBlaubotDevice>();
		final ArrayAdapter<IBlaubotDevice> adapter = new ArrayAdapter<IBlaubotDevice>(context, R.layout.blaubot_device_list_item, R.id.blaubot_device_list_item_name, devices) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				TextView id = (TextView) view.findViewById(R.id.blaubot_device_list_item_id);
				TextView name = (TextView) view.findViewById(R.id.blaubot_device_list_item_name);
				IBlaubotDevice device = devices.get(position);
				id.setText(device.getUniqueDeviceID());
				name.setText(device.getReadableName());
				return view;
			}
		};
		this.setAdapter(adapter);
		this.receiver = (new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					BlaubotBluetoothDevice bbd = new BlaubotBluetoothDevice("UnknownDeviceId", device);
					Log.d(LOG_TAG, "ACL connected: Bluetooth device " + device + " (" + bbd.getReadableName() + ") is alive and reachable.");
					synchronized (devices) {
						devices.add(bbd);
					}
					updateList();
					// deviceAliveWatcher.reportAlive(device, System.currentTimeMillis());
				} else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					BlaubotBluetoothDevice bbd = new BlaubotBluetoothDevice("UnknownDeviceId", device);
					Log.d(LOG_TAG, "ACL disconnected: Bluetooth device " + bbd + " (" + bbd.getReadableName() + ")");
					synchronized (devices) {
						devices.remove(bbd);
					}
					updateList();
					// deviceAliveWatcher.reportDead(device);
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	private void updateList() {
		uiHandler.post(new Runnable() {
			@Override
			public void run() {
				ArrayList<IBlaubotDevice> cp = new ArrayList<IBlaubotDevice>(devices);
				ArrayAdapter<IBlaubotDevice> adapter = (ArrayAdapter<IBlaubotDevice>) getAdapter();
				adapter.clear();
				adapter.addAll(cp);
				adapter.notifyDataSetChanged();
			}
		});
	}

	public BroadcastReceiver getReceiver() {
		return receiver;
	}
}
