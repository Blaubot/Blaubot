package eu.hgross.blaubot.geobeacon;

/**
 * Generalized GeoData DTO
 */
public class GeoData {
    private double longitude;
    private double latitude;
    private float accuracy; // metres

    public GeoData() {

    }

    public GeoData(double latitude, double longitude, float accuracy) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GeoData geoData = (GeoData) o;

        if (Double.compare(geoData.longitude, longitude) != 0) return false;
        if (Double.compare(geoData.latitude, latitude) != 0) return false;
        return Float.compare(geoData.accuracy, accuracy) == 0;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(longitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(latitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (accuracy != +0.0f ? Float.floatToIntBits(accuracy) : 0);
        return result;
    }
}
