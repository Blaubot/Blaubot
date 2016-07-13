package eu.hgross.blaubot.android.wifi;


import java.lang.reflect.Method;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiApUtil {
    private static final String LOG_TAG = "WifiApUtil";
    private static Method getWifiApState;
    private static Method isWifiApEnabled;
    private static Method setWifiApEnabled;
    private static Method getWifiApConfiguration;

    public static final String WIFI_AP_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED";

    public static final int WIFI_AP_STATE_DISABLED = WifiManager.WIFI_STATE_DISABLED;
    public static final int WIFI_AP_STATE_DISABLING = WifiManager.WIFI_STATE_DISABLING;
    public static final int WIFI_AP_STATE_ENABLED = WifiManager.WIFI_STATE_ENABLED;
    public static final int WIFI_AP_STATE_ENABLING = WifiManager.WIFI_STATE_ENABLING;
    public static final int WIFI_AP_STATE_FAILED = WifiManager.WIFI_STATE_UNKNOWN;

    public static final String EXTRA_PREVIOUS_WIFI_AP_STATE = WifiManager.EXTRA_PREVIOUS_WIFI_STATE;
    public static final String EXTRA_WIFI_AP_STATE = WifiManager.EXTRA_WIFI_STATE;

    static {
        // lookup methods and fields not defined publicly in the SDK.
        Class<?> cls = WifiManager.class;
        for (Method method : cls.getDeclaredMethods()) {
            String methodName = method.getName();
            if (methodName.equals("getWifiApState")) {
                getWifiApState = method;
            } else if (methodName.equals("isWifiApEnabled")) {
                isWifiApEnabled = method;
            } else if (methodName.equals("setWifiApEnabled")) {
                setWifiApEnabled = method;
            } else if (methodName.equals("getWifiApConfiguration")) {
                getWifiApConfiguration = method;
            }
        }
    }

    public static boolean isApSupported() {
        return (getWifiApState != null && isWifiApEnabled != null
                && setWifiApEnabled != null && getWifiApConfiguration != null);
    }

    private WifiManager mgr;

    private WifiApUtil(WifiManager mgr) {
        this.mgr = mgr;
    }

    /**
     *
     * @param mgr android's wifi manager service
     * @return instance or null, if ap mode is not supported
     */
    public static WifiApUtil createInstance(WifiManager mgr) {
        if (!isApSupported())
            return null;
        return new WifiApUtil(mgr);
    }

    public boolean isWifiApEnabled() {
        try {
            return (Boolean) isWifiApEnabled.invoke(mgr);
        } catch (Exception e) {
            Log.v(LOG_TAG, e.toString(), e); // shouldn't happen
            return false;
        }
    }

    public int getWifiApState() {
        try {
            return (Integer) getWifiApState.invoke(mgr);
        } catch (Exception e) {
            Log.v(LOG_TAG, e.toString(), e); // shouldn't happen
            return -1;
        }
    }

    public WifiConfiguration getWifiApConfiguration() {
        try {
            return (WifiConfiguration) getWifiApConfiguration.invoke(mgr);
        } catch (Exception e) {
            Log.v(LOG_TAG, e.toString(), e); // shouldn't happen
            return null;
        }
    }

    public boolean setWifiApEnabled(WifiConfiguration config, boolean enabled) {
        try {
            return (Boolean) setWifiApEnabled.invoke(mgr, config, enabled);
        } catch (Exception e) {
            Log.v(LOG_TAG, e.toString(), e); // shouldn't happen
            return false;
        }
    }

    private static final String[] WIFI_STATE_TEXTSTATE = new String[] {
            "DISABLING","DISABLED","ENABLING","ENABLED","FAILED"
    };

    /**
     * @param wifiState wifi state code
     * @return human readable string for the wifiState constant
     */
    public static String getTextForWifiState(int wifiState) {
        if(WIFI_STATE_TEXTSTATE.length > wifiState || wifiState < 0) {
            return "Unknown (" + wifiState + ")";
        }
        return WIFI_STATE_TEXTSTATE[wifiState];
    }

    /**
     * Gets the wifi state code state.
     * For human readable representations use getTextForWifiState(int)
     * @return the wifi state
     */
    public int getWifiAPState() {
        int state = -1;
        try {
            Method method2 = mgr.getClass().getMethod("getWifiApState");
            state = (Integer) method2.invoke(mgr);
        } catch (Exception e) {}
        Log.d(LOG_TAG, "getWifiAPState.state: " + getTextForWifiState(state));
        return state;
    }
}
