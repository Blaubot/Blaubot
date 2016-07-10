package eu.hgross.blaubot.android.wifip2p;

import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.connector.IncompatibleBlaubotDeviceException;

/**
 * WIFIDirect connector implementation for android.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BlaubotWifiP2PConnector implements IBlaubotConnector {
	private BlaubotWifiP2PAdapter adapter;
	private IBlaubotIncomingConnectionListener incomingConnectionListener;
    private IBlaubotBeaconStore beaconStore;

    protected BlaubotWifiP2PConnector(BlaubotWifiP2PAdapter blaubotWifiP2PAdapter) {
		this.adapter = blaubotWifiP2PAdapter;
	}

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

	@Override
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener acceptorConnectorListener) {
		this.incomingConnectionListener = acceptorConnectorListener;
	}

	@Override
	public IBlaubotConnection connectToBlaubotDevice(final IBlaubotDevice blaubotDevice) throws IncompatibleBlaubotDeviceException {
		if(!(blaubotDevice instanceof BlaubotWifiP2PDevice))
			throw new IncompatibleBlaubotDeviceException("Adapter is not compatible with " + blaubotDevice.getClass().toString() + " isntances.");
		final CountDownLatch latch = new CountDownLatch(1);
		BlaubotWifiP2PDevice wifiBlaubotDevice = (BlaubotWifiP2PDevice) blaubotDevice;
		WifiP2pConfig config = new WifiP2pConfig();
		config.deviceAddress = wifiBlaubotDevice.getUniqueDeviceID();
		
		final List<IBlaubotConnection> sharedMemory = new ArrayList<IBlaubotConnection>(1);
		adapter.getWifiP2pManager().connect(adapter.getAcceptorWifiChannel(), config, new ActionListener() {
			
			@Override
			public void onSuccess() {
				System.out.println("CONNECTED");
				// TODO: Open socket?
				// TODO: create connection object
//				IBlaubotConnection conn = BlaubotWifiP2PConnection.fromSocket(blaubotDevice, clientSocket, adapter.getUuidSet());
//				adapter.getWifiP2pManager().
//				sharedMemory.add(conn);
				latch.countDown();
			}
			
			@Override
			public void onFailure(int reason) {
				System.out.println("CONNECTION FAILED");
				latch.countDown();
			}
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {
			return null;
		}
		return sharedMemory.isEmpty() ? null : sharedMemory.get(0);
	}

    @Override
    public List<String> getSupportedAcceptorTypes() {
        // TODO create a dto
        return null;
    }

}
