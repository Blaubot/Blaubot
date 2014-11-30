package de.hsrm.blaubot.ethernet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import de.hsrm.blaubot.core.acceptor.IBlaubotListeningStateListener;
import de.hsrm.blaubot.util.Log;

/**
 * Acceptor for ethernet
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotEthernetAcceptor implements IBlaubotConnectionAcceptor {
	private EthernetAcceptThread acceptThread;
	private final int acceptorPort;
	private volatile IBlaubotListeningStateListener listeningStateListener;
	private volatile IBlaubotIncomingConnectionListener incomingConnectionListener;
	private BlaubotEthernetAdapter adapter;
	private Object startStopMonitor;

	public BlaubotEthernetAcceptor(BlaubotEthernetAdapter blaubotEthernetAdapter, int acceptorPort) {
		this.adapter = blaubotEthernetAdapter;
		this.acceptorPort = acceptorPort;
		this.startStopMonitor = new Object();
	}

	@Override
	public void startListening() {
		synchronized (startStopMonitor) {
			if (isStarted()) {
				return;
			}
			EthernetAcceptThread acceptThread = new EthernetAcceptThread();
			acceptThread.start();
			this.acceptThread = acceptThread;
		}
	}

	@Override
	public void stopListening() {
		synchronized (startStopMonitor) {
			if (!isStarted()) {
				return;
			}
			this.acceptThread.interrupt();
		}
	}

	@Override
	public boolean isStarted() {
		return acceptThread != null && acceptThread.isAlive();
	}

	@Override
	public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
		this.listeningStateListener = stateListener;
	}

	@Override
	public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
		this.incomingConnectionListener = acceptorListener;
	}

	protected int getAcceptorPort() {
		return acceptorPort;
	}

	private class EthernetAcceptThread extends Thread {
		private static final String LOG_TAG = "EthernetAcceptThread";
		private volatile ServerSocket serverSocket = null;

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
			try {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Waiting for incoming connections ...");
				}
				ServerSocket serverSocket = new ServerSocket();
				serverSocket.setReuseAddress(false);
				serverSocket.bind(new InetSocketAddress(acceptorPort));
				this.serverSocket = serverSocket;
				notify_listening_started();
				while (!isInterrupted() && acceptThread == Thread.currentThread()) {
					Socket socket = serverSocket.accept();
					if(Log.logDebugMessages()) {
						Log.d(LOG_TAG, "Got new client connection from " + socket.getInetAddress().toString());
					}
					BlaubotEthernetConnection connection = BlaubotEthernetUtils.getEthernetConnectionFromSocket(socket, adapter);
					if(connection == null) {
						if(Log.logWarningMessages()) {
							Log.w(LOG_TAG, "Failed to create connection from incoming socket. Closing connection.");
						}
						continue;
					}
					if (incomingConnectionListener != null) {
						incomingConnectionListener.onConnectionEstablished(connection);
					}
				}
				serverSocket.close();
			} catch (IOException e) {
				if(serverSocket != null && !serverSocket.isClosed()) {
					if(Log.logErrorMessages()) {
						Log.e(LOG_TAG, "Socket I/O failed (" + e.getMessage() + ")", e);
					}
				}
				
			} finally {
				if (this.serverSocket != null) {
					if (!serverSocket.isClosed()) {
						try {
							serverSocket.close();
						} catch (IOException e) {
						}
						serverSocket = null;
					}
				}
				notify_listening_stopped();
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
