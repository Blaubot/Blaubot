package eu.hgross.blaubot.core;

import java.util.UUID;

/**
 * Generic IBlaubotDevice implementation, where the uniqueId can be freely chosen.
 */
public class BlaubotDevice implements IBlaubotDevice {
    protected String uniqueDeviceId;
    protected String readableName = null;

    public BlaubotDevice() {
        this(UUID.randomUUID().toString());
    }
    public BlaubotDevice(String uniqueId) {
        this.uniqueDeviceId = uniqueId;
    }

    @Override
    public String getUniqueDeviceID() {
        return uniqueDeviceId;
    }

    @Override
    public String getReadableName() {
        return readableName == null || readableName.isEmpty() ? uniqueDeviceId : readableName;
    }

    public void setReadableName(String readableName) {
        this.readableName = readableName;
    }

    @Override
    public int compareTo(IBlaubotDevice another) {
        return this.getUniqueDeviceID().compareTo(another.getUniqueDeviceID());
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BlaubotDevice{");
        sb.append("uniqueDeviceId='").append(uniqueDeviceId).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof IBlaubotDevice)) return false;
        return this.getUniqueDeviceID().equals(((IBlaubotDevice) o).getUniqueDeviceID());
    }

    @Override
    public int hashCode() {
        return uniqueDeviceId != null ? uniqueDeviceId.hashCode() : 0;
    }
}
