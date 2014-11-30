package de.hsrm.blaubot.protocol.handshake;

import de.hsrm.blaubot.core.IBlaubotConnection;

/**
 * Listener for the {@link ProtocolHandshakeTask}s.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IProtocolHandshakeListener {
	/**
	 * Called if the handshake succeeded without problems.
	 * @param shortDeviceId resulting short device ID
	 */
	public void onSuccess(short shortDeviceId);
	/**
	 * Called if the handshake failed an the connection can be considered broken.
	 * @param connection the connection in question
	 */
	public void onFailure(IBlaubotConnection connection);
}
