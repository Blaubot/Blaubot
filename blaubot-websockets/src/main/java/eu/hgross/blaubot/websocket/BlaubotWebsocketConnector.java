package eu.hgross.blaubot.websocket;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.util.Log;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public class BlaubotWebsocketConnector implements IBlaubotConnector {
    private static final List<String> acceptedConnectorTypes = Arrays.asList(WebsocketConnectionMetaDataDTO.ACCEPTOR_TYPE);
    private static final String LOG_TAG = "BlaubotWebsocketConnector";

    private final IBlaubotAdapter adapter;
    private final IBlaubotDevice ownDevice;
    private final AtomicReference<IBlaubotIncomingConnectionListener> incomingConnectionListener;
    private IBlaubotBeaconStore beaconStore;


    public BlaubotWebsocketConnector(IBlaubotAdapter adapter, IBlaubotDevice ownDevice) {
        this.adapter = adapter;
        this.ownDevice = ownDevice;
        this.incomingConnectionListener = new AtomicReference<>();
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
    public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener acceptorConnectorListener) {
        this.incomingConnectionListener.set(acceptorConnectorListener);
    }

    @Override
    public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) {
        // check if we can get connection meta data for this device
        List<ConnectionMetaDataDTO> connectionMetaDataList = beaconStore.getLastKnownConnectionMetaData(blaubotDevice.getUniqueDeviceID());
        List<ConnectionMetaDataDTO> filteredMetaDataList = BlaubotAdapterHelper.filterBySupportedAcceptorTypes(connectionMetaDataList, getSupportedAcceptorTypes());

        // validate
        if(filteredMetaDataList.isEmpty()) {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "No meta data to connect to " + blaubotDevice);
            }
            return null;
        }

        // connect to the first applyable
        WebsocketConnectionMetaDataDTO connectionMetaData = new WebsocketConnectionMetaDataDTO(filteredMetaDataList.get(0));
        URI connectUri;

        // get the uri and append our unique device id
        String encodedUniqueDeviceID = null;
        try {
            encodedUniqueDeviceID = URLEncoder.encode(ownDevice.getUniqueDeviceID(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        final String uriStr = connectionMetaData.getUri() + "?" + BlaubotWebsocketAdapter.URI_PARAM_UNIQUEDEVICEID + "=" + encodedUniqueDeviceID;
        try {
            connectUri = new URI(uriStr);
        } catch (URISyntaxException e) {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Wrong URI format " + uriStr, e);
            }
            return null;
        }


        // connect to web socket using netty#
        final String scheme = connectUri.getScheme();
        final String host = connectUri.getHost();
        final int port = connectUri.getPort();

        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Bad scheme, Only WS(S) is supported -> " + connectUri);
            }
            return null;
        }

        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        if (ssl) {
            try {
                sslCtx = SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE);
            } catch (SSLException e) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Failed to create ssl context", e);
                }
                return null;
            }
        } else {
            sslCtx = null;
        }

        final EventLoopGroup group = new NioEventLoopGroup();
        try {
            final WebsocketClientHandler handler = new WebsocketClientHandler(connectUri, blaubotDevice.getUniqueDeviceID(), incomingConnectionListener);

            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Connecting to websocket: " + uriStr);
            }

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                            }
                            p.addLast(new HttpClientCodec(),
                                    new HttpObjectAggregator(8192),
                                    handler);
                        }
                    });

            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Awaiting web socket handshake to complete ...");
            }
            ChannelFuture channelFuture = b.connect(connectUri.getHost(), port);
            final AtomicBoolean result = new AtomicBoolean(false);
            final CountDownLatch latch = new CountDownLatch(1);
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    result.set(future.isSuccess());
                    latch.countDown();
                }
            });

            latch.await();

            if(result.get()) {
                BlaubotWebsocketConnection connection = handler.getConnection();
                if(connection == null) {
                    if (Log.logErrorMessages()) {
                        Log.d(LOG_TAG, "Connection could not be established.");
                    }
                    return null;
                }
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Websocket connected.");
                }

                // shutdown the group, if the connection is lost
                connection.addConnectionListener(new IBlaubotConnectionListener() {
                    @Override
                    public void onConnectionClosed(IBlaubotConnection connection) {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Websocket connection closed, shutting down netty");
                        }
                        group.shutdownGracefully();
                    }
                });
                return connection;
            } else {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Could not connect to web socket");
                }
                group.shutdownGracefully();
                return null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            group.shutdownGracefully();
            return null;
        }
    }

    @Override
    public List<String> getSupportedAcceptorTypes() {
        return acceptedConnectorTypes;
    }
}
