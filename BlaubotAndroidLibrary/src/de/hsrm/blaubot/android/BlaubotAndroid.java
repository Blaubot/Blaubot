package de.hsrm.blaubot.android;

import java.util.List;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.IBlaubotAdapter;

/**
 * The blaubot android implementation.
 * 
 * Usage:
 *    1. Create a blaubot instance
 *    2. onResume() call blaubot.registerReceivers(this);
 *    3. onPause() call blaubot.unregisterReceivers(this);
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotAndroid extends Blaubot {
	protected BlaubotAndroid(List<IBlaubotAdapter> adapters) {
		super(adapters);
	}

	/**
	 * Registers the needed {@link BroadcastReceiver}s to the current context.
	 * This method is recommended to be called inside the onResume() method of
	 * an {@link Activity} or {@link Service}.
	 * 
	 * @param context
	 */
	public void registerReceivers(Context context) {
		for(IBlaubotAdapter adapter : getAdapters()) {
			if(adapter instanceof IBlaubotBroadcastReceiver) {
				IBlaubotBroadcastReceiver blaubotBroadcastReceiver = (IBlaubotBroadcastReceiver) adapter;
				IntentFilter filter = blaubotBroadcastReceiver.getIntentFilter();
				BroadcastReceiver receiver = blaubotBroadcastReceiver.getReceiver();
				context.registerReceiver(receiver, filter);
			}
		}
	}
	
	/**
	 * Unregisters the {@link BroadcastReceiver}s regsitered by registerReceivers.
	 * This method is recommended to be called inside the onPause() method of
	 * an {@link Activity} or {@link Service}.
	 * 
	 * @param context
	 */
	public void unregisterReceivers(Context context) {
		for(IBlaubotAdapter adapter : getAdapters()) {
			if(adapter instanceof IBlaubotBroadcastReceiver) {
				IBlaubotBroadcastReceiver blaubotBroadcastReceiver = (IBlaubotBroadcastReceiver) adapter;
				BroadcastReceiver receiver = blaubotBroadcastReceiver.getReceiver();
				context.unregisterReceiver(receiver);
			}
		}
	}
}
