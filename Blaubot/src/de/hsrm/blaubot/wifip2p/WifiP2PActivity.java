package de.hsrm.blaubot.wifip2p;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import de.hsrm.blaubot.R;
import de.hsrm.blaubot.android.BlaubotAndroid;
import de.hsrm.blaubot.android.BlaubotAndroidFactory;
import de.hsrm.blaubot.android.views.ConnectionView;
import de.hsrm.blaubot.android.views.DiscoveryView;
import de.hsrm.blaubot.android.views.StateView;
import de.hsrm.blaubot.android.wifip2p.WifiP2PUtils;
import de.hsrm.blaubot.util.Log;

/**
 * Blaubot WifiP2P activity
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
@SuppressLint("NewApi")
public class WifiP2PActivity extends Activity {
	private static final String BONJOUR_SERVICE_TYPE = "_tcp";
	private static final String LOG_TAG = "WifiP2PActivity";
	private static final UUID APP_UUID = UUID.fromString("5da7898d-7e1e-4b62-8c12-61494dd58d5d");
	protected static final int DISCOVERABLE_TIME = 0; // seconds

	private StateView mBlaubotStateView;
	private ConnectionView mConnectionView;
	private DiscoveryView mDiscoveryView;
	private BlaubotAndroid mBlaubot;
	private Button mPrintArpToLogButton;
	private Button mDiscoverDevicesButton;
	private Button mAddLocalServiceButton;
	private Button mLogInterfacesButton;
	private Button mClearLocalServicesButton;
	private Button mSearchLocalServicesButton;
	
	private WifiP2pManager mP2PWifiManager;
	private WifiManager mWifiManager;
	private Channel mBeaconChannel;
	private Channel mAcceptorChannel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wifip2p_activity);

		this.mP2PWifiManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
		this.mBeaconChannel = mP2PWifiManager.initialize(this, getMainLooper(), null);
		this.mAcceptorChannel = mP2PWifiManager.initialize(this, getMainLooper(), null);
		this.mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		
		this.mBlaubot = BlaubotAndroidFactory.createWifiP2PBlaubot(APP_UUID, mP2PWifiManager, mWifiManager, mBeaconChannel, mAcceptorChannel);
		this.mBlaubotStateView = (StateView) findViewById(R.id.wifip2pStateView);
		this.mConnectionView = (ConnectionView) findViewById(R.id.wifip2pConnectionView);
		this.mDiscoveryView = (DiscoveryView) findViewById(R.id.wifip2pDiscoveryView);
		this.mConnectionView.setBlaubotInstance(mBlaubot);
		this.mBlaubotStateView.registerBlaubotInstance(mBlaubot);
		this.mDiscoveryView.registerBlaubotInstance(mBlaubot);
		this.mDiscoverDevicesButton = (Button) findViewById(R.id.wifip2p_discoverButton);
		this.mPrintArpToLogButton = (Button) findViewById(R.id.wifip2p_print_arp);
		this.mLogInterfacesButton = (Button) findViewById(R.id.wifip2p_log_ifaces);
		
		this.mAddLocalServiceButton = (Button) findViewById(R.id.wifip2p_addLocalServiceButton);
		this.mClearLocalServicesButton = (Button) findViewById(R.id.wifip2p_clearLocalServicesButton);
		this.mSearchLocalServicesButton = (Button) findViewById(R.id.wifip2p_searchLocalServicesButton);
		
		
		this.mAddLocalServiceButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Add the bonjour local service
				Map<String, String> txtRecordsMap = new HashMap<String, String>();
				txtRecordsMap.put("acceptorPort", "17171");
				txtRecordsMap.put("beaconPort", "17172");
				WifiP2pServiceInfo bonjourServiceInfo = WifiP2pDnsSdServiceInfo.newInstance("Blaubot", BONJOUR_SERVICE_TYPE, txtRecordsMap );
				mP2PWifiManager.addLocalService(mBeaconChannel, bonjourServiceInfo, new ActionListener() {
					
					@Override
					public void onSuccess() {
						Toast.makeText(getApplicationContext(), "Added bonjour local service", Toast.LENGTH_LONG).show();
						Log.d(LOG_TAG, "Added bonjour local service");
					}
					
					@Override
					public void onFailure(int reason) {
						Toast.makeText(getApplicationContext(), "FAIL: could not add bonjour local service, Reason: " + reason, Toast.LENGTH_LONG).show();
						Log.d(LOG_TAG, "FAIL: could not add bonjour local service, Reason: " + reason);
					}
				});
			}
		});
		
		this.mClearLocalServicesButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mP2PWifiManager.clearLocalServices(mBeaconChannel, new ActionListener() {
					
					@Override
					public void onSuccess() {
						Toast.makeText(getApplicationContext(), "Cleared local services.", Toast.LENGTH_LONG).show();
						Log.d(LOG_TAG, "Cleared local services");
					}
					
					@Override
					public void onFailure(int reason) {
						Toast.makeText(getApplicationContext(), "Failed to clear local service. Reason: " + reason, Toast.LENGTH_LONG).show();
						Log.d(LOG_TAG, "FAIL: could not clear local service, Reason: " + reason);
					}
				});
			}
		});
		
		this.mSearchLocalServicesButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// first remove previous service requests
				mP2PWifiManager.clearServiceRequests(mBeaconChannel, new ActionListener() {
					
					@Override
					public void onSuccess() {
						// bonjour
						final WifiP2pDnsSdServiceRequest bonjourSearchRequest = WifiP2pDnsSdServiceRequest.newInstance("Blaubot", BONJOUR_SERVICE_TYPE);
						mP2PWifiManager.addServiceRequest(mBeaconChannel, bonjourSearchRequest, new ActionListener() {
							
							@Override
							public void onSuccess() {
								Log.d(LOG_TAG, "Bonjour service search request added: " + bonjourSearchRequest);
								try {
									// android hardware .... 
									Thread.sleep(500);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								mP2PWifiManager.discoverServices(mBeaconChannel, new ActionListener() {
									@Override
									public void onSuccess() {
										Log.d(LOG_TAG, "Bonjour service discovery started");
									}
									
									@Override
									public void onFailure(int reason) {
										Log.d(LOG_TAG, "Failed to start Bonjour service discovery, reason: " + reason);
									}
								});
							}
							
							@Override
							public void onFailure(int reason) {
								Log.d(LOG_TAG, "Failed to add bonjour service search request: " + bonjourSearchRequest);
								Log.d(LOG_TAG, "Reason: " + reason);
							}
						});
						
					}
					
					@Override
					public void onFailure(int reason) {
						Log.w(LOG_TAG, "Failed to clear service requests");
					}
				});
				

			}
		});
		
		this.mDiscoverDevicesButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(mP2PWifiManager != null) {
					mDiscoverDevicesButton.setEnabled(false);
					final ProgressDialog dialog = ProgressDialog.show(WifiP2PActivity.this, "Discovering", "Discovering WifiP2P Devices - please wait ...", true);
					mP2PWifiManager.discoverPeers(mBeaconChannel, new ActionListener() {
						
						@Override
						public void onSuccess() {
							Log.d(LOG_TAG, "WifiP2pDiscovery successfully requested.");
							mDiscoverDevicesButton.setEnabled(true);
							dialog.dismiss();
						}
						
						@Override
						public void onFailure(int reason) {
							Log.w(LOG_TAG, "WifiP2pDiscovery failed.");
							if(reason == WifiP2pManager.ERROR) {
								Log.w(LOG_TAG, "WifiP2pDiscovery failed upon an internal error by the WifiAdapter.");
							} else if(reason == WifiP2pManager.BUSY) {
								Log.w(LOG_TAG, "WifiP2pDiscovery failed: The Adapter was busy.");
							} else if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
								Log.w(LOG_TAG, "WifiP2pDiscovery failed: WifiDirect is not supported by this device.");
							}
							mDiscoverDevicesButton.setEnabled(true);
							dialog.dismiss();
						}
					});
				}
			}
		});
		
		this.mPrintArpToLogButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				WifiP2PUtils.printARP();
			}
		});
		
		this.mLogInterfacesButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				WifiP2PUtils.logInterfacesAndIpAddresses();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		mBlaubot.registerReceivers(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		mBlaubot.unregisterReceivers(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

}
