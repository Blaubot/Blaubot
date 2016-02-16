package eu.hgross.blaubot.core.acceptor.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import eu.hgross.blaubot.util.Log;

/**
 * Helper to determine if an object is alive or dead. Can be used for keepAlive purposes as well as a seen cache for SDP lookups or similar use cases.
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 */
public class TimeoutList<T> {
    private static final String LOG_TAG = "TimeoutList";
    private static final boolean DO_LOG = false;
    private HashMap<T, Long> devicesMap;
    private Object monitor = new Object();
    private long timeout;

    /**
     * @param timeout the timeout after which a device is assumed to be dead
     */
    public TimeoutList(long timeout) {
        this.timeout = timeout;
        this.devicesMap = new HashMap<T, Long>();
    }

    public void report(T device) {
        long seenAliveTimestamp = System.currentTimeMillis();
        if (DO_LOG && Log.logDebugMessages()) {
            Log.d(LOG_TAG, device + " reported");
        }
        synchronized (monitor) {
            devicesMap.put(device, seenAliveTimestamp);
        }
    }

    public void report(T device, long seenAliveTimestamp) {
        if (DO_LOG && Log.logDebugMessages()) {
            Log.d(LOG_TAG, device + " reported");
        }
        synchronized (monitor) {
            devicesMap.put(device, seenAliveTimestamp);
        }
    }

    public void remove(T device) {
        if (DO_LOG && Log.logDebugMessages()) {
            Log.d(LOG_TAG, device + " removed");
        }
        synchronized (monitor) {
            devicesMap.remove(device);
        }
    }

    public boolean contains(T device) {
        purgeDead();
        return devicesMap.containsKey(device);
    }

    public Set<T> getItems() {
        purgeDead();
        synchronized (monitor) {
            return new HashSet<T>(devicesMap.keySet());
        }
    }

    private void purgeDead() {
        ArrayList<T> toRemove = new ArrayList<T>();
        long now = System.currentTimeMillis();
        synchronized (monitor) {
            for (T d : devicesMap.keySet()) {
                long lastSeen = devicesMap.get(d);
                if (now - lastSeen >= this.timeout) {
                    toRemove.add(d);
                }
            }
        }
        synchronized (monitor) {
            for (T d : toRemove) {
                if (DO_LOG && Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Purged " + d + " - not in timeout list anymore");
                }
                devicesMap.remove(d);
            }
        }
    }
}
