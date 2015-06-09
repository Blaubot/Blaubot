package eu.hgross.blaubot.core.acceptor;


/**
 * Listener interface to retrieve the listening state of {@link IBlaubotConnectionAcceptor} objects. 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotListeningStateListener {
	/**
	 * Called when listening for incoming connections started
	 */
	public void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor);
	/**
	 * Called when listening for incoming connections stopped
	 */
	public void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor);
}
