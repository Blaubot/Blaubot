package eu.hgross.blaubotcam.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import eu.hgross.blaubot.util.Log;

/**
 * Message to be sent
 */
public class WalkieTalkieMessage {
    public static final String LOG_TAG = WalkieTalkieMessage.class.getSimpleName();
    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final Charset CHARSET = Charset.forName("UTF-8");

    private byte[] audioBytes;
    private String senderUniqueDeviceId;

    private WalkieTalkieMessage() {
    }

    public WalkieTalkieMessage(byte[] audioBytes, String senderUniqueDeviceId) {
        this.senderUniqueDeviceId = senderUniqueDeviceId;
        this.audioBytes = audioBytes;
    }

    public byte[] getAudioBytes() {
        return audioBytes;
    }

    public String getSenderUniqueDeviceId() {
        return senderUniqueDeviceId;
    }


    /**
     * Plays the containing audio message.
     * Blocks until playback was completed.
     *
     * @throws IOException if there is not enough space to store the temp audio file
     */
    public void play(Context context) throws IOException {
        final File outputFile = File.createTempFile("BlaubotTemp" + UUID.randomUUID(), "3gp", context.getCacheDir());
        FileOutputStream fos = new FileOutputStream(outputFile);
        fos.write(getAudioBytes());
        fos.close();

        final MediaPlayer player = new MediaPlayer();
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            player.setDataSource(outputFile.getAbsolutePath());
            player.prepare();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {


                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d(LOG_TAG, "MediaPlayer.onCompletion()");
                    player.release();
                    outputFile.delete();
                    latch.countDown();
                }
            });
            player.start();

            try {
                latch.await();
            } catch (InterruptedException e) {
            }
        } catch (IOException e) {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "prepare() to play failed: " + e.getMessage());
            }
        }
    }

    /**
     * Serializes the message to a byte array
     *
     * @return the byte array
     */
    public byte[] toBytes() {
        // allocate buffer for 2 ints inidicating the length and the actual lenghts of the data attributes
        ByteBuffer bb = ByteBuffer.allocate(4 + 4 + audioBytes.length + senderUniqueDeviceId.length());
        bb.order(BYTE_ORDER);
        bb.putInt(audioBytes.length);
        bb.putInt(senderUniqueDeviceId.length());

        bb.put(audioBytes);
        bb.put(senderUniqueDeviceId.getBytes(CHARSET));
        bb.flip();
        return bb.array();
    }

    /**
     * Deserializes the message from a byte array
     *
     * @param bytes the bytes to deserialize
     * @return the deserialized message
     */
    public static WalkieTalkieMessage fromBytes(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        // get size of needed buffers
        final int audioBytesLength = bb.getInt();
        final int senderUniqueDeviceIdLength = bb.getInt();

        // allocate byte arrays
        byte[] audioBytesBuffer = new byte[audioBytesLength];
        byte[] senderUniqueDeviceIdBuffer = new byte[senderUniqueDeviceIdLength];

        // fill buffers
        bb.get(audioBytesBuffer, 0, audioBytesLength);
        bb.get(senderUniqueDeviceIdBuffer, 0, senderUniqueDeviceIdLength);

        // construct message
        final WalkieTalkieMessage message = new WalkieTalkieMessage();
        message.audioBytes = audioBytesBuffer;
        message.senderUniqueDeviceId = new String(senderUniqueDeviceIdBuffer, CHARSET);
        return message;
    }
}
