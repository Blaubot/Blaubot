package de.hsrm.blaubot.core.acceptor;

import de.hsrm.blaubot.core.IBlaubotConnection;

/**
 * Listener interface to be registered to {@link IBlaubotConnectionAcceptor} instances.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotIncomingConnectionListener {
	/**
	 * Called when a connection was established.
	 * @param connection
	 */
	public void onConnectionEstablished(IBlaubotConnection connection);
	
}
