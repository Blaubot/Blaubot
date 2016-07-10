package eu.hgross.blaubot.wifi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.Button;

import eu.hgross.blaubot.DemoConstants;
import eu.hgross.blaubot.R;
import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.android.views.DebugView;
import eu.hgross.blaubot.android.wifi.WifiApUtil;
import eu.hgross.blaubot.util.Log;

/**
 * Blaubot wifi ap activity with a nfc beacon
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class WifiApNFCActivity extends Activity {
    private static final String LOG_TAG = "WifiApNFCAcitivty";

    private BlaubotAndroid mBlaubot;
    private Button mTestButton;
    private DebugView mDebugView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "LifeCycle.onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifiap_nfc_activity);
        WifiManager wifiManager = (WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        this.mDebugView = (DebugView) findViewById(R.id.debugView);
//        this.mTestButton = (Button) findViewById(R.id.ap_testButton);
        mBlaubot = BlaubotAndroidFactory.createWifiApWithNfcBeaconBlaubot(DemoConstants.APP_UUID_WIFIAP_NFC, connectivityManager, wifiManager, 16666);
        Log.d(LOG_TAG, "Blaubot instance created.");
//        mTestButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                turnOnOffHotspot(WifiApNFCActivity.this, true);
//            }
//        });

        mDebugView.registerBlaubotInstance(mBlaubot);
    }

    public static void turnOnOffHotspot(Context context, boolean activate) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiApUtil apUtil = WifiApUtil.createInstance(wifiManager);
        if (apUtil != null) {
            final WifiConfiguration wifiApConfiguration = apUtil.getWifiApConfiguration();
            Log.d(LOG_TAG, wifiApConfiguration + "");
            Log.d(LOG_TAG, "ssid:" + wifiApConfiguration.SSID + ",psk:" + wifiApConfiguration.preSharedKey);
            apUtil.setWifiApEnabled(wifiApConfiguration, activate);
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
