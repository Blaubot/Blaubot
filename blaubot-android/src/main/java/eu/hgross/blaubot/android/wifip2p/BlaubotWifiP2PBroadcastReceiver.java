package eu.hgross.blaubot.android.wifip2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.hgross.blaubot.util.Log;

/**
 * A BroadcastReceiver that gets notified on important WIFI Direct events.
 * This events can be retrieved by regsitering a {@link IBlaubotWifiDirectEventListener}s
 * to this object via addEventListener(..).
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class BlaubotWifiP2PBroadcastReceiver extends BroadcastReceiver {
    protected static final String LOG_TAG = "WifiP2PBroadcastReceiver";
    /**
     * If true, events will be logged.
     */
    private static final boolean LOG_EVENTS = false;

    /**
     * Threadpool used to notify listenres.
     */
    private ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * The wifi manager obtained from the android context
     */
    private WifiP2pManager mManager;

    /**
     * The initialized wifi channel to operate on
     */
	private Channel mChannel;

    /**
     * The attached listeners to this receiver
     */
    private List<IBlaubotWifiDirectEventListener> eventListeners;

    /**
	 * The needed {@link WifiP2pManager} and {@link Channel} can be retrieved like this:
	 *    mManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
	 *	  mChannel = mManager.initialize(context, context.getMainLooper(), null);
	 * 
	 * @param manager the {@link WifiP2pManager} manager instace
	 * @param channel the channel
	 */
	public BlaubotWifiP2PBroadcastReceiver(WifiP2pManager manager, Channel channel) {
		super();
		this.mManager = manager;
		this.mChannel = channel;
		this.eventListeners = new CopyOnWriteArrayList<BlaubotWifiP2PBroadcastReceiver.IBlaubotWifiDirectEventListener>();
	}

	public interface IBlaubotWifiDirectEventListener {
		/**
		 * Called when the P2PWifiManager's state changed to enabled
		 */
		public void onP2PWifiEnabled();

		/**
		 * Called when the P2PWifiManager's state changed to disabled
		 */
		public void onP2PWifiDisabled();

		/**
		 * Called when a discovery for WifiDirect devices was started.
		 */
		public void onDiscoveryStarted();

		/**
		 * Called when a discovery for WifiDirect devices was stopped/finished.
		 */
		public void onDiscoveryStopped();

		/**
		 * Called when the list of CURRENTLY AVAILBALE devices changed.
		 * @param deviceList the list of currently available devices
		 */
		public void onListOfPeersChanged(WifiP2pDeviceList deviceList);

		/**
		 * Called on changes to the P2p-WiFi connectivity like connects, disconnects, ... 
		 * @param p2pInfo the p2p2 info containing group owner state, group owner ip, ...
		 * @param networkInfo the network information
         * @param group the group of which the connectivity changed
		 */
		public void onConnectivityChanged(WifiP2pInfo p2pInfo, NetworkInfo networkInfo, WifiP2pGroup group);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();

		if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            if(Log.logDebugMessages() && LOG_EVENTS) {
                Log.d(LOG_TAG, "Received intent: WIFI_P2P_STATE_CHANGED_ACTION");
            }
			// Check to see if Wi-Fi is enabled and notify appropriate activity
			int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
			if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                if(Log.logDebugMessages() && LOG_EVENTS) {
                    Log.d(LOG_TAG, "Wifi P2P is now enabled");
                }
				notify_p2p_enabled();
			} else {
                if(Log.logDebugMessages() && LOG_EVENTS) {
                    Log.d(LOG_TAG, "Wi-Fi P2P is now disabled ");
                }

				notify_p2p_disabled();
			}
		} else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            if(Log.logDebugMessages() && LOG_EVENTS) {
                Log.d(LOG_TAG, "Received intent: WIFI_P2P_PEERS_CHANGED_ACTION");
            }
			// -- the list of AVAILABLE peers has changed
			// Call WifiP2pManager.requestPeers() to get a list of current peers
			// request available peers from the wifi p2p manager. This is an
			// asynchronous call and the calling activity is notified with a
			// callback on PeerListListener.onPeersAvailable()
			if (mManager != null) {
                if(Log.logDebugMessages() && LOG_EVENTS) {
                    Log.d(LOG_TAG, "Requesting peers ...");
                }
				mManager.requestPeers(mChannel, new PeerListListener() {
					@Override
					public void onPeersAvailable(WifiP2pDeviceList peers) {
                        if(Log.logDebugMessages() && LOG_EVENTS) {
                            Log.d(LOG_TAG, "Got available peers: " + peers.getDeviceList());
                        }
						notify_list_of_peers_changed(peers);
					}
				});
			}
		} else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
			/*
			 * Broadcast intent action indicating that the state of Wi-Fi p2p connectivity has changed. One extra
			 * EXTRA_WIFI_P2P_INFO provides the p2p connection info in the form of a WifiP2pInfo object. Another extra
			 * EXTRA_NETWORK_INFO provides the network info in the form of a NetworkInfo. A third extra provides the
			 * details of the group.
			 */
			// Respond to new connection or disconnections
			WifiP2pInfo p2pInfo = (WifiP2pInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
			NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
			WifiP2pGroup wifiP2pGroup = (WifiP2pGroup) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);// --> at higher api levels
            if(Log.logDebugMessages() && LOG_EVENTS) {
                Log.d(LOG_TAG, "Received intent: WIFI_P2P_CONNECTION_CHANGED_ACTION");
                Log.d(LOG_TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION.p2pInfo: " + p2pInfo);
                Log.d(LOG_TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION.networkInfo: " + networkInfo);
                Log.d(LOG_TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION.wifiP2pGroup: " + wifiP2pGroup);
            }
			notify_connectivity_changed(p2pInfo, networkInfo, wifiP2pGroup);
		} else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            if(Log.logDebugMessages() && LOG_EVENTS) {
                Log.d(LOG_TAG, "Received intent: WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
            }
			// Respond to this device's wifi state changing
		} else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
			// indicating that peer discovery has either started or stopped
			int newState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);
			if (newState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                if(Log.logDebugMessages() && LOG_EVENTS) {
                    Log.d(LOG_TAG, "P2P Discovery started");
                }
				// discovery started
				notify_discovery_started();
			} else if(newState == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                if(Log.logDebugMessages() && LOG_EVENTS) {
                    Log.d(LOG_TAG, "P2P Discovery stopped");
                }
				// discovery stopped
				notify_discovery_stopped();
			} else {
				// newState == -1
                if(Log.logWarningMessages() && LOG_EVENTS) {
                    Log.w(LOG_TAG, "Got WIFI_P2P_DISCOVERY_CHANGED_ACTION intent but could not determine the discovery state (state = " + newState + " )");
                }
			}

		}
	}


	/**
	 * Adds an event listener to this receiver.
	 * @param listener the listener to be attached
	 */
	public void addEventListener(IBlaubotWifiDirectEventListener listener) {
		this.eventListeners.add(listener);
	}
	
	/**
	 * Removes an event listener from this receiver, if registered.
	 * @param listener the listener to be attached
	 */
	public void removeEventListener(IBlaubotWifiDirectEventListener listener) {
		this.eventListeners.remove(listener);
	}
	
	/**
	 * Creates an {@link IntentFilter} for the WIFIDirect intents needed by this {@link BroadcastReceiver}.
	 * 
	 * @return the set up {@link IntentFilter}
	 */
	public static IntentFilter createWifiP2PIntentFilter() {
		// register wifip2p related intents
		IntentFilter mIntentFilter = new IntentFilter();
		mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
		mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
		return mIntentFilter;
	}

	/*
	 * Listener Notification methods.
	 */
	private void notify_discovery_stopped() {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				for (IBlaubotWifiDirectEventListener listener : eventListeners) {
					listener.onDiscoveryStopped();
				}
			}
		});
	}

	
	private void notify_discovery_started() {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				for (IBlaubotWifiDirectEventListener listener : eventListeners) {
					listener.onDiscoveryStarted();
				}
			}
		});
	}

	private void notify_connectivity_changed(final WifiP2pInfo p2pInfo, final NetworkInfo networkInfo, final WifiP2pGroup group) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				for (IBlaubotWifiDirectEventListener listener : eventListeners) {
					listener.onConnectivityChanged(p2pInfo, networkInfo, group);
				}
			}
		});
	}

	protected void notify_list_of_peers_changed(final WifiP2pDeviceList peers) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				for (IBlaubotWifiDirectEventListener listener : eventListeners) {
					listener.onListOfPeersChanged(peers);
				}
			}
		});
	}

	private void notify_p2p_disabled() {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				for (IBlaubotWifiDirectEventListener listener : eventListeners) {
					listener.onP2PWifiDisabled();
				}
			}
		});
	}

	private void notify_p2p_enabled() {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				for (IBlaubotWifiDirectEventListener listener : eventListeners) {
					listener.onP2PWifiEnabled();
				}
			}
		});
	}
	

}