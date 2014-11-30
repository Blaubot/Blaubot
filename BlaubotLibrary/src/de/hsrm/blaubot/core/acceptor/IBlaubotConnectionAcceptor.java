package de.hsrm.blaubot.core.acceptor;

/**
 * Defines an abstraction for any kind of connection accepting mechanisms.
 * This could be a TCP,UDP, BluetoothSocket ...
 * 
 * The state, whether the acceptor is listening for incoming connections or
 * not is populated through events send to the registered {@link IBlaubotListeningStateListener}.
 * 
 * If a client establishes a connection it will be populated through the
 * registered {@link IBlaubotIncomingConnectionListener}.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotConnectionAcceptor {
	public void startListening();
	public void stopListening();
	public boolean isStarted();
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener);
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener);
}
