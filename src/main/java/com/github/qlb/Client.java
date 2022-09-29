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
    private Channel connection;
    private volatile boolean closed;

    public Client(HttpTask task) {
        this.task = task;
    }

    public void start() {
        Bootstrap bootstrap = new Bootstrap();
        final Http http = task.getHttp();
        final ChannelFuture connect = bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
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
                connection = f.channel();
            } else {
                f.cause().printStackTrace();
            }
        });
        connect.channel().closeFuture().addListener(f -> {
            if (!closed) {
                if (task instanceof Retryable) {
                    Retryable retryable = (Retryable) task;
                    if (retryable.canRetry()) {
                        retryable.retry();
                    }
                } else {
                    task.failed();
                }
            }
        });
    }

    public void shutdown() {
        if (!closed) {
            closed = true;
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
            System.out.println("Connection " + connection + " closed");
        }
        group.shutdownGracefully();
    }

}
