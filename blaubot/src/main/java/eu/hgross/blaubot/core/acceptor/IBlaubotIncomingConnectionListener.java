package eu.hgross.blaubot.core.acceptor;

import eu.hgross.blaubot.core.IBlaubotConnection;

/**
 * Listener interface to be registered to {@link IBlaubotConnectionAcceptor} instances.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public interface IBlaubotIncomingConnectionListener {
	/**
	 * Called when a connection was established.
	 * 
	 * @param connection the newly established connection
	 */
	void onConnectionEstablished(IBlaubotConnection connection);
	
}
