package eu.hgross.blaubot.geobeacon;

import com.google.gson.Gson;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Contains helper methods for the geo beacon.
 */
public class GeoBeaconUtil {
    private static final Gson gson = new Gson();

    /**
     * Wraps a geo beacon message into a blaubot message
     *
     * @param message the message
     * @return the blaubot message
     */
    public static BlaubotMessage geoBeaconMessageToBlaubotMessage(GeoBeaconMessage message) {
        GeoBeaconMessageDTO dto = new GeoBeaconMessageDTO(message);
        byte[] jsonBytes = gson.toJson(dto).getBytes(BlaubotConstants.STRING_CHARSET);

        // wrap it and send
        BlaubotMessage msg = new BlaubotMessage();
        msg.setPayload(jsonBytes);
        return msg;
    }

    public static GeoBeaconMessage blaubotMessageToGeoBeaconMessage(BlaubotMessage msg) {
        final byte[] payload = msg.getPayload();
        final String jsonData = new String(payload, BlaubotConstants.STRING_CHARSET);
        final GeoBeaconMessageDTO geoBeaconMessageDTO = gson.fromJson(jsonData, GeoBeaconMessageDTO.class);
        final GeoBeaconMessage geoBeaconMessage = new GeoBeaconMessage(geoBeaconMessageDTO);
        return geoBeaconMessage;
    }

    /**
     * Calculates the distance between two coordinates in KM using the harvesine formula.
     *
     * @param geoData1 containing lat and lon
     * @param geoData2 containing lat and lon
     * @return the distance in km
     */
    public static double distanceBetweenGeoBeaconMessages(GeoData geoData1, GeoData geoData2) {
        // conver to radian values
        double lat1 = geoData1.getLatitude() * Math.PI / 180d;
        double lon1 = geoData1.getLongitude() * Math.PI / 180d;
        double lat2 = geoData2.getLatitude() * Math.PI / 180d;
        double lon2 = geoData2.getLongitude() * Math.PI / 180d;

        // diffs in x and y
        double latDiff = lat2 - lat1;
        double lonDiff = lon2 - lon1;

        // harversine
        double a = Math.sin(latDiff / 2d) * Math.sin(latDiff / 2d) + Math.cos(lat1)
                * Math.cos(lat2) * Math.sin(lonDiff / 2d) * Math.sin(lonDiff / 2d);
        double c =  2d * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c;
        return distance;
    }

    /**
     * Earth's diameter in KM
     */
    private static final double R = 6371d;

    
    public static void main(String[] args) {
        GeoData dortmund = new GeoData(51.512054, 7.463573, 1);
        GeoData berlin = new GeoData(52.523403, 13.411400, 1);
        System.out.println("Dortmund -> Berlin (should be 422,11 KM): " + distanceBetweenGeoBeaconMessages(dortmund, berlin));
    }

}
