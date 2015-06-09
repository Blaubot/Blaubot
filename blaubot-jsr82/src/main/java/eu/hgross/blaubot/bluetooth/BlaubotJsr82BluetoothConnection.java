package eu.hgross.blaubot.bluetooth;

import java.io.IOException;

import javax.microedition.io.StreamConnection;

import eu.hgross.blaubot.core.GenericBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;

/**
 * A bluetooth connection using the provided JSR82 implementation (probalby bluecove)
 */
public class BlaubotJsr82BluetoothConnection extends GenericBlaubotConnection {
    private static final String LOG_TAG = "BlaubotJsr82BluetoothConnection";
    private final StreamConnection streamConnection;

    public BlaubotJsr82BluetoothConnection(IBlaubotDevice remoteDevice, StreamConnection streamConnection) throws IOException {
        super(remoteDevice, streamConnection.openInputStream(), streamConnection.openOutputStream());
        this.streamConnection = streamConnection;
    }

    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Override
    public void disconnect() {
        disconnectStreams();
        try {
            streamConnection.close();
        } catch (IOException e) {
        }
        notifyDisconnected();
    }

    @Override
    public boolean isConnected() {
        return false;
    }
}
