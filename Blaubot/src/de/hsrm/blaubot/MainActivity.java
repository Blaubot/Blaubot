package de.hsrm.blaubot;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import de.hsrm.blaubot.bluetooth.BluetoothActivity;
import de.hsrm.blaubot.ethernet.EthernetActivity;
import de.hsrm.blaubot.wifip2p.WifiP2PActivity;

/**
 * Blaubot bluetooth activity
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class MainActivity extends Activity {
	private Button mBluetoothButton;
	private Button mWifiP2PButton;
	private Button mEthernetButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		mBluetoothButton = (Button) findViewById(R.id.BluetoothButton);
		mWifiP2PButton = (Button) findViewById(R.id.WIFIDirectButton);
		mEthernetButton = (Button) findViewById(R.id.ethernetButton);
		
		mBluetoothButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent btIntent = new Intent(MainActivity.this, BluetoothActivity.class);
				startActivity(btIntent);
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
		
	}
}
