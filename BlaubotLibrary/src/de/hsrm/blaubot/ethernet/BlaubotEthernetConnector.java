package de.hsrm.blaubot.ethernet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.IBlaubotDevice;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;
import de.hsrm.blaubot.core.connector.IncompatibleBlaubotDeviceException;
import de.hsrm.blaubot.util.Log;

/**
 * Connector for ethernet
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotEthernetConnector implements IBlaubotConnector {
	private static final String LOG_TAG = "BlaubotEthernetConnector";
	private final BlaubotEthernetAdapter adapter;
	private IBlaubotIncomingConnectionListener incomingConnectionListener;

	public BlaubotEthernetConnector(BlaubotEthernetAdapter blaubotEthernetAdapter) {
		this.adapter = blaubotEthernetAdapter;
	}

	@Override
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener acceptorConnectorListener) {
		this.incomingConnectionListener = acceptorConnectorListener;
	}

	@Override
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) throws IncompatibleBlaubotDeviceException {
		if(!(blaubotDevice instanceof BlaubotEthernetDevice)) {
			throw new IncompatibleBlaubotDeviceException(blaubotDevice.getClass() + " is not a viable type for this connector.");
		}
		BlaubotEthernetDevice ethernetDevice = (BlaubotEthernetDevice) blaubotDevice;
		int remoteAcceptorPort = ethernetDevice.getAcceptorPort();
		InetAddress remoteAddress = ethernetDevice.getInetAddress();
		
		// connect
		Socket remoteSocket = null;
		try {
			remoteSocket = new Socket(remoteAddress, remoteAcceptorPort);
			// send acceptor port and beacon port
			BlaubotEthernetUtils.sendAcceptorAndBeaconPortsThroughSocket(remoteSocket, adapter);
			BlaubotEthernetConnection connection = new BlaubotEthernetConnection(ethernetDevice, remoteSocket);
			if(incomingConnectionListener != null) {
				incomingConnectionListener.onConnectionEstablished(connection);
			}
			return connection;
		} catch (IOException e) {
			if(Log.logErrorMessages()) {
				Log.e(LOG_TAG, "Could not connect or write to " + remoteAddress + " (" + e.getMessage() + ")");
			}
			if(remoteSocket != null) {
				try {
					remoteSocket.close();
				} catch (IOException e1) {
				}
			}
		}
		return null;
		
	}

	@Override
	public IBlaubotDevice createRemoteDevice(String uniqueId) {
		try {
			BlaubotEthernetDevice device = new BlaubotEthernetDevice(uniqueId, adapter);
			return device;
		} catch (UnknownHostException e) {
			Log.e(LOG_TAG, "Could not create remote device from uniqueId!", e);
			return null;
		}
	}

}
