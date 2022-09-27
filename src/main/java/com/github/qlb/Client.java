package com.github.qlb;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public class Client {
    private final DownloadTask task;
    private DownloadSubTask subTask;
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private Channel connection;


    public Client(DownloadTask task) {
        this.task = task;
    }

    public Client(DownloadSubTask subTask) {
        this.task = subTask.getParent();
        this.subTask = subTask;
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
                            .build().newHandler(channel.alloc()));
                }
                pipeline.addLast(new HttpClientCodec())
                        .addLast(new ResponseHandler(task, subTask));

            }
        }).connect(http.getHost(), http.getPort()).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                System.out.println("connect success");
                connection = f.channel();
            } else {
                System.out.println("connection failed");
                f.cause().printStackTrace();
            }
        });
        connect.channel().closeFuture().addListener((ChannelFutureListener) channelFuture -> group.shutdownGracefully());
    }

    public void shutdown() {
        if (connection != null && connection.isOpen()) {
            connection.close();
            System.out.println("Connection " + connection + " closed");
        }
    }

}
