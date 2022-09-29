package com.github.qlb;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.StringUtil;

import java.io.IOException;


public class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
    private final HttpTask task;

    public ResponseHandler(HttpTask task) {
        this.task = task;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpContent) {
                handleContent(ctx, (HttpContent) msg);
            } else if (msg instanceof HttpResponse) {
                handleResponse(ctx, (HttpResponse) msg);
            } else {
                System.out.println(msg);
            }
    }

    private void handleResponse(ChannelHandlerContext ctx, HttpResponse response) throws IOException {
        System.out.println(task + " receive response " + response.status().toString());
        final HttpHeaders headers = response.headers();
        if (response.status() == HttpResponseStatus.OK) {
            if (task instanceof DownloadTask) {
                DownloadTask fullTask = (DownloadTask) task;
                long contentLength = Long.parseLong(headers.get(HttpHeaderNames.CONTENT_LENGTH, "0"));
                if (headers.contains(HttpHeaderNames.ACCEPT_RANGES)) {
                    allocateSubTasks(fullTask, contentLength, Runtime.getRuntime().availableProcessors());
                } else {
                    allocateSubTasks(fullTask, 0, 1);
                }
                fullTask.startSubTasks();
            } else {
                task.getHttp().setResponseHeaders(headers);
            }
        } else if (response.status() == HttpResponseStatus.PARTIAL_CONTENT) {
            System.out.println("receive partial");
            task.getHttp().setResponseHeaders(headers);
        } else if ((response.status() == HttpResponseStatus.FOUND || response.status() == HttpResponseStatus.MOVED_PERMANENTLY)
                && task instanceof DownloadTask) {
            String location = response.headers().get(HttpHeaderNames.LOCATION);
            if (StringUtil.isNullOrEmpty(location)) {
                throw new IllegalStateException("Location is absent in response headers");
            }
            ((DownloadTask) task).discarded();
            System.out.println("redirect to " + location);
            new DownloadTask(location, task.targetFileDirectory()).start();
        } else {
            System.out.println("failed " + response);
            task.failed();
        }
    }

    private void handleContent(ChannelHandlerContext ctx, HttpContent content) throws IOException {
        if (task instanceof DownloadSubTask) {
            ((DownloadSubTask) task).receive(ctx, content);
        }
    }

    private void allocateSubTasks(DownloadTask task, long totalLen, int numSubTasks) throws IOException {
        long size = totalLen / numSubTasks;
        for (int i = 0; i < numSubTasks; i++) {
            if (i != numSubTasks - 1) {
                task.addSubTask(new DownloadSubTask(task, new Range(i * size, (i + 1) * size - 1)));
            } else {
                task.addSubTask(new DownloadSubTask(task, new Range(i * size, totalLen - 1)));
            }
        }
    }

    private void sendRequest(ChannelHandlerContext ctx, Http http) {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, http.getUrl());
        request.headers().set(HttpHeaderNames.HOST, http.getHost());
        if (task instanceof DownloadSubTask) {
            request.setMethod(HttpMethod.GET);
            final Range range = ((DownloadSubTask) task).getRange();
            if (range.size() > 0) {
                request.headers().set(HttpHeaderNames.RANGE,
                        String.format("bytes=%s-%s", range.getStart(), range.getEnd()));
            }
        }
        System.out.println("send request " +  request);
        ctx.writeAndFlush(request);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
       sendRequest(ctx, task.getHttp());
    }

}
