package eu.hgross.blaubot.ethernet;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import java.net.InetAddress;

import eu.hgross.blaubot.DemoConstants;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.android.views.DebugView;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.R;

/**
 * Blaubot ethernet activity using the bonjour beacon
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class EthernetBonjourActivity extends Activity {
	private static final String LOG_TAG = "EthernetBonjourActivity";
	private static final boolean AUTOSTART = false;
	
	private Blaubot mBlaubot;
    private DebugView mDebugView;
    private WifiManager.MulticastLock mMulticastLock;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.ethernet_bonjour_activity);
        WifiManager wifi = (WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
        mMulticastLock = wifi.createMulticastLock("BlaubotMulticastLock");
        mMulticastLock.setReferenceCounted(true);
        mMulticastLock.acquire();


        this.mDebugView = (DebugView) findViewById(R.id.debugView);
		Thread startThread = new Thread(new Runnable() {
			@Override
			public void run() {
				InetAddress inetAddress = BlaubotAndroidFactory.getLocalIpAddress();
				Log.d(LOG_TAG, "Using inetAddress: " + inetAddress);
				mBlaubot = BlaubotAndroidFactory.createEthernetBlaubotWithBonjourBeacon(DemoConstants.APP_UUID_ETHERNET_BONJOUR, 5001, 5002, inetAddress);
                mDebugView.registerBlaubotInstance(mBlaubot);
                Log.d(LOG_TAG, "Blaubot instance created.");
				if (AUTOSTART) {
					mBlaubot.startBlaubot();
				}
			}
		});
		startThread.start();
		try {
			startThread.join();
		} catch (InterruptedException e) {
		}

	}


	@Override
	protected void onResume() {
        super.onResume();
	}

	@Override
	protected void onStop() {
        mBlaubot.stopBlaubot();
        mMulticastLock.release();
		super.onStop();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

}
