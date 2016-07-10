package eu.hgross.blaubot;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import eu.hgross.blaubot.bluetooth.BluetoothActivity;
import eu.hgross.blaubot.bluetooth.BluetoothMulticastBeaconActivity;
import eu.hgross.blaubot.bluetooth.BluetoothNFCActivity;
import eu.hgross.blaubot.ethernet.EthernetActivity;
import eu.hgross.blaubot.ethernet.EthernetBonjourActivity;
import eu.hgross.blaubot.ethernet.EthernetNFCActivity;
import eu.hgross.blaubot.wifi.WifiApNFCActivity;
import eu.hgross.blaubot.wifip2p.WifiP2PActivity;

/**
 * Blaubot debug app main activity
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class MainActivity extends Activity {
	private Button mBluetoothButton;
    private Button mBluetoothWithMulticastBeaconButton;
    private Button mBluetoothWithNFCBeaconButton;
    private Button mWifiP2PButton;
    private Button mEthernetButton;
    private Button mEthernetWithNFCButton;
    private Button mEthernetWithBonjourButton;
    private Button mWifiApWithNfcButton;
    private Button mBlaubotConfiguratorButton;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		mBluetoothButton = (Button) findViewById(R.id.BluetoothButton);
        mBluetoothWithMulticastBeaconButton = (Button) findViewById(R.id.BluetoothWithMulticastBeaconButton);
        mBluetoothWithNFCBeaconButton = (Button) findViewById(R.id.BluetoothWithNFCBeaconButton);
		mWifiP2PButton = (Button) findViewById(R.id.WIFIDirectButton);
		mEthernetButton = (Button) findViewById(R.id.ethernetButton);
        mEthernetWithNFCButton = (Button) findViewById(R.id.ethernetWithNFCButton);
        mEthernetWithBonjourButton = (Button) findViewById(R.id.ethernetWithBonjourButton);
        mWifiApWithNfcButton = (Button) findViewById(R.id.WifiApWithNfcButton);
        mBlaubotConfiguratorButton = (Button) findViewById(R.id.blaubotConfiguratorButton);

        mBlaubotConfiguratorButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent btIntent = new Intent(MainActivity.this, BlaubotConfiguratorActivity.class);
                startActivity(btIntent);
            }
        });

		mBluetoothButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent btIntent = new Intent(MainActivity.this, BluetoothActivity.class);
				startActivity(btIntent);
			}
		});

        mBluetoothWithMulticastBeaconButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, BluetoothMulticastBeaconActivity.class);
                startActivity(i);
            }
        });

        mBluetoothWithNFCBeaconButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, BluetoothNFCActivity.class);
                startActivity(i);
            }
        });

		mWifiP2PButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent btIntent = new Intent(MainActivity.this, WifiP2PActivity.class);
				startActivity(btIntent);
			}
		});
		
		mEthernetButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent btIntent = new Intent(MainActivity.this, EthernetActivity.class);
				startActivity(btIntent);
			}
		});

        mEthernetWithNFCButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent btIntent = new Intent(MainActivity.this, EthernetNFCActivity.class);
                startActivity(btIntent);
            }
        });

        mEthernetWithBonjourButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent btIntent = new Intent(MainActivity.this, EthernetBonjourActivity.class);
                startActivity(btIntent);
            }
        });

        mWifiApWithNfcButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent btIntent = new Intent(MainActivity.this, WifiApNFCActivity.class);
                startActivity(btIntent);
            }
        });
	}
}
