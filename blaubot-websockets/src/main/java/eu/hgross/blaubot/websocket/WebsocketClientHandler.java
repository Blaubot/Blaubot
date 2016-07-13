package eu.hgross.blaubot.websocket;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.util.Log;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;

/**
 * WebSocketClientHandler that manages the BlaubotWebsocketConnection
 * 
 * After .connect() via the bootstrapper, call getConnection().
 */
public class WebsocketClientHandler extends SimpleChannelInboundHandler<Object> {
    private static final String LOG_TAG = "WebsocketClientHandler";
    private final WebSocketClientHandshaker handshaker;
    private final String remoteDeviceUniqueDeviceId;
    private final AtomicReference<IBlaubotIncomingConnectionListener> incomingConnectionListenerReference;
    private ChannelPromise handshakeFuture;
    private BlaubotWebsocketConnection connection;

    /**
     * Creates a new WebSocketClientHandler that manages the BlaubotWebsocketConnection
     * @param uri                  The uri to connect with
     * @param remoteUniqueDeviceId the unique device id of the device we are connecting to
     * @param listenerReference    a reference Object that handles the connection listener
     */
    public WebsocketClientHandler(URI uri, String remoteUniqueDeviceId, AtomicReference<IBlaubotIncomingConnectionListener> listenerReference) {
        // Connect with V13 (RFC 6455 aka HyBi-17).
        // other options are V08 or V00.
        // If V00 is used, ping is not supported and remember to change
        // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
        this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders(), BlaubotWebsocketAdapter.MAX_WEBSOCKET_FRAME_SIZE);
        this.remoteDeviceUniqueDeviceId = remoteUniqueDeviceId;
        this.incomingConnectionListenerReference = listenerReference;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
        handshakeFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel channel = future.channel();
                BlaubotDevice remoteDevice = new BlaubotDevice(remoteDeviceUniqueDeviceId);
                connection = new BlaubotWebsocketConnection(remoteDevice, channel);
                final IBlaubotIncomingConnectionListener connectionListener = incomingConnectionListenerReference.get();
                if (connectionListener != null) {
                    connectionListener.onConnectionEstablished(connection);
                }
            }
        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "WebSocket Client disconnected!");
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "WebSocket Client connected!");
            }
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.getStatus() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame binaryWebSocketFrame = (BinaryWebSocketFrame) frame;
            ByteBuf content = binaryWebSocketFrame.content();
            // write to the connection
            connection.writeMockDataToInputStream(content);
        } else if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "WebSocket Client received message: " + textFrame.text());
            }
        } else if (frame instanceof PongWebSocketFrame) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "WebSocket Client received pong");
            }
        } else if (frame instanceof CloseWebSocketFrame) {
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "WebSocket Client received closing");
            }
            ch.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }

    /**
     * Blocks until the handshake is done.
     * If done, the blaubot connection is returned
     *
     * @return the blaubot connection if the connection was successful or null, if not
     * @throws InterruptedException if interrupted while waiting for the handshake
     */
    public synchronized BlaubotWebsocketConnection getConnection() throws InterruptedException {
        if (this.connection != null) {
            return connection;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean result = new AtomicBoolean(false);
        handshakeFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                result.set(future.isSuccess());
                latch.countDown();
            }
        });
        latch.await();
        if (result.get()) {
            return this.connection;
        }
        return null;
    }
}
