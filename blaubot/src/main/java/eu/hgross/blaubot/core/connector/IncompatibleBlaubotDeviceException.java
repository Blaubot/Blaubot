package eu.hgross.blaubot.core.connector;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class IncompatibleBlaubotDeviceException extends RuntimeException {
	private static final long serialVersionUID = -4144005078999508631L;

	public IncompatibleBlaubotDeviceException(String msg) {
		super(msg);
	}
}
