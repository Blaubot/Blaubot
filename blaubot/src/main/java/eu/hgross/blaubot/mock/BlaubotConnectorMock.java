package eu.hgross.blaubot.mock;

import java.util.Arrays;
import java.util.List;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.connector.IncompatibleBlaubotDeviceException;

/**
 * Connector for debugging purposes.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotConnectorMock implements IBlaubotConnector {
	private IBlaubotIncomingConnectionListener incomingConnectionListener;
	private AdapterMock adapter;
    private IBlaubotBeaconStore beaconStore;

    public BlaubotConnectorMock(AdapterMock adapter) {
		this.adapter = adapter;
	}

    @Override
    public List<String> getSupportedAcceptorTypes() {
        return Arrays.asList("MockAcceptor");
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }
	/**
	 * Mock a new connection by directly injecting it via this connector
	 * @param connection the connection to be mocked
	 * @param connectionAcceptorMock the mocked acceptor
	 * @return
	 */
	public boolean mockConnectToDevice(IBlaubotConnection connection, BlaubotConnectionAcceptorMock connectionAcceptorMock) {
		connectionAcceptorMock.mockNewConnection(connection);
		
		this.incomingConnectionListener.onConnectionEstablished(connection);
		return true;
	}

	@Override
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener acceptorConnectorListener) {
		this.incomingConnectionListener = acceptorConnectorListener;
	}

	@Override
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) throws IncompatibleBlaubotDeviceException {
		// TODO Auto-generated method stub
		return null;
	}

}
