package de.hsrm.blaubot.junit;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.mock.BlaubotConnectionMock;
import de.hsrm.blaubot.mock.BlaubotConnectionQueueMock;
import de.hsrm.blaubot.mock.BlaubotDeviceMock;
import de.hsrm.blaubot.protocol.ProtocolContext;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.util.Log;

public class ProtocolTestHelper {

	private final int startPort;
	private static final String TAG = "ProtocolTestHelper";
	private final int BARRIER_TIMEOUT = 1000;
	
	private ExecutorService executorService = Executors.newCachedThreadPool();
	private ArrayList<Socket> clientSockets;
	private ArrayList<Socket> serverSockets;
	private ProtocolManager masterProtocolManager;
	private List<ProtocolManager> clientProtocolManagers;
	private List<IBlaubotConnection> masterConnections;
	private List<IBlaubotConnection> clientConnections;
	private String masterID;
	private CyclicBarrier socketBarrier;

	public ProtocolTestHelper(int startPort, String masterID) {
		this.startPort = startPort;
		this.masterID = masterID;
		this.clientProtocolManagers = new ArrayList<ProtocolManager>();
		this.serverSockets = new ArrayList<Socket>();
		this.clientSockets = new ArrayList<Socket>();
		this.masterConnections = new ArrayList<IBlaubotConnection>();
		this.clientConnections = new ArrayList<IBlaubotConnection>();
		this.socketBarrier = new CyclicBarrier(2);
	}

	public ProtocolManager getMasterProtocolManager() {
		return masterProtocolManager;
	}

	public List<ProtocolManager> getClientProtocolManagers() {
		return clientProtocolManagers;
	}

	public List<IBlaubotConnection> getMasterConnections() {
		return masterConnections;
	}

	public List<IBlaubotConnection> getClientConnections() {
		return clientConnections;
	}
	
	public void createAndConnectMockNetwork(int size) throws IOException, InterruptedException, BrokenBarrierException, TimeoutException{
		createMockNetwork(size);
		connectMasterAndClients(this.masterProtocolManager, this.clientProtocolManagers);
	}

	public void createMockNetwork(int size) {
		if (Log.logDebugMessages())
			Log.d(TAG, "creating network");

		int clientCount = size - 1;

		// create master
		this.masterProtocolManager = createMaster();

		// create clients
		for (int i = 0; i < clientCount; i++) {
			String ownUniqueDeviceID = "deviceID#" + i;
			ProtocolManager client = createClient(ownUniqueDeviceID);
			this.clientProtocolManagers.add(client);
			createMockConnection(ownUniqueDeviceID);
		}

		if (Log.logDebugMessages())
			Log.d(TAG, String.format("network created: 1 master and %d clients", this.clientProtocolManagers.size()));
	}

	private void createMockConnection(String clientID) {
		// connection mock from master to client
		BlaubotDeviceMock clientDevice = new BlaubotDeviceMock(clientID);
		BlaubotConnectionQueueMock masterConnection = new BlaubotConnectionQueueMock(clientDevice);
		
		// connection mock from client to master
		BlaubotDeviceMock masterDevice = new BlaubotDeviceMock(this.masterID);
		BlaubotConnectionQueueMock clientConnection = masterConnection.getOtherEndpointConnection(masterDevice);

		this.masterConnections.add(masterConnection);
		this.clientConnections.add(clientConnection);
	}

	/**
	 * use this method in order to create a network of 1 master and n-1 clients
	 * 
	 * @param size
	 *            network size (1 master & size - 1 clients)
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws BrokenBarrierException
	 * @throws TimeoutException
	 */
	public void createSocketNetwork(int size) throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		createSockets(size);
		if (Log.logDebugMessages())
			Log.d(TAG, "creating network");

		int clientCount = size - 1;

		// create master
		this.masterProtocolManager = createMaster();

		// create clients
		for (int i = 0; i < clientCount; i++) {
			String ownUniqueDeviceID = "deviceID#" + i;
			ProtocolManager client = createClient(ownUniqueDeviceID);
			this.clientProtocolManagers.add(client);
			createSocketConnection(ownUniqueDeviceID);

		}

		if (Log.logDebugMessages())
			Log.d(TAG, String.format("network created: 1 master and %d clients", this.clientProtocolManagers.size()));
	}

	/**
	 * use this method in order to create a network of the given size and
	 * connect all clients to the master by adding the correspondig connections
	 * to their protocol managers
	 * 
	 * @param size
	 * @throws TimeoutException
	 * @throws BrokenBarrierException
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public void createAndConnectSocketNetwork(int size) throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		createSocketNetwork(size);
		connectMasterAndClients(this.masterProtocolManager, this.clientProtocolManagers);
	}

	private void createSockets(int count) throws IOException, InterruptedException, BrokenBarrierException, TimeoutException {
		final int socketCount = count - 1;
		final CountDownLatch socketLatch = new CountDownLatch(socketCount);

		if (Log.logDebugMessages())
			Log.d(TAG, String.format("creating %d sockets", socketCount));

		executorService.submit(new Runnable() {

			@Override
			public void run() {
				int acceptedSocketsCount = 0;
				while (acceptedSocketsCount < socketCount && !Thread.currentThread().isInterrupted()) {
					try {
						int port = startPort + acceptedSocketsCount;
						// don't worry: socket is closed by wrapping connection
						// mock (see "disconnect()")
						@SuppressWarnings("resource")
						ServerSocket serverSocket = new ServerSocket(port);
						ProtocolTestHelper.this.socketBarrier.await(BARRIER_TIMEOUT, TimeUnit.MILLISECONDS);
						Socket acceptedSocket = serverSocket.accept();
						// force port reuse (see
						// http://stackoverflow.com/questions/13838256/java-closing-a-serversocket-and-opening-up-the-port)
						acceptedSocket.setReuseAddress(true);
						serverSockets.add(acceptedSocket);
						acceptedSocketsCount += 1;
						if (Log.logDebugMessages())
							Log.d(TAG, "accepted socket number " + acceptedSocketsCount);
						socketLatch.countDown();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (BrokenBarrierException e) {
						e.printStackTrace();
					} catch (TimeoutException e) {
						e.printStackTrace();
					}
				}
			}
		});

		for (int i = 0; i < socketCount; i++) {
			this.socketBarrier.await(BARRIER_TIMEOUT, TimeUnit.MILLISECONDS);
			Socket socket = new Socket("localhost", startPort + i);
			socket.setReuseAddress(true);
			clientSockets.add(socket);
			// use barrier in order to sync server and client threads
		}

		socketLatch.await();
		if (Log.logDebugMessages())
			Log.d(TAG, String.format("%2d sockets created", socketCount));
	}

	protected void createSocketConnection(String clientID) {
		Socket serverSocket = this.serverSockets.remove(0);
		Socket clientSocket = this.clientSockets.remove(0);
		BlaubotConnectionMock clientConnection = createConnectionMock(this.masterID, clientSocket);
		BlaubotConnectionMock masterConnection = createConnectionMock(clientID, serverSocket);

		this.masterConnections.add(masterConnection);
		this.clientConnections.add(clientConnection);

	}

	protected void connectMasterAndClients(ProtocolManager master, List<ProtocolManager> clientProtocolManagers) {
		for (int i = 0; i < clientProtocolManagers.size(); i++) {
			ProtocolManager client = clientProtocolManagers.get(i);

			IBlaubotConnection masterConnection = this.masterConnections.get(i);
			IBlaubotConnection clientConnection = this.clientConnections.get(i);

			master.addConnection(masterConnection);
			client.addConnection(clientConnection);
		}
	}

	private ProtocolManager createMaster() {
		ProtocolContext context = new ProtocolContext(this.masterID);
		this.masterProtocolManager = new ProtocolManager(context);
		this.masterProtocolManager.activate();
		this.masterProtocolManager.setMaster(true);

		return this.masterProtocolManager;
	}

	private ProtocolManager createClient(String ownUniqueDeviceID) {
		ProtocolContext context = new ProtocolContext(ownUniqueDeviceID);
		ProtocolManager protocolManager = new ProtocolManager(context);
		protocolManager.activate();
		protocolManager.setMaster(false);

		return protocolManager;
	}

	private BlaubotConnectionMock createConnectionMock(String uniqueDeviceID, Socket socket) {
		BlaubotDeviceMock deviceMock = new BlaubotDeviceMock(uniqueDeviceID);
		BlaubotConnectionMock blaubotConnectionMock = new BlaubotConnectionMock(deviceMock, socket);
		return blaubotConnectionMock;
	}

}
