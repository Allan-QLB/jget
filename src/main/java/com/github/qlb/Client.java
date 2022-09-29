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
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private ChannelFuture connectionFuture;
    private volatile boolean closed;

    public Client(HttpTask task) {
        this.task = task;
    }

    public synchronized void start() {
        Bootstrap bootstrap = new Bootstrap();
        final Http http = task.getHttp();
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
                System.out.println("connect success");
            } else {
                //System.out.println(f.cause().getMessage());
                f.cause().printStackTrace();
                if (closed) {
                    group.shutdownGracefully();
                }
            }
        });
        connectionFuture.channel().closeFuture().addListener(f -> {
            if (!closed) {
                if (task instanceof Retryable && ((Retryable) task).canRetry()) {
                    ((Retryable) task).retry();
                } else {
                    task.failed();
                }
            } else {
                group.shutdownGracefully();
            }
        });
    }

    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        if (connectionFuture != null && group.isShutdown()) {
            final Channel channel = connectionFuture.channel();
            if (channel.isOpen()) {
                channel.close();
                System.out.println("close channel" + channel);
            } else {
                connectionFuture.addListener((ChannelFutureListener) future -> {
                    if (channel.isOpen()) {
                        System.out.println("close channel" + channel);
                        channel.close();
                    }
                });
            }
        }
    }

}
