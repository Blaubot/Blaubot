package eu.hgross.blaubot.core;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.UUID;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;
import eu.hgross.blaubot.util.Log;

/**
 * This is a wrapper for connections to the server.
 * It extends a standard connection by exchanging the kingdom's king's uniqueDeviceId at construction
 * time.
 * It can only be constructed using createFromOutboundConnect (peasants, kings, princes) and
 * createFromInboundConnection (for the server side).
 * The Handshake will be at construction time on inbound connections.
 * On outbound connections the handshake is done automatically before the first byte is send.
 */
public class BlaubotKingdomConnection extends AbstractBlaubotConnection implements IBlaubotConnection {
    private static final String LOG_TAG = "BlaubotKingdomConnection";
    private final IBlaubotConnection connection;
    private volatile boolean handshakeDone = false;
    private volatile boolean isOutboundConnection = false;
    private UUID uuid = UUID.randomUUID();

    /**
     * The unique device id of the king
     */
    private String kingUniqueDeviceId;

    private BlaubotKingdomConnection(IBlaubotConnection connection, String kingUniqueDeviceId) {
        this.connection = connection;
        this.connection.addConnectionListener(new IBlaubotConnectionListener() {
            @Override
            public void onConnectionClosed(IBlaubotConnection connection) {
                disconnect();
                notifyDisconnected();
            }
        });
        this.kingUniqueDeviceId = kingUniqueDeviceId;
    }

    /**
     * Creates this connection upon another connection and ensures to send the handshake before first write.
     * @param connection the outbound connection to be wrapped
     * @param kingUniqueDeviceId the current kingdom's king unique device id
     * @return
     */
    public static BlaubotKingdomConnection createFromOutboundConnection(IBlaubotConnection connection, String kingUniqueDeviceId) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Creating KingdomConnection from outbound connection" + connection + " for kingdom of " + kingUniqueDeviceId);
        }
        BlaubotKingdomConnection blaubotKingdomConnection = new BlaubotKingdomConnection(connection, kingUniqueDeviceId);
        blaubotKingdomConnection.kingUniqueDeviceId = kingUniqueDeviceId;
        blaubotKingdomConnection.isOutboundConnection = true;
        return blaubotKingdomConnection;
    }

    /**
     * Creates this conneciton upon another connection and awaits data to be sent instantly.
     * Should be used in a separate thread.
     *
     * @param connection the inbound connection
     * @return
     */
    public static BlaubotKingdomConnection createFromInboundConnection(IBlaubotConnection connection) throws IOException {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Creating KingdomConnection from inbound connection" + connection);
        }
        BlaubotKingdomConnection blaubotKingdomConnection = new BlaubotKingdomConnection(connection, null);
        blaubotKingdomConnection.isOutboundConnection = false;

        BlaubotMessage message = BlaubotMessage.readFromBlaubotConnection(connection);
        String kingUniqueDeviceId = new String(message.getPayload(), BlaubotConstants.STRING_CHARSET);
        blaubotKingdomConnection.kingUniqueDeviceId = kingUniqueDeviceId;

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Sucessfully created KingdomConnection from inbound connection" + connection + " for kingdom " + kingUniqueDeviceId);
        }

        return blaubotKingdomConnection;
    }

    /**
     * ensures that the handshake was done before the first byte is sent
     */
    private synchronized void ensureHandshake() throws IOException {
        if(!isOutboundConnection) {
            return;
        }
        if(!handshakeDone) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "A write was requested on the kingdom connection but a handshake never took place. Handshaking now ...");
            }

            // send uniqueDeviceId of the king
            byte[] deviceIdBytes = kingUniqueDeviceId.getBytes(BlaubotConstants.STRING_CHARSET);
            BlaubotMessage kingdomIdMessage = new BlaubotMessage();
            kingdomIdMessage.setPriority(BlaubotMessage.Priority.ADMIN);
            kingdomIdMessage.setPayload(deviceIdBytes);
            kingdomIdMessage.getMessageType().setIsAdminMessage(false).setContainsPayload(true).setIsKeepAliveMessage(false).setIsFirstHop(false);
            byte[] toSend = kingdomIdMessage.toBytes();
            connection.write(toSend);
            handshakeDone = true;
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Handshake completed.");
            }
        }
    }

    @Override
    public void disconnect() {
        connection.disconnect();
    }

    @Override
    public boolean isConnected() {
        return connection.isConnected();
    }

    /**
     * used for the inbound connection (the server side) to fake the remote device as it is
     * relayed from this connection
     */
    private IBlaubotDevice fakedKingDevice;
    @Override
    public IBlaubotDevice getRemoteDevice() {
        if(!isOutboundConnection) {
            // inbound connection
            if (fakedKingDevice == null) {
                fakedKingDevice = new BlaubotDevice(kingUniqueDeviceId);
            }
            return fakedKingDevice;
        }
        return connection.getRemoteDevice();
    }

    @Override
    public void write(int b) throws SocketTimeoutException, IOException {
        ensureHandshake();
        connection.write(b);
    }

    @Override
    public void write(byte[] bytes) throws SocketTimeoutException, IOException {
        ensureHandshake();
        connection.write(bytes);
    }

    @Override
    public void write(byte[] bytes, int byteOffset, int byteCount) throws SocketTimeoutException, IOException {
        ensureHandshake();
        connection.write(bytes, byteOffset, byteCount);
    }

    @Override
    public int read() throws SocketTimeoutException, IOException {
        return connection.read();
    }

    @Override
    public int read(byte[] buffer) throws SocketTimeoutException, IOException {
        return connection.read(buffer);
    }

    @Override
    public int read(byte[] buffer, int byteOffset, int byteCount) throws SocketTimeoutException, IOException {
        return connection.read(buffer, byteOffset, byteCount);
    }

    @Override
    public void readFully(byte[] buffer) throws SocketTimeoutException, IOException {
        connection.readFully(buffer);
    }

    @Override
    public void readFully(byte[] buffer, int offset, int byteCount) throws SocketTimeoutException, IOException {
        connection.readFully(buffer, offset, byteCount);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BlaubotKingdomConnection{");
        sb.append("connection=").append(connection);
        sb.append(", handshakeDone=").append(handshakeDone);
        sb.append(", isOutboundConnection=").append(isOutboundConnection);
        sb.append(", kingUniqueDeviceId='").append(kingUniqueDeviceId).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BlaubotKingdomConnection that = (BlaubotKingdomConnection) o;

        if (uuid != null ? !uuid.equals(that.uuid) : that.uuid != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        return result;
    }

    public static void main(String[] args) throws InterruptedException {
        final BlaubotConnectionQueueMock mockCon1 = new BlaubotConnectionQueueMock(new BlaubotDevice("server"));
        final BlaubotConnectionQueueMock mockCon2 = mockCon1.getOtherEndpointConnection(new BlaubotDevice("client"));

        Runnable outboundConnectionTask = new Runnable() {
            @Override
            public void run() {
                BlaubotKingdomConnection fromOutboundConnection = BlaubotKingdomConnection.createFromOutboundConnection(mockCon1, "daKing");
                try {
                    // to trigger the handshake
                    fromOutboundConnection.write((byte)5);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        Runnable inboundConnectionTask = new Runnable() {
            @Override
            public void run() {
                try {
                    BlaubotKingdomConnection fromInboundConnection = BlaubotKingdomConnection.createFromInboundConnection((mockCon2));
                    System.out.println("king: " + fromInboundConnection.kingUniqueDeviceId);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        Thread t1 = new Thread(outboundConnectionTask);
        Thread t2 = new Thread(inboundConnectionTask);

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("done");
    }

    /**
     * Get the uniqueDeviceId of the king
     * @return
     */
    public String getKingUniqueDeviceId() {
        return kingUniqueDeviceId;
    }
}
