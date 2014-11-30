package de.hsrm.blaubot.mock;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotListeningStateListener;

/**
 * Mockup object for acceptors.
 * You can mock a new incoming connection via mockNewConnection(...)
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotConnectionAcceptorMock implements IBlaubotConnectionAcceptor {
	private boolean started;
	private IBlaubotListeningStateListener listeningStateListener;
	private IBlaubotIncomingConnectionListener acceptorListener;
	private AdapterMock adapter;
	
	public BlaubotConnectionAcceptorMock(AdapterMock adapterMock) {
		this.adapter = adapterMock;
	}
	
	public void mockNewConnection(IBlaubotConnection connection) {
		if(this.acceptorListener != null)
			this.acceptorListener.onConnectionEstablished(connection);
	}
	
	@Override
	public void startListening() {
		if(started)
			stopListening();
		started = true;
		if(listeningStateListener != null)
			listeningStateListener.onListeningStarted(this);
	}

	@Override
	public void stopListening() {
		started = false;
		if(listeningStateListener !=null)
			listeningStateListener.onListeningStopped(this);
	}

	@Override
	public boolean isStarted() {
		return this.started;
	}

	@Override
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
		this.listeningStateListener = stateListener;
		
	}

	@Override
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
		this.acceptorListener = acceptorListener;
	}


}
