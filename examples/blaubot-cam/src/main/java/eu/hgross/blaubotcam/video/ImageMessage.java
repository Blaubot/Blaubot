package eu.hgross.blaubotcam.video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Date;

import eu.hgross.blaubot.core.BlaubotConstants;

/**
 * A class to send images through a channel
 */
public class ImageMessage {
    private String uniqueDeviceId;
    private byte[] jpegData;
    private Long time;

    /**
     * @param uniqueDeviceId the sender's uniqueDeviceId
     * @param jpegData the jpeg data
     * @param date the datetime when the picture was taken
     */
    public ImageMessage(String uniqueDeviceId, byte[] jpegData, Date date) {
        this.uniqueDeviceId = uniqueDeviceId;
        this.jpegData = jpegData;
        this.time = date.getTime();
    }

    /**
     * @param bytes a byte array to construct the message from
     */
    public ImageMessage(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        int uniqueDeviceIdLength = bb.getInt();
        byte[] strBytes = new byte[uniqueDeviceIdLength];
        bb.get(strBytes);
        int jpegBytesLength = bb.getInt();
        byte[] jpegBytes = new byte[jpegBytesLength];
        bb.get(jpegBytes);

        this.uniqueDeviceId = new String(strBytes, BlaubotConstants.STRING_CHARSET);
        this.jpegData = jpegBytes;
        this.time = bb.getLong();
    }

    /**
     * Creates a bitmap out of the message bytes
     * @return
     */
    public Bitmap toBitmap() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(getJpegData());
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        return bitmap;
    }

    public byte[] toBytes() {
        // 1 long, 2 ints, jpeg data and string
        ByteBuffer bb = ByteBuffer.allocate(8 + 4 + 4 + jpegData.length + uniqueDeviceId.length());
        bb.order(BlaubotConstants.BYTE_ORDER);
        // store the id
        bb.putInt(uniqueDeviceId.length()).put(uniqueDeviceId.getBytes(BlaubotConstants.STRING_CHARSET));
        // store the jpeg
        bb.putInt(jpegData.length).put(jpegData);
        // store the time
        bb.putLong(time);
        // retrieve byte array from buffer
        byte[] bytes = new byte[bb.capacity()];
        bb.clear();
        bb.get(bytes);
        return bytes;
    }

    /**
     * @return uniqueDeviceId of the device that recorded this image
     */
    public String getUniqueDeviceId() {
        return uniqueDeviceId;
    }

    /**
     * @return the jpeg as byte array
     */
    public byte[] getJpegData() {
        return jpegData;
    }

    /**
     * @return the date when the picture was taken.
     */
    public Date getTime() {
        return new Date(time);
    }

}
