package eu.hgross.blaubot.geobeacon;

import java.io.IOException;

import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.util.Base64;

/**
 * DTO for the {@link GeoBeaconMessage}
 */
public class GeoBeaconMessageDTO {
    private GeoData geoData;
    private String beaconUuid;
    private String beaconMessage64;

    public GeoBeaconMessageDTO() {
    }

    public GeoBeaconMessageDTO(GeoBeaconMessage geoBeaconMessage) {
        this.beaconMessage64 = Base64.encodeBytes(geoBeaconMessage.getBeaconMessage().toBytes());
        this.geoData = geoBeaconMessage.getGeoData();
        this.beaconUuid = geoBeaconMessage.getBeaconUuid();
    }

    public String getBeaconMessage64() {
        return beaconMessage64;
    }

    public void setBeaconMessage64(String beaconMessage64) {
        this.beaconMessage64 = beaconMessage64;
    }

    /**
     * @return the beaconmessage deserialized from the base 64 attribute
     */
    public BeaconMessage getBeaconMessage() {
        try {
            return BeaconMessage.fromBytes(Base64.decode(beaconMessage64));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getBeaconUuid() {
        return beaconUuid;
    }

    public void setBeaconUuid(String beaconUuid) {
        this.beaconUuid = beaconUuid;
    }

    public GeoData getGeoData() {
        return geoData;
    }

    public void setGeoData(GeoData geoData) {
        this.geoData = geoData;
    }
}
