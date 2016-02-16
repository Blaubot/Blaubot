package eu.hgross.blaubot.ethernet;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.UniqueDeviceIdHelper;
import eu.hgross.blaubot.util.Log;

/**
 * Helper class for some ethernet specifics regarding acceptors/connectors/beacons 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotEthernetUtils {

    private static final String LOG_TAG = "BlaubotEthernetUtils";

    /**
	 * Creates a {@link BlaubotEthernetConnection} from an incoming {@link Socket}.
     * Receives the uniqueId from the socket and creates the blaubot device as well as the
     * connection object.
	 *
     * // TODO: check if we can generalize the uniqueId transmission through a DataInputStream
     *
     * @param clientSocket the connected socket
	 * @return the abstracted connection or null, if an {@link IOException} occured on reading the unique id - NOTE that the clientSocket will be closed if the result is null
	 */
	public static BlaubotEthernetConnection getEthernetConnectionFromSocket(Socket clientSocket) {
        // we await the connector to send us it's unique id (the counterpart of this is BlaubotEthernetUtils.sendOwnUniqueIdThroughSocket();
        String uniqueDeviceId;
        try {
            DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
            uniqueDeviceId = UniqueDeviceIdHelper.readUniqueDeviceId(dataInputStream);
        } catch (IOException e) {
            if(Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not read unique id: " + e.getMessage(), e);
            }
            try {
                clientSocket.close();
            } catch (IOException e1) {
                // ignore
            }
            return null;
        }

        BlaubotDevice blaubotDevice = new BlaubotDevice(uniqueDeviceId);
        return new BlaubotEthernetConnection(blaubotDevice, clientSocket);
	}


    /**
     * Send the unique id of ownDevice through the socket. This is the counterpart of the receive
     * in getEthernetConnectionFromSocket
     *
     * @param ownDevice the device of which the uniqueDevice should be send - usually the ownDevice
     * @param clientSocket the connected socket
     * @exception java.io.IOException if something went wrong with the socket communication
     */
    public static void sendOwnUniqueIdThroughSocket(IBlaubotDevice ownDevice, Socket clientSocket) throws IOException {
        final OutputStream outputStream = clientSocket.getOutputStream();
        UniqueDeviceIdHelper.sendUniqueDeviceIdThroughOutputStream(ownDevice, outputStream);
    }
}

