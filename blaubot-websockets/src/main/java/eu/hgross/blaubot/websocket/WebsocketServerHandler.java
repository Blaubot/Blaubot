package eu.hgross.blaubot.websocket;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.util.Log;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


/**
 * Handles handshakes and messages.
 * Used in the acceptor.
 */
public class WebsocketServerHandler extends SimpleChannelInboundHandler<Object> {
    private static final String LOG_TAG = "WebsocketServerHandler";
    private WebSocketServerHandshaker handshaker;

    private final ConcurrentHashMap<Channel, BlaubotWebsocketConnection> blaubotConnectionsMap;
    private final AtomicReference<IBlaubotIncomingConnectionListener> incomingConnectionListener;

    public WebsocketServerHandler(AtomicReference<IBlaubotIncomingConnectionListener> incomingConnectionListenerReference) {
        this.blaubotConnectionsMap = new ConcurrentHashMap<>();
        this.incomingConnectionListener = incomingConnectionListenerReference;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        // Handle a bad request.
        if (!req.getDecoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        // Allow only GET methods.
        if (req.getMethod() != GET) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        // Handshake
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, true, BlaubotWebsocketAdapter.MAX_WEBSOCKET_FRAME_SIZE);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            // extract uniqueDeviceId from query parameters
            final String requestUri = req.getUri();
            final QueryStringDecoder qstringDecoder = new QueryStringDecoder(requestUri);
            final List<String> uniqueDeviceIdParam = qstringDecoder.parameters().get(BlaubotWebsocketAdapter.URI_PARAM_UNIQUEDEVICEID);
            if(uniqueDeviceIdParam == null || uniqueDeviceIdParam.isEmpty()) {
                if(Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "UniqueDeviceId not provided while connecting WebSocket");
                }

                // send bad request (missing uniqueDeviceId param)
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
                return;
            }
            final String uniqueDeviceId = uniqueDeviceIdParam.get(0);

            final ChannelFuture handShakeFuture = handshaker.handshake(ctx.channel(), req);
            handShakeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Channel channel = future.channel();
                    BlaubotDevice remoteDevice = new BlaubotDevice(uniqueDeviceId);
                    BlaubotWebsocketConnection blaubotWebsocketConnection = new BlaubotWebsocketConnection(remoteDevice, channel);
                    blaubotConnectionsMap.put(channel, blaubotWebsocketConnection);
                    incomingConnectionListener.get().onConnectionEstablished(blaubotWebsocketConnection);
                }
            });
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        if(frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame binaryWebSocketFrame = (BinaryWebSocketFrame) frame;
            ByteBuf content = binaryWebSocketFrame.content();
            // write to the connection
            BlaubotWebsocketConnection conn = blaubotConnectionsMap.get(ctx.channel());
            conn.writeMockDataToInputStream(content);
            return;
        }

        if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
                    .getName()));
        }

        // Send the uppercase string back.
        TextWebSocketFrame textWebSocketFrame = (TextWebSocketFrame) frame;
        String data = textWebSocketFrame.text();
        System.err.printf("%s received %s%n", ctx.channel(), data);

        // write the data to the connection
//        BlaubotWebsocketConnection conn = blaubotConnectionsMap.get(ctx.channel());
//        byte[] dataBytes = data.getBytes(BlaubotConstants.STRING_CHARSET);
//        conn.writeMockDataToInputStream(dataBytes);

        //ctx.channel().write(new TextWebSocketFrame(data.toUpperCase()));
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            HttpHeaders.setContentLength(res, res.content().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private static String getWebSocketLocation(FullHttpRequest req) {
        String location =  req.headers().get(HOST) + BlaubotWebsocketAdapter.WEBSOCKET_PATH;
        if (BlaubotWebsocketAcceptor.SSL) {
            return "wss://" + location;
        } else {
            return "ws://" + location;
        }
    }
}