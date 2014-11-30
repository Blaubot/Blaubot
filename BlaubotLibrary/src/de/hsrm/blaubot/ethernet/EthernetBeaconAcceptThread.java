package de.hsrm.blaubot.ethernet;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import de.hsrm.blaubot.util.Log;

/**
 * Accepting connections to the beacon and hands them to the {@link IBlaubotIncomingConnectionListener} which will
 * be the {@link BlaubotBeaconService}.
 * 
 * The accept thread - once started - will check if he is in charge using the {@link IEthernetBeacon}s getAcceptThread() method 
 * and kill himself, if he is obsolete.
 * 
 * The accept protocol is designed to be used with the {@link EthernetExchangeTask} to communicate some metadata to
 * create appropriate {@link BlaubotEthernetDevice} instances (requiring some more informations than the endpoint's ip).
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
class EthernetBeaconAcceptThread extends Thread {
	private static final String LOG_TAG = "EthernetBeaconAcceptThread";
	/**
	 * This is the timeout for read operations on the accepted client {@link Socket}s.
	 * If not set a crashed client would cause a resource blocking {@link Thread} without a chance
	 * to finish.
	 */
	private static final int SOCKET_TIMEOUT = 5000;
	private ServerSocket serverSocket;
	private final IEthernetBeacon ethernetBeacon;
	private final IBlaubotIncomingConnectionListener incomingConnectionListener;
	private ExecutorService executorService = Executors.newCachedThreadPool();
	
	/**
	 * @param incomingConnectionListener the listener to report to
	 * @param ethernetBeacon the {@link IEthernetBeacon} using this object
	 */
	public EthernetBeaconAcceptThread(IBlaubotIncomingConnectionListener incomingConnectionListener, IEthernetBeacon ethernetBeacon) {
		this.ethernetBeacon = ethernetBeacon;
		this.incomingConnectionListener = incomingConnectionListener;
	}
	
	@Override
	public void interrupt() {
		if(serverSocket != null) {
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Closing ServerSocket ...");
			}
			try {
				serverSocket.close();
			} catch (IOException e) {
				if (Log.logWarningMessages()) {
					Log.w(LOG_TAG, "Got IOException during close!");
				}
			}
			serverSocket = null;
		}
		super.interrupt();
	}

	@Override
	public void run() {
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "BeaconAcceptThread started ...");
		}
		ServerSocket lServerSocket = null;
		int beaconPort = ethernetBeacon.getEthernetAdapter().getBeaconPort();
		try {
			lServerSocket = new ServerSocket();
			lServerSocket.setReuseAddress(false);
			lServerSocket.bind(new InetSocketAddress(beaconPort));
			serverSocket = lServerSocket;
		} catch (BindException e1) {
			throw new RuntimeException(e1);
		} catch (IOException e1) {
			if (Log.logErrorMessages()) {
				Log.e(LOG_TAG, "Failed to create ServerSocket on port " +  beaconPort + ": " + e1.getMessage(), e1);
			}
			return;
		}
		// this is busy wait (for ~3 to ~5 iterations) - I can live with that.
		Socket clientSocket = null;
		while (!isInterrupted() && this.serverSocket != null && Thread.currentThread() == ethernetBeacon.getAcceptThread()) {
			try {
				if (Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Waiting for incoming beacon connections ...");
				}
				clientSocket = lServerSocket.accept();
			} catch (IOException e) {
				if (Log.logWarningMessages() && !lServerSocket.isClosed()) {
					Log.w(LOG_TAG, "Beacon communication failed with I/O Exception (could not accept() -> " + e.getMessage() + ")", e);
				}
				continue;
			}
			if (Log.logDebugMessages()) {
				Log.d(LOG_TAG, "Got a new beacon connection from " + clientSocket);
			}
			
			// Dispatch to executor service
			final Socket finalClientSocket = clientSocket;
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					try {
						// Set a timeout to avoid zombie threads @see SOCKET_TIMEOUT
						finalClientSocket.setSoTimeout(SOCKET_TIMEOUT);
					} catch (SocketException e) {
						if(Log.logErrorMessages()) {
							Log.e(LOG_TAG, "Failed to set socket timeout for incoming beacon client socket! This will cause a memory leak!");
						}
					}
					BlaubotEthernetConnection connection = BlaubotEthernetUtils.getEthernetConnectionFromSocket(finalClientSocket, ethernetBeacon.getEthernetAdapter());
					if(connection == null) {
						if (Log.logWarningMessages()) {
							Log.w(LOG_TAG, "Failed to create connection from incoming socket. Closed connection.");
						}
						return;
					}
					if (incomingConnectionListener != null) {
						incomingConnectionListener.onConnectionEstablished(connection);
					} else {
						if (Log.logWarningMessages()) {
							Log.w(LOG_TAG, "Got a beacon connection but no acceptor listener was there to handle it!");
						}
						connection.disconnect();
					}
				}
			});
		}
		if (Log.logDebugMessages()) {
			Log.d(LOG_TAG, "BeaconAcceptThread finished ...");
		}
		if(serverSocket != null) {
			if(!serverSocket.isClosed()) {
				try {
					serverSocket.close();
				} catch (IOException e) {
					if(Log.logErrorMessages()) {
						Log.e(LOG_TAG, "Could not close serverSocket! " + e.getMessage());
					}
				}
			}
		}
	}
}