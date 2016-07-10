package eu.hgross.blaubot.ethernet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.net.InetAddress;

import eu.hgross.blaubot.DemoConstants;
import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.android.views.DebugView;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.R;

/**
 * Blaubot ethernet activity with a nfc beacon
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class EthernetNFCActivity extends Activity {
	private static final String LOG_TAG = "EthernetNFCActivity";

	private BlaubotAndroid mBlaubot;
    private DebugView mDebugView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "LifeCycle.onCreate");
        super.onCreate(savedInstanceState);
		setContentView(R.layout.ethernet_nfc_activity);
        this.mDebugView = (DebugView) findViewById(R.id.debugView);
		Thread startThread = new Thread(new Runnable() {
			@Override
			public void run() {
				InetAddress inetAddress = BlaubotAndroidFactory.getLocalIpAddress();
				Log.d(LOG_TAG, "Using inetAddress: " + inetAddress);
				mBlaubot = BlaubotAndroidFactory.createEthernetBlaubotWithNFCBeacon(DemoConstants.APP_UUID_ETHERNET_NFC, 16666, inetAddress);
				mDebugView.registerBlaubotInstance(mBlaubot);
                Log.d(LOG_TAG, "Blaubot instance created.");
			}
		});
		startThread.start();
		try {
			startThread.join();
		} catch (InterruptedException e) {
		}

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
        mBlaubot.stopBlaubot();
		super.onStop();
	}

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "LifeCycle.onDestroy");
        super.onDestroy();
    }
}
