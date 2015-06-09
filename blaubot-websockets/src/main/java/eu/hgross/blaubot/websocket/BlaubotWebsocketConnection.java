package eu.hgross.blaubot.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;
import eu.hgross.blaubot.util.Log;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

/**
 * Blaubot connection to work upon netty websockets.
 */
public class BlaubotWebsocketConnection extends BlaubotConnectionQueueMock implements IBlaubotConnection {
    private static final String LOG_TAG = "BlaubotWebsocketConnection";
    private final Channel websocketChannel;

    public BlaubotWebsocketConnection(IBlaubotDevice remoteDevice, Channel webSocketChannel) {
        super(remoteDevice);
        this.websocketChannel = webSocketChannel;
        websocketChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                disconnect();
            }
        });
    }

    @Override
    public void disconnect() {
        websocketChannel.disconnect();
        super.disconnect();
    }

    private void handleNotConnectedException(IOException e) throws SocketTimeoutException, IOException {
        if(Log.logWarningMessages()) {
            Log.w(LOG_TAG, "Got io exception, notifying", e);
        }
        this.disconnect();
        throw e;
    }

    @Override
    public void write(int b) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        if (!connected) {
            handleNotConnectedException(new IOException("not connected"));
        }
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        websocketChannel.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
    }

    @Override
    public void write(byte[] bytes, int byteOffset, int byteCount) throws IOException {
        if (!connected) {
            handleNotConnectedException(new IOException("not connected"));
        }
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes, byteOffset, byteCount);
        websocketChannel.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
    }

    /**
     * Write data to the stream that can be retrieved via the {@link IBlaubotConnection}s
     * read*() methods.
     *
     * @param data
     *            the data to write to the input stream as byte array
     */
    public synchronized void writeMockDataToInputStream(ByteBuf data) {
        data.forEachByte(writeMockDataProcessor);
    }
    private final ByteBufProcessor writeMockDataProcessor = new ByteBufProcessor() {
        @Override
        public boolean process(byte b) throws Exception {
            inputQueue.add(b);
            return true;
        }
    };

    @Override
    public InputStream getInputStreamForWrittenConnectionData() {
        throw new UnsupportedOperationException("Use the base class if you need this");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BlaubotWebsocketConnection that = (BlaubotWebsocketConnection) o;

        if (!websocketChannel.equals(that.websocketChannel)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + websocketChannel.hashCode();
        return result;
    }
}
