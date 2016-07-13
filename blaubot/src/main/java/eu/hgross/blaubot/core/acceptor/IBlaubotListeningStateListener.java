package eu.hgross.blaubot.core.acceptor;


/**
 * Listener interface to retrieve the listening state of {@link IBlaubotConnectionAcceptor} objects. 
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public interface IBlaubotListeningStateListener {
	/**
	 * Called when listening for incoming connections started
     * 
     * @param connectionAcceptor the acceptor that just started listening
	 */
	void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor);
	
	/**
	 * Called when listening for incoming connections stopped
     * 
     * @param connectionAcceptor the acceptor that just stopped listening for connections
	 */
	void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor);
}
