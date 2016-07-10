package eu.hgross.blaubot.android;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import eu.hgross.blaubot.core.IBlaubotAdapter;

/**
 * An android specific interface applicable to {@link IBlaubotAdapter}s indicating that
 * this adapter needs to receive {@link Intent}s via a {@link BroadcastReceiver}.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public interface IBlaubotBroadcastReceiver {
	/**
	 * @return the receiver that needs to be registered.
	 */
	public BroadcastReceiver getReceiver();
	
	/**
	 * @return the {@link IntentFilter} containing the intents needed by the {@link BroadcastReceiver} retrievable via getReceiver()
	 */
	public IntentFilter getIntentFilter();
}
