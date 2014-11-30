package de.hsrm.blaubot.ethernet;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Helper class for some ethernet specifics regarding acceptors/connectors/beacons 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotEthernetUtils {
	
	/**
	 * Creates a {@link BlaubotEthernetConnection} from an incoming {@link Socket}.
	 * This helper methods expects the client to send two integers (acceptorPort, beaconPort)
	 * after the connection was established and creates the Blaubot abstraction objects
	 * from this information.
	 * 
	 * @param clientSocket the connected socket
	 * @param adapter the blaubotAdapter
	 * @return the abstracted connection or null, if an {@link IOException} occured on reading the acceptor and beacon ports - NOTE that the clientSocket will be closed if the result is null
	 */
	protected static BlaubotEthernetConnection getEthernetConnectionFromSocket(Socket clientSocket, BlaubotEthernetAdapter adapter) {
		// The extended task will connect here and propagate acceptor and beacon port
		byte[] buff = new byte[EthernetExchangeTask.METADATA_BYTE_LENGTH]; // 2 ints
		try {
			DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
			dis.readFully(buff, 0, EthernetExchangeTask.METADATA_BYTE_LENGTH);
		} catch (IOException e) {
			try {
				clientSocket.close();
			} catch (IOException e1) {
			}
			return null;
		}
		ByteBuffer bb = ByteBuffer.wrap(buff);
		int remoteAcceptorPort = bb.getInt();
		int remoteBeaconPort = bb.getInt();
		
		BlaubotEthernetDevice ethernetDevice = new BlaubotEthernetDevice(clientSocket.getInetAddress(), remoteAcceptorPort, remoteBeaconPort, adapter);
		BlaubotEthernetConnection connection = new BlaubotEthernetConnection(ethernetDevice, clientSocket);
		return connection;
	}
	
	/**
	 * Sends the acceptor and beacon ports through a socket to the remote end.
	 * This method is the counterpart for getEthernetConnectionFromSocket(..) and
	 * should be used in ethernet connectors.
	 * 
	 * @param socket the freshly created socket to the remote {@link ServerSocket}
	 * @param adapter the {@link BlaubotEthernetAdapter} 
	 * @throws IOException if the write fails
	 */
	protected static void sendAcceptorAndBeaconPortsThroughSocket(Socket socket, BlaubotEthernetAdapter adapter) throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(EthernetExchangeTask.METADATA_BYTE_LENGTH); // 2 ints
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(adapter.getAcceptorPort());
		bb.putInt(adapter.getBeaconPort());
		bb.flip();
		socket.getOutputStream().write(bb.array());
		socket.getOutputStream().flush();
	}
	
	/**
	 * Creates the uniqueId {@link String} identifying a {@link BlaubotEthernetDevice}.
	 * 
	 * @param inetAddr the ip address
	 * @param acceptorPort the acceptor's port
	 * @param beaconPort the beacon's port
	 * @return valid unique id string
	 */
	public static String createUniqueDeviceId(InetAddress inetAddr, int acceptorPort, int beaconPort) {
		StringBuilder sb = new StringBuilder();
		sb.append(inetAddr.getHostAddress());
		sb.append(BlaubotEthernetDevice.UNIQUE_ID_SEPARATOR);
		sb.append(acceptorPort);
		sb.append(BlaubotEthernetDevice.UNIQUE_ID_SEPARATOR);
		sb.append(beaconPort);
		return sb.toString();
	}
	
	/**
	 * Retrieve the acceptorPort out of a uniqueDeviceId.
	 * @param uniqueDeviceId
	 * @return the acceptorPort
	 */
	public static int getAcceptorPortFromUniqueId(String uniqueDeviceId) {
		String[] splitted = uniqueDeviceId.split(BlaubotEthernetDevice.UNIQUE_ID_SEPARATOR);
		return Integer.parseInt(splitted[1]);
	}
	
	/**
	 * Retrieve the beaconPort out of a uniqueDeviceId.
	 * @param uniqueDeviceId
	 * @return the beaconPort
	 */
	public static int getBeaconPortFromUniqueId(String uniqueDeviceId) {
		String[] splitted = uniqueDeviceId.split(BlaubotEthernetDevice.UNIQUE_ID_SEPARATOR);
		return Integer.parseInt(splitted[2]);
	}
	
	/**
	 * Retrieve the beaconPort out of a uniqueDeviceId.
	 * @param uniqueDeviceId
	 * @return the beaconPort
	 * @throws UnknownHostException 
	 */
	public static InetAddress getInetAddressFromUniqueId(String uniqueDeviceId) throws UnknownHostException {
		String[] splitted = uniqueDeviceId.split(BlaubotEthernetDevice.UNIQUE_ID_SEPARATOR);
		return InetAddress.getByName(splitted[0]);
	}
	
}

