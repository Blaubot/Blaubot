package de.hsrm.blaubot.ethernet;

import java.net.InetAddress;
import java.util.UUID;

import android.app.Activity;
import android.os.Bundle;
import de.hsrm.blaubot.R;
import de.hsrm.blaubot.android.BlaubotAndroidFactory;
import de.hsrm.blaubot.android.views.ConnectionView;
import de.hsrm.blaubot.android.views.KingdomView;
import de.hsrm.blaubot.android.views.StateView;
import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.util.Log;

/**
 * Blaubot ethernet activity
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class EthernetActivity extends Activity {
	private static final String LOG_TAG = "EthernetActivity";
	private static final UUID APP_UUID = UUID.fromString("de506eef-d894-4c18-97c3-d877ff26ca38");

	private StateView mBlaubotStateView;
	private ConnectionView mConnectionView;
	private KingdomView mKingdomView;
	private Blaubot mBlaubot;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.ethernet_activity);
		this.mBlaubotStateView = (StateView) findViewById(R.id.ethernetStateView);
		this.mConnectionView = (ConnectionView) findViewById(R.id.ethernetConnectionView);
		this.mKingdomView = (KingdomView) findViewById(R.id.ethernetKingdomView);
		
		Thread startThread = new Thread(new Runnable() {
			@Override
			public void run() {
				InetAddress inetAddress = BlaubotAndroidFactory.getLocalIpAddress();
				Log.d(LOG_TAG, "Using inetAddress: " + inetAddress);
				mBlaubot = BlaubotAndroidFactory.createEthernetBlaubot(APP_UUID, 5001, 5002, 5003, inetAddress);
				mConnectionView.setBlaubotInstance(mBlaubot);
				mBlaubotStateView.registerBlaubotInstance(mBlaubot);
				mKingdomView.registerBlaubotInstance(mBlaubot);
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
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

}
