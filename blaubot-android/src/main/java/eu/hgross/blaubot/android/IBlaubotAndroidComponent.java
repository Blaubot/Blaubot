package eu.hgross.blaubot.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * This interface can be attached to Beacons or Adapters.
 * If present, the beacons/adapters will get updates on Context changes (Activities).
 */
public interface IBlaubotAndroidComponent {

    /**
     * Gets called if the android context changes (onResume).
     * @param context the context
     */
    public void setCurrentContext(Context context);

    /**
     * Gets called when onResume was called on the foreground activity
     * @param context the foregroudn activity
     */
    public void onResume(Activity context);

    /**
     * Gets called when onPause was called on the foreground activity
     * @param context the current android context
     */
    public void onPause(Activity context);

    /**
     * Gets called when a new intent was received on the foreground activity
     * @param intent the new intent
     */
    public void onNewIntent(Intent intent);
}
