package eu.hgross.blaubot.ethernet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.util.Log;

/**
 * Acceptor for ethernet
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotEthernetAcceptor implements IBlaubotConnectionAcceptor {
    private final IBlaubotDevice ownDevice;
    private final int acceptorPort;
    private final IBlaubotAdapter adapter;
    private final InetAddress ipAddress;
    private volatile EthernetAcceptThread acceptThread;
	private volatile IBlaubotListeningStateListener listeningStateListener;
	private volatile IBlaubotIncomingConnectionListener incomingConnectionListener;
	private final Object startStopMonitor;
    /**
     * Monitor to avoid two EthernetAcceptThread are executing at the same time on this instance.
     * (could happen on fast activate/deactivate calls)
     */
    private final Object acceptThreadLock = new Object();
    private IBlaubotBeaconStore beaconStore;

    public BlaubotEthernetAcceptor(IBlaubotAdapter adapter, IBlaubotDevice ownDevice, InetAddress ipAddress, int acceptorPort) {
        // TODO: remove ipAddress dependency here and get it from the ServerSocket
        this.adapter = adapter;
        this.ownDevice = ownDevice;
        this.ipAddress = ipAddress;
        this.acceptorPort = acceptorPort;
		this.startStopMonitor = new Object();
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
		synchronized (startStopMonitor) {
			if (acceptThread != null) {
                // stop thread and create new
				acceptThread.interrupt();
                acceptThread = null;
			}
			EthernetAcceptThread acceptThread = new EthernetAcceptThread();
            this.acceptThread = acceptThread;
            acceptThread.start();
		}
	}

	@Override
	public void stopListening() {
		synchronized (startStopMonitor) {
			if (acceptThread == null) {
				return;
			}
			this.acceptThread.interrupt();
            try {
                this.acceptThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.acceptThread = null;
		}
	}

	@Override
	public boolean isStarted() {
        synchronized (startStopMonitor) {
            return acceptThread != null;
        }
	}

	@Override
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
		this.listeningStateListener = stateListener;
	}

	@Override
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
		this.incomingConnectionListener = acceptorListener;
	}

    @Override
    public ConnectionMetaDataDTO getConnectionMetaData() {
        String ipStr = ipAddress.getHostAddress();
        final ConnectionMetaDataDTO connectionMetaDataDTO = new EthernetConnectionMetaDataDTO(ipStr, acceptorPort);
        return connectionMetaDataDTO;
    }

    private class EthernetAcceptThread extends Thread {
		private static final String LOG_TAG = "EthernetAcceptThread";
		private volatile ServerSocket serverSocket = null;

        public EthernetAcceptThread() {
            setName("ethernet-acceptor-accept-thread");
        }

		@Override
		public void interrupt() {
			super.interrupt();
			try {
				if(serverSocket != null) {
					serverSocket.close();
				}
			} catch (IOException e) {
				// nothing interesting
			}
		}

        @Override
        public void run() {
            synchronized (acceptThreadLock) {
                try {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Accept thread started, waiting for incoming connections ...");
                    }
                    ServerSocket serverSocket = new ServerSocket();
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress(acceptorPort));
                    this.serverSocket = serverSocket;
                    if (acceptThread != null && this == acceptThread) {
                        notify_listening_started();
                    }
                    final int connectionTimeout = adapter.getBlaubotAdapterConfig().getConnectionTimeout();
                    while (!isInterrupted() && acceptThread == Thread.currentThread()) {
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout(connectionTimeout);
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Got new client connection from " + socket.getInetAddress().toString());
                        }

                        BlaubotEthernetConnection connection = BlaubotEthernetUtils.getEthernetConnectionFromSocket(socket);
                        if (connection == null) {
                            if (Log.logWarningMessages()) {
                                Log.w(LOG_TAG, "Failed to create connection from incoming socket. Closing connection.");
                            }
                            continue;
                        }

                        // retrieve their beacon message with their state and most importantly their acceptor meta data
                        final BeaconMessage theirBeaconMessage = BeaconMessage.fromBlaubotConnection(connection);
                        beaconStore.putDiscoveryEvent(theirBeaconMessage, connection.getRemoteDevice());


                        if (incomingConnectionListener != null) {
                            incomingConnectionListener.onConnectionEstablished(connection);
                        }
                    }
                    serverSocket.close();
                } catch (IOException e) {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        if (Log.logErrorMessages()) {
                            Log.e(LOG_TAG, "Socket I/O failed (" + e.getMessage() + ")", e);
                        }
                    }

                } finally {
                    if (this.serverSocket != null) {
                        try {
                            serverSocket.close();
                        } catch (IOException e) {

                        } finally {
                            serverSocket = null;
                        }
                    }
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "Acceptors accept thread stopped ...");
                    }
                    // only notify, if we are the thread in action
                    notify_listening_stopped();
                }
            }
        }
    }

	private void notify_listening_stopped() {
		if (listeningStateListener != null)
			listeningStateListener.onListeningStopped(this);
	}

	private void notify_listening_started() {
		if (listeningStateListener != null)
			listeningStateListener.onListeningStarted(this);
	}

}
