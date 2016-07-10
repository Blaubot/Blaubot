package eu.hgross.blaubot.fingertracking;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.UUID;

import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.android.views.StateView;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.LifecycleListenerAdapter;
import eu.hgross.blaubot.messaging.BlaubotChannelConfig;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.util.Log.LogLevel;

/**
 * Blaubot sample application to test latencies.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class FingerTrackingMainActivity extends Activity implements IBlaubotMessageListener {
    private static final UUID APP_UUID = UUID.fromString("c3c941e0-cfaf-11e3-9c1a-0800200c9a66");
    private static final String LOG_TAG = "FingerTrackingMainActivity";
    public static final int MIN_MESSAGE_RATE_DELAY = 10;

    private Blaubot mBlaubot;
    private FingerField mField;
    private BlauBotTickle mBlauBotTickle;
    private FingerTracker mFingerTracker;
    private StateView stateView;
    private Handler mUiHandler;

    /**
     * If the creation of blaubot fails due to an exception (no wifi or whatever)
     * the exception is stored here to be displayed to the user.
     */
    private Exception mFailException;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.LOG_LEVEL = LogLevel.WARNINGS;
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);
        BluetoothAdapter.getDefaultAdapter().enable();


        // Try to create blaubot or save the exception
        try {

//            this.mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubot(APP_UUID);
//            this.mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubotWithBluetoothAndNFCBeacon(APP_UUID);
//            this.mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubotWithNFCBeacon(APP_UUID);
            this.mBlaubot = BlaubotAndroidFactory.createEthernetBlaubot(APP_UUID);
//            this.mBlaubot = BlaubotAndroidFactory.createEthernetBlaubotWithBonjourBeacon(APP_UUID, 17171, 17172, BlaubotFactory.getLocalIpAddress());
        } catch (Exception e) {
            this.mFailException = e;
            e.printStackTrace();
            return;
        }


        final short channelId = (short) 1;
        final IBlaubotChannel channel = mBlaubot.createChannel(channelId);
        channel.getChannelConfig().setMessagePickerStrategy(BlaubotChannelConfig.MessagePickerStrategy.DISCARD_OLD);
        channel.getChannelConfig().setMessageRateLimit(MIN_MESSAGE_RATE_DELAY);

        mField = (FingerField) findViewById(R.id.fingerField);
        mField.setVisibility(View.INVISIBLE);
        mBlauBotTickle = new BlauBotTickle(mField);
        stateView = (StateView) findViewById(R.id.stateView);
        stateView.registerBlaubotInstance(mBlaubot);
        mUiHandler = new Handler(Looper.getMainLooper());
        mFingerTracker = new FingerTracker(channel, mField, FingerField.REFRESH_INTERVAL);

        channel.subscribe(FingerTrackingMainActivity.this);
        this.mBlaubot.addLifecycleListener(new LifecycleListenerAdapter() {
            @Override
            public void onConnected() {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mFingerTracker.activate();
                        mField.setVisibility(View.VISIBLE);

                    }
                });
            }

            @Override
            public void onDisconnected() {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mFingerTracker.deactivate();
                        mField.setVisibility(View.INVISIBLE);
                    }
                });
            }
        });
    }


    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(LOG_TAG, "LifeCycle.onNewIntent(" + intent + ")");
        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.onNewIntent(intent);
        }
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "LifeCycle.onResume");
        if (this.mBlaubot == null) {
            // stop
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.drawable.ic_stopped);
            builder.setTitle("Creation error");
            builder.setMessage("Could not create Blaubot. Is Wi-Fi/Bluetooth/... turned on? Message: " + mFailException.getMessage())
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            FingerTrackingMainActivity.this.finish();
                        }
                    });
            builder.create().show();
            super.onResume();
            return;
        }


        mFingerTracker.activate();
        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.setContext(this);
            blaubotAndroid.registerReceivers(this);
            blaubotAndroid.onResume(this);
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "LifeCycle.onPause");
        super.onPause();
        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.unregisterReceivers(this);
            blaubotAndroid.onResume(this);
        }
    }

    @Override
    protected void onStop() {
        Log.d(LOG_TAG, "LifeCycle.onStop");
        if (mBlaubot == null) {
            super.onStop();
            return;
        }

        mFingerTracker.deactivate();
        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.stopBlaubot();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "LifeCycle.onDestroy");
        super.onDestroy();
    }

    @Override
    public void onMessage(BlaubotMessage message) {
        byte[] payload = message.getPayload();
        String jsonString = new String(payload);
        mBlauBotTickle.onMessage(jsonString);
    }

}
