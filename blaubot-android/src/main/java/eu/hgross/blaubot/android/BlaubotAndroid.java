package eu.hgross.blaubot.android;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.List;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotUUIDSet;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;

/**
 * The blaubot android implementation.
 *
 * Note that not all of the below life cycle methods are always required, since this
 * depends on the Beacons and Adapters you use. With the life cycle events below you
 * will be able to use ALL provided adapter and beacon implementations.
 *
 * Usage:
 *    1. onCreate()
 *          create a blaubot instance
 *    3. onResume():
 *          call blaubot.startBlaubot();
 *          call blaubot.registerReceivers(this);
 *          call blaubot.setContext(this);
 *          call blaubot.onResume(this) // if activity
 *    4. onPause()
 *          call blaubot.unregisterReceivers(this);
 *          call blaubot.onPause(this) // if activity
 *    5. onStop()
 *          call blaubot.stopBlaubot();
 *    6. onNewIntent(Intent intent)
 *          call blaubot.onNewIntent(intent);
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlaubotAndroid extends Blaubot {
	public BlaubotAndroid(IBlaubotDevice ownDevice, BlaubotUUIDSet uuidSet, List<IBlaubotAdapter> adapters, List<IBlaubotBeacon> beacons) {
		super(ownDevice, uuidSet, adapters, beacons);
	}


    /**
     * Used in blaubot components that need to be aware of the current lifecycle of foreground
     * activities (the NFC-Beacon, for example).
     *
     * @param activity the foreground activity
     */
    public void onPause(Activity activity) {
        ArrayList<Object> blaubotComponents = new ArrayList<>();
        blaubotComponents.addAll(getAdapters());
        blaubotComponents.addAll(getConnectionStateMachine().getBeaconService().getBeacons());
        for(Object component : blaubotComponents) {
            if (component instanceof IBlaubotAndroidComponent) {
                final IBlaubotAndroidComponent androidComponent = (IBlaubotAndroidComponent) component;
                androidComponent.onPause(activity);
            }
        }
    }

    /**
     * Used in blaubot components that need to be aware of the current lifecycle of the foreground activity
     * like the NFC beacon.
     *
     * @param activity the foreground activity
     */
    public void onResume(Activity activity) {
        ArrayList<Object> blaubotComponents = new ArrayList<>();
        blaubotComponents.addAll(getAdapters());
        blaubotComponents.addAll(getConnectionStateMachine().getBeaconService().getBeacons());
        for(Object component : blaubotComponents) {
            if (component instanceof IBlaubotAndroidComponent) {
                final IBlaubotAndroidComponent androidComponent = (IBlaubotAndroidComponent) component;
                androidComponent.onResume(activity);
            }
        }
    }

    /**
     * Used for blaubot components that need to react on incoming intents like the NFC-Beacon.
     * @param intent the new intent
     */
    public void onNewIntent(Intent intent) {
        ArrayList<Object> blaubotComponents = new ArrayList<>();
        blaubotComponents.addAll(getAdapters());
        blaubotComponents.addAll(getConnectionStateMachine().getBeaconService().getBeacons());
        for(Object component : blaubotComponents) {
            if (component instanceof IBlaubotAndroidComponent) {
                final IBlaubotAndroidComponent androidComponent = (IBlaubotAndroidComponent) component;
                androidComponent.onNewIntent(intent);
            }
        }
    }

    /**
     * Updates Blaubot components which need a context with the given context.
     * This method should be called as soon as the new context is available.
     * @param context the context to be updated
     */
    public void setContext(Context context) {
        ArrayList<Object> blaubotComponents = new ArrayList<>();
        blaubotComponents.addAll(getAdapters());
        blaubotComponents.addAll(getConnectionStateMachine().getBeaconService().getBeacons());
        for(Object component : blaubotComponents) {
            if (component instanceof IBlaubotAndroidComponent) {
                final IBlaubotAndroidComponent androidComponent = (IBlaubotAndroidComponent) component;
                androidComponent.setCurrentContext(context);
            }
        }
    }

	/**
	 * Registers the needed {@link BroadcastReceiver}s to the current context.
	 * This method is recommended to be called inside the onResume() method of
	 * an {@link Activity} or {@link Service}.
	 * 
	 * @param context the current android context
	 */
	public void registerReceivers(Context context) {
        ArrayList<Object> blaubotComponents = new ArrayList<>();
        blaubotComponents.addAll(getAdapters());
        blaubotComponents.addAll(getConnectionStateMachine().getBeaconService().getBeacons());

		for(Object component : blaubotComponents) {
			if(component instanceof IBlaubotBroadcastReceiver) {
				IBlaubotBroadcastReceiver blaubotBroadcastReceiver = (IBlaubotBroadcastReceiver) component;
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
	 * @param context the current android context
	 */
	public void unregisterReceivers(Context context) {
        ArrayList<Object> blaubotComponents = new ArrayList<>();
        blaubotComponents.addAll(getAdapters());
        blaubotComponents.addAll(getConnectionStateMachine().getBeaconService().getBeacons());

        for(Object component : blaubotComponents) {
            if(component instanceof IBlaubotBroadcastReceiver) {
				IBlaubotBroadcastReceiver blaubotBroadcastReceiver = (IBlaubotBroadcastReceiver) component;
				BroadcastReceiver receiver = blaubotBroadcastReceiver.getReceiver();
				context.unregisterReceiver(receiver);
			}
		}
	}



}
