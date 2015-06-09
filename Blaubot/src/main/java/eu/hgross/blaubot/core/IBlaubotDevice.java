package eu.hgross.blaubot.core;

/**
 * Abstraction for blaubot devices.
 *
 * A blaubot device is in most cases a simple wrapper class above
 * a unique address specific to its related {@link IBlaubotAdapter}
 * uniquely identified by the {@link String} retrievable via it's
 * getUniqueDeviceID() method. In most cases this will be the
 * MAC address.   
 * 
 * Please implement the equals and hashcode methods properly. 
 * These methods should be bound to ONE blaubot instance (i.e. by
 * comparing the UUID or smthg else).
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotDevice extends Comparable<IBlaubotDevice> {
	/**
	 * Note that ; and | are reserved characters which are not allowed to be used as part of the uniqueId
	 * @return returns a String representing a unique id for this device (most probably a MAC or something similar)
	 */
	public String getUniqueDeviceID();
	
	/**
	 * @return some sort of readable name that (not necessarily uniquely) identifies this device 
	 */
	public String getReadableName();
}
