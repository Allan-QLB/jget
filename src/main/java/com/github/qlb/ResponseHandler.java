package com.github.qlb;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;



import java.nio.charset.StandardCharsets;


public class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final DownloadTask task;
    private final DownloadSubTask subTask;

    public ResponseHandler(DownloadTask task, DownloadSubTask subTask) {
        this.task = task;
        this.subTask = subTask;
    }



    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpContent) {
                if (subTask != null) {
                    subTask.accept(((HttpContent) msg).content().nioBuffer());
                }
            } else if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                final HttpHeaders headers = response.headers();
                if (response.status().code() == 200) {
                    System.out.println("response: " + response);
                    long contentLength = headers.getInt(HttpHeaderNames.CONTENT_LENGTH);
                    if (subTask == null && headers.contains(HttpHeaderNames.ACCEPT_RANGES)) {
                        ctx.close();
                        int np = 4;
                        long size = contentLength / np;
                        for (int i = 0; i < np; i++) {
                            if (i != np - 1) {
                                task.addSubTask(new DownloadSubTask(task, new Range(i * size, (i + 1) * size - 1)));
                            } else {
                                task.addSubTask(new DownloadSubTask(task, new Range(i * size, contentLength - 1)));
                            }
                        }
                    } else if (subTask == null && !headers.contains(HttpHeaderNames.ACCEPT_RANGES)) {
                        ctx.close();
                        task.addSubTask(new DownloadSubTask(task, new Range(0, contentLength)));
                    }
                    task.start();
                }
                else if (response.status().code() == 206){
                    System.out.println("receive partial");
                } else {
                    System.out.println("failed " + response);
                    ctx.close();

                }
            } else {
                System.out.println(msg);
            }
    }

    private void sendRequest(ChannelHandlerContext ctx) {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, task.getHttp().getUrl());
        request.headers().set(HttpHeaderNames.HOST, task.getHttp().getHost());
        if (subTask != null) {
            request.setMethod(HttpMethod.GET);
            request.headers().set(HttpHeaderNames.RANGE, String.format("bytes=%s-%s", subTask.getRange().getStart(), subTask.getRange().getEnd()));
        }
        System.out.println(request);
        ctx.writeAndFlush(request);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
       sendRequest(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

    }
}
