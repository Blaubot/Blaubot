package eu.hgross.blaubot.core.acceptor;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IBlaubotDevice;

/**
 * Helper to serialize and de-serialize the uniqueDevice id from input and output streams.
 */
public class UniqueDeviceIdHelper {
    /**
     * Reads a uniqueDeviceId from a data input stream.
     *
     * @param dataInputStream the input stream
     * @return the unique device id
     * @throws java.io.IOException if something goes wrong
     */
    public static String readUniqueDeviceId(DataInputStream dataInputStream) throws IOException {
        String uniqueDeviceId;// read length of unique id
        byte[] lengthBuff = new byte[4];
        dataInputStream.readFully(lengthBuff, 0, lengthBuff.length);
        final ByteBuffer bbLength = ByteBuffer.wrap(lengthBuff);
        bbLength.order(BlaubotConstants.BYTE_ORDER);
        int uniqueIdLength = bbLength.getInt();

        // read the actual unique id
        byte[] uniqueIdBuff = new byte[uniqueIdLength];
        dataInputStream.readFully(uniqueIdBuff, 0, uniqueIdLength);
        final ByteBuffer bbUniqueId = ByteBuffer.wrap(uniqueIdBuff);
        bbUniqueId.order(BlaubotConstants.BYTE_ORDER);
        byte[] strBytes = new byte[uniqueIdLength];
        bbUniqueId.get(strBytes);
        uniqueDeviceId = new String(strBytes, BlaubotConstants.STRING_CHARSET);
        return uniqueDeviceId;
    }

    /**
     * Sends the uniqueDeviceId of ownDevice through an outputstream
     *
     * @param ownDevice the own device containing the id
     * @param outputStream the outputstream to send through
     * @throws IOException if something goes wrong
     */
    public static void sendUniqueDeviceIdThroughOutputStream(IBlaubotDevice ownDevice, OutputStream outputStream) throws IOException {
        // Send our unique id through the outputstream
        final String uniqueDeviceID = ownDevice.getUniqueDeviceID();
        byte[] uniqueIdBytes = uniqueDeviceID.getBytes(BlaubotConstants.STRING_CHARSET);
        final ByteBuffer bb = ByteBuffer.allocate(4 + uniqueDeviceID.length());
        bb.order(BlaubotConstants.BYTE_ORDER);
        bb.putInt(uniqueDeviceID.length());
        bb.put(uniqueIdBytes);
        bb.flip();
        outputStream.write(bb.array());
    }
}
