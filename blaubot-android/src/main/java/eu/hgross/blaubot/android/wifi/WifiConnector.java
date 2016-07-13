package eu.hgross.blaubot.android.wifi;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helps to connect to a Wifi Network.
 * 
 * A {@link WifiConnector} is instantiated with the SSID and a PSK of a Wifi
 * network. Once you have an instance, you can call either
 * {@link WifiConnector#connect()} which is blocking for a maximum of 10
 * seconds or you utilize
 * {@link WifiConnector#connect(IWifiConnectorCallback)} to get
 * asynchronously informed via a listener (the max 10 seconds timeout applies
 * here too).
 *
 * You can also check if you are currently connected to a WiFi network via
 * {@link WifiConnector#isConnected()}.
 *
 * Note that this class is not reusable. Reinstantiate after use!
 *
 */
public class WifiConnector {
    private final WifiApUtil apUtil;

    public interface IWifiConnectorCallback {
		public void onSuccess();

		public void onFailure();
	}

	private static final long FAIL_TIMEOUT = 8000; // Give up after x milliseconds
	private static final String LOG_TAG = "WifiConnector";
	private String ssid;
	private String psk;
	private WifiManager wifiManager;
	private volatile CountDownLatch connectLatch;
	private ConnectivityManager connectivityManager;
    private Integer addedNetworkId;

	/**
	 *
	 * @param connectivityManager android's connectivity manager service
	 * @param wifiManager android's wifi manager service
	 * @param ssid
	 *            the network's SSID
	 * @param psk
	 *            the network's pre shared key. If empty string or null, the
	 *            network is assumed to be an open network without any
	 *            encryption
	 */
	public WifiConnector(ConnectivityManager connectivityManager, WifiManager wifiManager, String ssid, String psk) {
		this.wifiManager = wifiManager;
		this.connectivityManager = connectivityManager;
		this.ssid = ssid;
		this.psk = psk;
        this.apUtil = WifiApUtil.createInstance(wifiManager);
	}

    /**
     * When using {WifiConnector#connect} a WifiConfiguration is added to the system.
     * This method removes WifiConfiguration created from the most recent call to connect()
     * Note that this also disconnects the WiFi if connected.
     */
    public void removeAddedWifiConfiguration() {
        if(addedNetworkId != null) {
            Log.d(LOG_TAG, "Removing WiFi network config with id " + addedNetworkId);
            wifiManager.removeNetwork(addedNetworkId);
            wifiManager.saveConfiguration();
        }
    }

	/**
	 * I tried to dynamically register a broadcast receiver to get informed on
	 * this but android (once again) does not act as described in their
	 * documentation. So here you get it ... we poll the state. This buggy
	 * {@link android.content.BroadcastReceiver} system is a pain since version 1.0 ...
	 * 
	 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
	 * 
	 */
	private abstract class PollNetworkStateThread extends Thread {
		private static final long POLL_INTERVALL = 150;
		private CountDownLatch latch;
		public final AtomicBoolean active = new AtomicBoolean(true);
		private AtomicBoolean resultBoolean;
		private Runnable callback;

		public PollNetworkStateThread(CountDownLatch latch, AtomicBoolean result) {
			this.latch = latch;
			this.resultBoolean = result;
		}
		
		public PollNetworkStateThread(CountDownLatch latch, AtomicBoolean result, Runnable finishedCallback) {
			this(latch, result);
			this.callback = finishedCallback;
		}

		/**
		 * @return true if the condition is meht, false otherwise
		 */
		protected abstract boolean pollCheck();
		
		@Override
		public void run() {
			while (active.get()) {
				if (isConnected()) {
					resultBoolean.set(true);
					latch.countDown();
					break;
				}
				try {
					Thread.sleep(POLL_INTERVALL);
				} catch (InterruptedException e) {
					latch.countDown();
					break;
				}
			}
			// finished callback
			if(callback != null) {
				callback.run();
			}
		}

	}

	/**
	 * Checks if the android device is connected to the desired SSID (from
	 * constructor).
	 * 
	 * @return true iff connected to the desired SSID
	 */
	public boolean isConnected() {
		if(!wifiManager.isWifiEnabled()) {
            if(!(apUtil.isApSupported() && apUtil.isWifiApEnabled())) {
                return false;
            }
		}
		NetworkInfo wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (wifi == null) {
			return false; // not supported by device
		}
		boolean wifiConnected = wifi.isConnected();
		if (!wifiConnected) {
			return false;
		}

		WifiInfo connectionInfo = wifiManager.getConnectionInfo();
		String ssid = connectionInfo.getSSID();
		boolean isConnected = ssid != null;
		if (isConnected) {
			if (ssid.equals("\"" + WifiConnector.this.ssid + "\"")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Connects to the network (same as connect(boolean) but with async api)
	 * 
	 * @param callback the callback to be called when the connect operation finished
	 */
	public void connect(final IWifiConnectorCallback callback) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				boolean succeeded = connect();
				if (succeeded) {
					callback.onSuccess();
				} else {
					callback.onFailure();
				}
			}
		}).start();
	}

	/**
	 * Activates wifi
	 * @param timeout max time to wait for activation (before fail)
	 * @param finishedCallback called when finished
	 */
	private void activateWifi(long timeout, Runnable finishedCallback) {
		if (wifiManager.isWifiEnabled()) {
			if(finishedCallback != null) {
				finishedCallback.run();
			}
			return;
		}
		CountDownLatch activatedLatch = new CountDownLatch(1);
		boolean enabled = wifiManager.setWifiEnabled(true);
		if (!enabled) {
			Log.e(LOG_TAG, "Could not enable wifi");
			if(finishedCallback != null) {
				finishedCallback.run();
			}
			return;
		}
		
		final AtomicBoolean result = new AtomicBoolean(false);
		PollNetworkStateThread poller = new PollNetworkStateThread(activatedLatch, result, finishedCallback) {
			@Override
			protected boolean pollCheck() {
				return wifiManager.isWifiEnabled();
			}
		};
		poller.start();
		
		try {
			boolean timedOut = !activatedLatch.await(timeout, TimeUnit.MILLISECONDS);
			if(timedOut) {
				poller.active.set(false);
			}
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Got interrupted while waiting");
		}
	}
	
	
	/**
	 * Connects to the network.
	 * 
	 * @return true if connection was successfull, false otherwise
	 */
	public boolean connect() {
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean result = new AtomicBoolean(false);
		activateWifi(FAIL_TIMEOUT, new Runnable() {
			@Override
			public void run() {
				if(wifiManager.isWifiEnabled()) {
					boolean succeeded = _connect();
					result.set(succeeded);
				} else {
					result.set(false);
				}
				latch.countDown();
			}
		});
		try {
			boolean timedOut = !latch.await(FAIL_TIMEOUT, TimeUnit.MILLISECONDS);
			if(timedOut) {
				return false;
			}
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Got interrupted while waiting for wifi network connection");
		}
		return result.get();
	}
	
	private synchronized boolean _connect() {
		Log.d(LOG_TAG, "Trying to connect to network " + ssid);
		connectLatch = new CountDownLatch(1);
		final AtomicBoolean result = new AtomicBoolean(false);
		final WifiConfiguration wifiConfig = new WifiConfiguration();
		wifiConfig.SSID = String.format("\"%s\"", ssid);
		if (psk == null || psk.isEmpty()) {
			wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
		} else {
			wifiConfig.preSharedKey = String.format("\"%s\"", psk);
		}
		final PollNetworkStateThread connectedToSSIDpoller = new PollNetworkStateThread(connectLatch, result) {
			@Override
			protected boolean pollCheck() {
				return isConnected();
			}
			
		};
		new Thread(new Runnable() {
			@Override
			public void run() {
				Log.d(LOG_TAG, "Adding wifi config");
				int netId = wifiManager.addNetwork(wifiConfig);
				boolean networkAdded = netId != -1;
                WifiConnector.this.addedNetworkId = netId;
//				Log.d(LOG_TAG, "Disconnecting from current network (if any)");
//				boolean disconnected = wifiManager.disconnect();
                boolean disconnected = true;
                Log.d(LOG_TAG, "Enabling newly added wifi config");
				boolean networkEnabled = wifiManager.enableNetwork(netId, true);
                Log.d(LOG_TAG, "Connecting to network");
				boolean reconnected = wifiManager.reconnect();

				if (!(networkAdded && disconnected && networkEnabled && reconnected)) {
					connectLatch.countDown();
					result.set(false);
					return;
				}

				connectedToSSIDpoller.start();
			}
		}).start();

		try {
			boolean timeOutOccured = !connectLatch.await(FAIL_TIMEOUT, TimeUnit.MILLISECONDS);
			if (timeOutOccured) {
				Log.w(LOG_TAG, "Connection to network " + ssid + " failed (Timeout)");
				result.set(false);
			}
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Got interrupted while waiting for wifi network connection");
		}
		connectedToSSIDpoller.active.set(false);
		if (result.get()) {
			Log.d(LOG_TAG, "Connection to network with SSID " + ssid + " succeeded");
		} else {
			Log.d(LOG_TAG, "Connection to network with SSID " + ssid + " failed");
		}
		return result.get();
	}

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("WifiConnector{");
        sb.append("ssid='").append(ssid).append('\'');
        sb.append(", psk='").append(psk).append('\'');
        sb.append(", addedNetworkId=").append(addedNetworkId);
        sb.append('}');
        return sb.toString();
    }
}
