package eu.hgross.blaubot.geobeacon;

/**
 */
public class GeoBeaconConstants {

    /**
     * The beacon server invalidates beacon messages after this amount of time (milliseconds).
     * Therefore the beacons will republish their state periodically, if 75% of this time passed
     * since their last updates.
     */
    public static final long MAX_AGE_BEACON_MESSAGES = 60000;
    public static final String GEO_BEACON_SERVER_UNIQUE_DEVICE_ID = "GeoBeaconServer";
}
