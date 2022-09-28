package com.github.qlb;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.StringUtil;

import java.io.IOException;


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
                handleContent((HttpContent) msg);
            } else if (msg instanceof HttpResponse) {
                handleResponse(ctx, (HttpResponse) msg);
            } else {
                System.out.println(msg);
            }
    }

    private void handleResponse(ChannelHandlerContext ctx, HttpResponse response) throws IOException {
        final HttpHeaders headers = response.headers();
        if (response.status() == HttpResponseStatus.OK) {
            if (subTask == null) {
                long contentLength = Long.parseLong(headers.get(HttpHeaderNames.CONTENT_LENGTH, "-1"));
                if (headers.contains(HttpHeaderNames.ACCEPT_RANGES)) {
                    allocateSubTasks(contentLength, Runtime.getRuntime().availableProcessors());
                } else {
                    allocateSubTasks(contentLength, 1);
                }
                ctx.close();
                task.start();
            } else {
                task.getHttp().setResponseHeaders(headers);
            }
        } else if (response.status() == HttpResponseStatus.PARTIAL_CONTENT) {
            System.out.println("receive partial");
        } else if (response.status() == HttpResponseStatus.FOUND
                || response.status() == HttpResponseStatus.MOVED_PERMANENTLY) {
            String location = response.headers().get(HttpHeaderNames.LOCATION);
            ctx.close();
            if (StringUtil.isNullOrEmpty(location)) {
                throw new IllegalStateException("Location is absent in response headers");
            }
            new Client(new DownloadTask(location, task.fileDirectory())).start();
        } else {
            System.out.println("failed " + response);
            ctx.close();
        }
    }

    private void handleContent(HttpContent content) throws IOException {
        if (subTask != null) {
            subTask.accept(content);
        }
    }

    private void allocateSubTasks(long totalLen, int numSubTasks) throws IOException {
        long size = totalLen / numSubTasks;
        for (int i = 0; i < numSubTasks; i++) {
            if (i != numSubTasks - 1) {
                task.addSubTask(new DownloadSubTask(task, new Range(i * size, (i + 1) * size - 1)));
            } else {
                task.addSubTask(new DownloadSubTask(task, new Range(i * size, totalLen - 1)));
            }
        }
    }

    private void sendRequest(ChannelHandlerContext ctx) {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, task.getHttp().getUrl());
        request.headers().set(HttpHeaderNames.HOST, task.getHttp().getHost());
        if (subTask != null) {
            request.setMethod(HttpMethod.GET);
            request.headers().set(HttpHeaderNames.RANGE, String.format("bytes=%s-%s", subTask.getRange().getStart(), subTask.getRange().getEnd()));
        }
//        System.out.println(request);
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
