package eu.hgross.blaubot.mock;

import eu.hgross.blaubot.core.IBlaubotDevice;


/**
 * Mocks a blaubot device
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotDeviceMock implements IBlaubotDevice {
	private String uniqueId;

	public BlaubotDeviceMock(String uniqueId) {
		this.uniqueId = uniqueId;
	}
	
	@Override
	public String getUniqueDeviceID() {
		return uniqueId;
	}

	@Override
	public int compareTo(IBlaubotDevice o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getReadableName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((uniqueId == null) ? 0 : uniqueId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof BlaubotDeviceMock))
			return false;
		BlaubotDeviceMock other = (BlaubotDeviceMock) obj;
		if (uniqueId == null) {
			if (other.uniqueId != null)
				return false;
		} else if (!uniqueId.equals(other.uniqueId))
			return false;
		return true;
	}
}
