package eu.hgross.blaubot.android.geo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import eu.hgross.blaubot.android.IBlaubotAndroidComponent;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.geobeacon.GeoData;
import eu.hgross.blaubot.geobeacon.GeoLocationBeacon;

/**
 * Android implementation of the GeoLocationBeacon.
 * Needs permission android.permission.ACCESS_FINE_LOCATION
 */
public class GeoLocationBeaconAndroid extends GeoLocationBeacon implements LocationListener, IBlaubotAndroidComponent {
    private Context mCurrentContext;
    private Location mLastKnownLocation;

    /**
     * @param beaconStore The beacon store holding the connection meta data for the given connectors to connect to the GeoBeaconServer's acceptors.
     * @param connectors  connectors to be used to establish a connection to the beacon server
     */
    public GeoLocationBeaconAndroid(IBlaubotBeaconStore beaconStore, IBlaubotConnector... connectors) {
        super(beaconStore, connectors);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mLastKnownLocation != null) {
            boolean betterLocation = isBetterLocation(location, mLastKnownLocation);
            if (!betterLocation) {
                return; // ignore
            }
        }
        mLastKnownLocation = location;
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        float accuracy = location.getAccuracy();
        GeoData geoData = new GeoData(latitude, longitude, accuracy);
        setGeoData(geoData);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void setCurrentContext(Context context) {
        if (context == null && mCurrentContext != null) {
            // deregister if anything is registered
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            locationManager.removeUpdates(this);
        }
        mCurrentContext = context;
        if (context != null) {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);

            // ask for last location
            Location lastGpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);


            // get the better of the two
            Location lastKnownLocation = null;
            if (lastGpsLocation != null && lastNetworkLocation == null) {
                lastKnownLocation = lastGpsLocation;
            } else if (lastGpsLocation == null && lastNetworkLocation != null) {
                lastKnownLocation = lastNetworkLocation;
            } else if (lastKnownLocation != null && lastGpsLocation != null) {
                lastKnownLocation = isBetterLocation(lastNetworkLocation, lastGpsLocation) ? lastNetworkLocation : lastGpsLocation;
            } // else -> both null

            // compare with our current location, if any
            if (lastKnownLocation != null) {
                if (mLastKnownLocation != null) {
                    mLastKnownLocation = isBetterLocation(lastKnownLocation, mLastKnownLocation) ? lastKnownLocation : mLastKnownLocation;
                } else {
                    // the location is better -> trigger the listener
                    onLocationChanged(lastKnownLocation);
                }
            }

        }
    }

    @Override
    public void onResume(Activity context) {
    }

    @Override
    public void onPause(Activity context) {

    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    /*
     * Yay... Boilerplate code to copy again ...
     */


    private static final int TWO_MINUTES = 1000 * 60 * 2;

    /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to compare the new one
     * @return true, iff location is more accurate than currentBestLocation 
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }
}
