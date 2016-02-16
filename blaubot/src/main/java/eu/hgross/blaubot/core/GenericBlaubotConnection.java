package eu.hgross.blaubot.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import eu.hgross.blaubot.core.AbstractBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.util.Log;

/**
 * An generic generic {@link IBlaubotConnection} blaubot connection implementation using input and output streams
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 */
public abstract class GenericBlaubotConnection extends AbstractBlaubotConnection {
    private IBlaubotDevice blaubotDevice;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private UUID uuid = UUID.randomUUID(); // for instance based hashcode/equals

    public GenericBlaubotConnection(IBlaubotDevice device, InputStream inputStream, OutputStream outputStream) {
        this.blaubotDevice = device;
        this.dataInputStream = new DataInputStream(inputStream);
        this.dataOutputStream = new DataOutputStream(outputStream);
    }

    private boolean notifiedDisconnect = false;

    @Override
    protected void notifyDisconnected() {
        if (notifiedDisconnect)
            return;
        super.notifyDisconnected();
        notifiedDisconnect = true;
    }

    /**
     * Disconnects the datainput and dataoutputstrem
     */
    protected void disconnectStreams() {
        if (Log.logDebugMessages()) {
            Log.d(getLogTag(), "Disconnecting BluetoothBlaubotConnection " + this + " ...");
        }
        try {
            dataOutputStream.close();
        } catch (IOException e) {
            if (Log.logErrorMessages()) {
                Log.e(getLogTag(), "Failed to close output stream", e);
            }
        }
        try {
            dataInputStream.close();
        } catch (IOException e) {
            if (Log.logErrorMessages()) {
                Log.e(getLogTag(), "Failed to close input stream", e);
            }
        }
    }

    /**
     * @return the log tag for this implementation
     */
    protected abstract String getLogTag();

    private void handleSocketException(IOException e) throws IOException {
        if (Log.logWarningMessages()) {
            Log.w(getLogTag(), "Got socket exception", e);
        }
        this.disconnect();
        throw e;
    }

    @Override
    public int read() throws IOException {
        try {
            return dataInputStream.read();
        } catch (IOException e) {
            this.handleSocketException(e);
            return -1; // will never get here
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        try {
            return dataInputStream.read(b);
        } catch (IOException e) {
            this.handleSocketException(e);
            return -1; // will never get here
        }
    }

    @Override
    public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
        try {
            return dataInputStream.read(buffer, byteOffset, byteCount);
        } catch (IOException e) {
            this.handleSocketException(e);
            return -1; // will never get here
        }
    }

    @Override
    public void write(int b) throws IOException {
        try {
            dataOutputStream.write(b);
        } catch (IOException e) {
            this.handleSocketException(e);
        }
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        try {
            dataOutputStream.write(bytes);
        } catch (IOException e) {
            this.handleSocketException(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            dataOutputStream.write(b, off, len);
        } catch (IOException e) {
            this.handleSocketException(e);
        }
    }

    @Override
    public void readFully(byte[] buffer) throws IOException {
        try {
            dataInputStream.readFully(buffer);
        } catch (IOException e) {
            handleSocketException(e);
        }
    }

    @Override
    public void readFully(byte[] buffer, int offset, int byteCount) throws IOException {
        try {
            dataInputStream.readFully(buffer, offset, byteCount);
        } catch (IOException e) {
            handleSocketException(e);
        }
    }

    @Override
    public IBlaubotDevice getRemoteDevice() {
        return this.blaubotDevice;
    }

    @Override
    public String toString() {
        return "GenericBlaubotConnection[" + getRemoteDevice() + "]";
    }

    public DataOutputStream getDataOutputStream() {
        return dataOutputStream;
    }

    public DataInputStream getDataInputStream() {
        return dataInputStream;
    }
}
