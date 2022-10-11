package com.github.qlb;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class HttpHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static final Logger LOG = LoggerFactory.getLogger(HttpHandler.class);
    private final HttpTask task;
    private Throwable error;
    public HttpHandler(HttpTask task) {
        this.task = task;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpContent) {
                handleContent(ctx, (HttpContent) msg);
            } else if (msg instanceof HttpResponse) {
                handleResponse(ctx, (HttpResponse) msg);
            } else {
                throw new IllegalStateException("Unexpected message type " + msg.getClass());
            }
    }

    private void handleResponse(ChannelHandlerContext ctx, HttpResponse response) throws IOException {
        LOG.info("{} receive response {}", task, response.status());
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
                fullTask.ready();
            } else {
                task.getHttp().setResponseHeaders(headers);
            }
        } else if (response.status() == HttpResponseStatus.PARTIAL_CONTENT) {
            LOG.info("receive partial content, task {}", task);
            task.getHttp().setResponseHeaders(headers);
        } else if ((response.status() == HttpResponseStatus.FOUND || response.status() == HttpResponseStatus.MOVED_PERMANENTLY)
                && task instanceof DownloadTask) {
            String location = response.headers().get(HttpHeaderNames.LOCATION);
            if (StringUtil.isNullOrEmpty(location)) {
                throw new IllegalStateException("Location is absent in response headers");
            }
            ((DownloadTask) task).discarded();
            LOG.info("redirect to {}, task {}", location, task);
            new DownloadTask(location, task.targetFileDirectory()).start();
        } else {
            throw new IllegalStateException("unexpected response " + response.status());
        }
    }

    private void handleContent(ChannelHandlerContext ctx, HttpContent content) throws IOException {
        if (error != null) {
            LOG.warn("ignore content because there is error occurred, task {}", task, error);
            return;
        }
        if (task instanceof DownloadSubTask) {
            ((DownloadSubTask) task).receive(ctx, content);
        }
    }

    private void allocateSubTasks(DownloadTask task, long totalLen, int numSubTasks) throws IOException {
        long size = totalLen / numSubTasks;
        for (int i = 0; i < numSubTasks; i++) {
            if (i != numSubTasks - 1) {
               new DownloadSubTask(task, i, new Range(i * size, (i + 1) * size - 1)).ready();
            } else {
                new DownloadSubTask(task, i, new Range(i * size, totalLen - 1)).ready();
            }
        }
    }

    private void sendRequest(ChannelHandlerContext ctx, Http http) {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, http.getUrl());
        request.headers().set(HttpHeaderNames.HOST, http.getHost());
        if (task instanceof DownloadSubTask) {
            request.setMethod(HttpMethod.GET);
            final Range range = ((DownloadSubTask) task).getRange();
            long readBytes = ((DownloadSubTask) task).getReadBytes();
            if (task.isFinished()) {
                return;
            } else if (range.size() > 0 && readBytes < range.size()) {
                request.headers().set(HttpHeaderNames.RANGE,
                        String.format("bytes=%s-%s", range.getStart() + readBytes, range.getEnd()));
            }
        }
        LOG.info("send request , url {}, task {}", request.uri(), task);
        ctx.writeAndFlush(request);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("exception caught, task {}", task, error);
        error = cause;
        task.failed();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
       sendRequest(ctx, task.getHttp());
    }

}
