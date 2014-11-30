package de.hsrm.blaubot.android.views;

import java.util.ArrayList;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.hsrm.blaubot.android.R;
import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import de.hsrm.blaubot.util.Log;

/**
 * A view showing the connected clients as a ListView encapsulated in an object to get rid of this ugly android specific
 * boilerplate code for a simple ListView ...
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class ConnectionView extends ListView implements IBlaubotConnectionManagerListener {
	private static final String LOG_TAG = "ConnectionView";
	private Blaubot blaubot;
	private ArrayList<IBlaubotConnection> connections;
	private Handler uiHandler;
	private Object monitor = new Object();

	public ConnectionView(Context context, AttributeSet attrs) {
		super(context, attrs);
		uiHandler = new Handler(Looper.getMainLooper());
		connections = new ArrayList<IBlaubotConnection>();
		final ArrayAdapter<IBlaubotConnection> adapter = new ArrayAdapter<IBlaubotConnection>(context, R.layout.blaubot_device_list_item, R.id.blaubot_device_list_item_name, connections) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				TextView id = (TextView) view.findViewById(R.id.blaubot_device_list_item_id);
				TextView name = (TextView) view.findViewById(R.id.blaubot_device_list_item_name);
				IBlaubotConnection connection = connections.get(position);
				id.setText(connection.getRemoteDevice().getUniqueDeviceID());
				name.setText(connection.getRemoteDevice().getReadableName());
				return view;
			}
		};
		this.setAdapter(adapter);
		this.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				IBlaubotConnection selectedItem = connections.get(position);
				selectedItem.disconnect();
			}
		});
	}

	public void setBlaubotInstance(Blaubot blaubot) {
		if (this.blaubot != null) {
			this.blaubot.getConnectionManager().removeConnectionListener(this);
		}
		this.blaubot = blaubot;
		this.blaubot.getConnectionManager().addConnectionListener(this);
	}

	@Override
	public void onConnectionClosed(final IBlaubotConnection connection) {
		uiHandler.post(new Runnable() {
			@Override
			public void run() {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got connection closed event - " + connection);
				}
				synchronized (monitor) {
					connections.remove(connection);
				}
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Connections: " + connections);
				}
				updateList();
			}
		});
	}

	@Override
	public void onConnectionEstablished(final IBlaubotConnection connection) {
		uiHandler.post(new Runnable() {
			@Override
			public void run() {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got connection established event - " + connection);
				}
				synchronized (monitor) {
					connections.add(connection);
				}
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Connections: " + connections);
				}
				updateList();
			}
		});
	}

	@SuppressWarnings("unchecked")
	private void updateList() {
		ArrayList<IBlaubotConnection> cp = new ArrayList<IBlaubotConnection>(connections);
		ArrayAdapter<IBlaubotConnection> adapter = (ArrayAdapter<IBlaubotConnection>) getAdapter();
		adapter.clear(); // hate this ... but android will not update otherwise ... bloody hell
		adapter.addAll(cp);
		adapter.notifyDataSetChanged();
	}
}
