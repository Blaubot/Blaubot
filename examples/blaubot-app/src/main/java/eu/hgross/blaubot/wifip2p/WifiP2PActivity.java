package eu.hgross.blaubot.wifip2p;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import eu.hgross.blaubot.DemoConstants;
import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.android.views.DebugView;
import eu.hgross.blaubot.android.wifi.WifiUtils;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.R;

/**
 * Blaubot WifiP2P activity
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class WifiP2PActivity extends Activity {
	private static final String BONJOUR_SERVICE_TYPE = "_blaubot._tcp";
	private static final String LOG_TAG = "WifiP2PActivity";

    private DebugView mDebugView;

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
    private WifiManager.MulticastLock mMulticastLock;


    /*
        Example connect

        final WifiP2pManager wifiP2pManager = adapter.getWifiP2pManager();
        final Channel beaconWifiChannel = adapter.getBeaconWifiChannel();

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = srcDevice.deviceAddress;
        Log.d(LOG_TAG, "connecting to " + srcDevice + " ...");
        wifiP2pManager.connect(beaconWifiChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(LOG_TAG, "connect.onSuccess() - seems like we connected successfully to " + srcDevice);
            }

            @Override
            public void onFailure(int reason) {
                Log.d(LOG_TAG, "connect.onFailure(" + reason + ")");
            }
        });
     */

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wifip2p_activity);

		this.mP2PWifiManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        WifiManager wifi = (WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
        mMulticastLock = wifi.createMulticastLock("BlaubotMulticastLock");
        mMulticastLock.setReferenceCounted(true);
        mMulticastLock.acquire();
		this.mBeaconChannel = mP2PWifiManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.e(LOG_TAG, "onChannelDisconnected for beacon channel");
            }
        });
		this.mAcceptorChannel = mP2PWifiManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.e(LOG_TAG, "onChannelDisconnected for acceptor channel");
            }
        });
		this.mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		
		this.mBlaubot = BlaubotAndroidFactory.createWifiP2PBlaubot(DemoConstants.APP_UUID_WIFIDIRECT, mP2PWifiManager, mWifiManager, mBeaconChannel, mAcceptorChannel);
        this.mBlaubot.setContext(this);
		this.mDebugView = (DebugView) findViewById(R.id.wifip2pDebugView);
        this.mDebugView.registerBlaubotInstance(mBlaubot);
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
				txtRecordsMap.put("ID", mBlaubot.getOwnDevice().getUniqueDeviceID());
				WifiP2pServiceInfo bonjourServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(mBlaubot.getUuidSet().getBeaconUUID().toString(), BONJOUR_SERVICE_TYPE, txtRecordsMap );
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
				WifiUtils.printARP();
			}
		});
		
		this.mLogInterfacesButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				WifiUtils.logInterfacesAndIpAddresses();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		mBlaubot.registerReceivers(this);
        mBlaubot.onResume(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		mBlaubot.unregisterReceivers(this);
        mBlaubot.stopBlaubot();
        mMulticastLock.release();
	}

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mBlaubot.onNewIntent(intent);
    }

    @Override
    protected void onDestroy() {
        try {
            mBlaubot.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
	protected void onPause() {
		super.onPause();
        mBlaubot.onPause(this);
	}

}
