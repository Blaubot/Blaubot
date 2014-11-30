package de.hsrm.blaubot.mock;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;
import de.hsrm.blaubot.core.connector.IncompatibleBlaubotDeviceException;

/**
 * Connector for debugging purposes.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotConnectorMock implements IBlaubotConnector {
	private IBlaubotIncomingConnectionListener incomingConnectionListener;
	private AdapterMock adapter;

	public BlaubotConnectorMock(AdapterMock adapter) {
		this.adapter = adapter;
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

	@Override
	public IBlaubotDevice createRemoteDevice(String uniqueId) {
		// TODO Auto-generated method stub
		return null;
	}

}
