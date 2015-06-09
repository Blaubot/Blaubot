package eu.hgross.blaubot.websocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.util.Log;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Acceptor using netty for communication over websockets
 */
public class BlaubotWebsocketAcceptor implements IBlaubotConnectionAcceptor {
    /**
     * Max milliseconds to bind to the given acceptorPort
     */
    private static final long MAX_TIME_TO_BIND = 10000;
    public static final String LOG_TAG = "BlaubotWebsocketAcceptor";
    protected static boolean SSL = false;

    private final IBlaubotAdapter adapter;
    private final int acceptorPort;
    private final String hostAddress;
    private IBlaubotListeningStateListener listeningStateListener;

    /**
     * Is handed to the websocket handler
     */
    private AtomicReference<IBlaubotIncomingConnectionListener> incomingConnectionListener;

    /**
     * The current netty channel on which the websocket is working
     */
    private Channel currentChannel;
    private final Object startStopMonitor = new Object();

    public BlaubotWebsocketAcceptor(IBlaubotAdapter adapter, String hostAddress, int acceptorPort) {
        this.adapter = adapter;
        this.acceptorPort = acceptorPort;
        this.hostAddress = hostAddress;
        this.incomingConnectionListener = new AtomicReference<>();
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        // not used
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void startListening() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Starting netty websocket server.");
        }
        synchronized (startStopMonitor) {
            // Configure SSL.
            SslContext sslCtx;
            if (SSL) {
                try {
                    SelfSignedCertificate ssc = new SelfSignedCertificate();
                    sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
                } catch (Exception e) {
                    sslCtx = null;
                    e.printStackTrace();
                }
            } else {
                sslCtx = null;
            }

            final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            final EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new WebSocketServerInitializer(sslCtx));


                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Creating websocket server. Binding to port " + acceptorPort);
                }
                final CountDownLatch latch = new CountDownLatch(1);
                bootstrap.bind(acceptorPort).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Bind " + (future.isSuccess() ? "succeeded" : "failed"));
                        }
                        if (future.isSuccess()) {
                            currentChannel = future.channel();
                        } else {
                            currentChannel = null;
                            bossGroup.shutdownGracefully();
                            workerGroup.shutdownGracefully();
                        }
                        if (listeningStateListener != null) {
                            listeningStateListener.onListeningStarted(BlaubotWebsocketAcceptor.this);
                        }
                        latch.countDown();
                    }
                });

                boolean timedOut = !latch.await(MAX_TIME_TO_BIND, TimeUnit.MILLISECONDS);
                if (timedOut) {
                    if (Log.logDebugMessages()) {
                        Log.e(LOG_TAG, "Failed to start listening");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Netty websocket server should now be started.");
        }
    }

    @Override
    public void stopListening() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Stopping netty websocket server ...");
        }
        synchronized (startStopMonitor) {
            if(this.currentChannel != null) {
                final ChannelFuture closeFuture = this.currentChannel.closeFuture();
                closeFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (listeningStateListener != null) {
                            listeningStateListener.onListeningStopped(BlaubotWebsocketAcceptor.this);
                        }
                    }
                });
                this.currentChannel.close();
                try {
                    closeFuture.sync();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                this.currentChannel = null;
            }
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Netty websocket server should now be stopped ...");
        }
    }

    @Override
    public boolean isStarted() {
        synchronized (startStopMonitor) {
            return currentChannel != null;
        }
    }

    @Override
    public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
        this.listeningStateListener = stateListener;
    }

    @Override
    public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
        this.incomingConnectionListener.set(acceptorListener);
    }

    @Override
    public ConnectionMetaDataDTO getConnectionMetaData() {
        return new WebsocketConnectionMetaDataDTO(hostAddress, BlaubotWebsocketAdapter.WEBSOCKET_PATH, acceptorPort);
    }

    /**
     * Configures the pipeline for the netty channel
     */
    public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {
        private final SslContext sslCtx;

        public WebSocketServerInitializer(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            if (sslCtx != null) {
                pipeline.addLast(sslCtx.newHandler(ch.alloc()));
            }
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536));
            pipeline.addLast(new WebsocketServerHandler(incomingConnectionListener));
        }
    }
}
