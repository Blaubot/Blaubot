package eu.hgross.blaubot.mock;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;

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
    private IBlaubotBeaconStore beaconStore;

    public BlaubotConnectionAcceptorMock(AdapterMock adapterMock) {
		this.adapter = adapterMock;
	}
	
	public void mockNewConnection(IBlaubotConnection connection) {
		if(this.acceptorListener != null)
			this.acceptorListener.onConnectionEstablished(connection);
	}

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
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

    @Override
    public ConnectionMetaDataDTO getConnectionMetaData() {
        return new ConnectionMetaDataDTO() {
            {
                getMetaData().put(CONNECTION_TYPE_KEY, "ACCEPTORMOCK");
            }
        };
    }


}
