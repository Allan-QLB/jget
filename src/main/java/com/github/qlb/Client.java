package com.github.qlb;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public class Client {
    private final HttpTask task;
    private final ChannelFutureListener logCloseListener;
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private volatile ChannelFuture connectionFuture;
    private volatile ChannelFutureListener connectionCloseListener = f -> {};
    private volatile boolean closed;

    public Client(HttpTask task) {
        this.task = task;
        logCloseListener = f -> System.out.println(task + " connection " + f.channel() + " is closed");
    }

    public synchronized void start() {
        Bootstrap bootstrap = new Bootstrap();
        final Http http = task.getHttp();
        doCloseCurrentConnection(false);
        connectionFuture = bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                final ChannelPipeline pipeline = channel.pipeline();
                if (http.isSecure()) {
                    pipeline.addLast(SslContextBuilder
                            .forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build().newHandler(channel.alloc(), http.getHost(), http.getPort()));
                }
                pipeline.addLast(new HttpClientCodec())
                        .addLast(new ResponseHandler(task));

            }
        }).connect(http.getHost(), http.getPort()).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                System.out.println(task + " connect success " + f.channel());
            } else {
                System.out.println(task + " connect fail " + f.channel());
                f.cause().printStackTrace();
            }
        });
        connectionCloseListener = f -> task.failed();
        connectionFuture.channel().closeFuture().addListeners(logCloseListener, connectionCloseListener);
    }

    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        doCloseCurrentConnection(true);
    }

    private void doCloseCurrentConnection(boolean shutdown) {
        if (connectionFuture != null) {
            final Channel channel = connectionFuture.channel();
            channel.closeFuture().removeListener(connectionCloseListener);
            connectionFuture.cancel(true);
            if (channel.isOpen()) {
                channel.close();
            }
            if (shutdown) {
                group.shutdownGracefully();
            }
//            else {
//                connectionFuture.addListener((ChannelFutureListener) future -> {
//                    if (future.channel().isOpen()) {
//                        future.channel().close();
//                    }
//                    if (shutdown) {
//                        group.shutdownGracefully();
//                    }
//                });
//            }
        }
    }

}
