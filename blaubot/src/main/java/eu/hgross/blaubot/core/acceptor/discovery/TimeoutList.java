package eu.hgross.blaubot.core.acceptor.discovery;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.util.Log;

/**
 * Helper to determine if an object is alive or dead. Can be used for keepAlive purposes as well as 
 * a seen cache for SDP lookups or similar use cases.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class TimeoutList<T> {
    private static final String LOG_TAG = "TimeoutList";
    private static final boolean DO_LOG = false;
    private ExpiringMap<T, Long> devicesMap;

    /**
     * @param timeout the timeout after which a device is assumed to be dead
     */
    public TimeoutList(long timeout) {
        this.devicesMap = ExpiringMap.builder()
                .expiration(timeout, TimeUnit.MILLISECONDS)
                .expirationPolicy(ExpirationPolicy.CREATED)
                .build();
    }

    /**
     * Reports an item as alive.
     * 
     * @param item the item to be reported as alive
     */
    public void report(T item) {
        long seenAliveTimestamp = System.currentTimeMillis();
        if (DO_LOG && Log.logDebugMessages()) {
            Log.d(LOG_TAG, item + " reported");
        }
        Long prev = devicesMap.put(item, seenAliveTimestamp);
        if (prev != null && prev.equals(item)) {
            devicesMap.resetExpiration(item);
        }
    }

    /**
     * Removes an item from the alive list.
     * 
     * @param item the item that is no longer alive.
     */
    public void remove(T item) {
        if (DO_LOG && Log.logDebugMessages()) {
            Log.d(LOG_TAG, item + " removed");
        }
        devicesMap.remove(item);
    }

    /**
     * Checks whether an item is considered alive
     * 
     * @param item the item to check for
     * @return true, if item is alive
     */
    public boolean contains(T item) {
        return devicesMap.containsKey(item);
    }

    /**
     * Returns a list (copy) of alive items.
     * 
     * @return the list of alive items
     */
    public Set<T> getItems() {
        return new HashSet<>(devicesMap.keySet());
    }
}
