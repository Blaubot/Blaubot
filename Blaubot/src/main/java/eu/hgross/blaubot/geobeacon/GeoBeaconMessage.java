package eu.hgross.blaubot.geobeacon;

import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;

/**
 * Contains a BeaconMessage and the geo data
 */
public class GeoBeaconMessage {
    private String beaconUuid;
    private final BeaconMessage beaconMessage;
    private final GeoData geoData;

    public GeoBeaconMessage(BeaconMessage beaconMessage, GeoData geoData, String beaconUuid) {
        this.beaconMessage = beaconMessage;
        this.geoData = geoData;
        this.beaconUuid = beaconUuid;
    }

    public GeoBeaconMessage(GeoBeaconMessageDTO dto) {
        this.beaconMessage = dto.getBeaconMessage();
        this.geoData = dto.getGeoData();
        this.beaconUuid = dto.getBeaconUuid();
    }

    public BeaconMessage getBeaconMessage() {
        return beaconMessage;
    }

    public GeoData getGeoData() {
        return geoData;
    }

    public String getBeaconUuid() {
        return beaconUuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GeoBeaconMessage that = (GeoBeaconMessage) o;

        if (beaconUuid != null ? !beaconUuid.equals(that.beaconUuid) : that.beaconUuid != null)
            return false;
        if (beaconMessage != null ? !beaconMessage.getUniqueDeviceId().equals(that.beaconMessage.getUniqueDeviceId()) : that.beaconMessage != null)
            return false;
        return true;

    }

    @Override
    public int hashCode() {
        int result = beaconUuid != null ? beaconUuid.hashCode() : 0;
        result = 31 * result + (beaconMessage != null ? beaconMessage.getUniqueDeviceId().hashCode() : 0);
        return result;
    }
}
