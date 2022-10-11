package com.github.qlb;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DownloadSubTask extends HttpTask implements Retryable {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadSubTask.class);
    private static final int MAX_RETRY = 3;
    private final DownloadTask parent;
    private final Range range;
    private volatile SeekableByteChannel targetFileChannel;
    private boolean finished;
    private final int index;
    private volatile ScheduledFuture<?> idleProcess;
    private long readBytes;
    private int retry;

    public DownloadSubTask(DownloadTask parent, int index, Range range) {
        this.parent = parent;
        this.index = index;
        this.range = range;
        this.finished = false;
        this.client = new Client(this);
    }

    public DownloadSubTask(DownloadTask parent, int index, Range range, long readBytes) {
        this.parent = parent;
        this.index = index;
        this.range = range;
        this.finished = range.size() > 0 && readBytes >= range.size();
        this.client = new Client(this);
        this.readBytes = readBytes;
    }


    @Override
    public String targetFileDirectory() {
        return parent.targetFileDirectory();
    }

    public void restart() {
        try {
            LOG.info("restart subtask {}", this);
            reset();
            start();
        } catch (IOException e) {
            e.printStackTrace();
            failed();
        }
    }

    private void reset() throws IOException {
        readBytes = 0;
    }

    public void finished() {
        client.shutdown();
        this.finished = true;
        parent.subTaskFinished();
    }

    @Override
    public void failed() {
        if (canRetry()) {
            retry();
        } else {
            LOG.info("subtask {} failed", this);
            parent.subTaskFailed(this);
        }
    }

    @Override
    public void stop() {
        client.shutdown();
    }

    public SubTaskSnapshot snapshot() {
        return new SubTaskSnapshot(index, range, readBytes);
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    public Range getRange() {
        return range;
    }

    @Override
    public String targetFileName() {
        return parent.id() + "_" + index;
    }

    @Override
    public void ready() {
        parent.addSubTask(this);
    }

    @Override
    public long getReadBytes() {
        return readBytes;
    }

    public void receive(ChannelHandlerContext ctx, HttpContent httpContent) throws IOException {
        if (idleProcess != null && !idleProcess.isDone()) {
            idleProcess.cancel(false);
        }
        final ByteBuffer contentBuffer = httpContent.content().nioBuffer();
        int remaining = contentBuffer.remaining();
        int written = 0;
        synchronized (targetFileChannel) {
            targetFileChannel.position(range.getStart() + readBytes);
            do {
                written += targetFileChannel.write(contentBuffer);
            } while (written < remaining);
        }
        readBytes += remaining;
        parent.reportRead(remaining);
        if (httpContent instanceof LastHttpContent) {
            if (range.size() > 0 && range.size() != readBytes) {
                LOG.error("subtask {} read bytes not match range size!", this);
                failed();
            } else {
                LOG.info("subtask {}, finished", this);
                finished();
            }
        } else {
            idleProcess = ctx.channel().eventLoop().schedule(this::processIdle, 2, TimeUnit.MINUTES);
        }
    }

    private void processIdle() {
        LOG.warn("subtask {} is idle, will restart", this);
        restart();
    }

    @Override
    public Http getHttp() {
        return parent.getHttp();
    }

    public void setTargetFileChannel(SeekableByteChannel targetFileChannel) {
        this.targetFileChannel = targetFileChannel;
    }

    @Override
    public void retry() {
        retry ++;
        LOG.info("retry subtask {}, retry {}", this, retry);
        restart();
    }

    @Override
    public boolean canRetry() {
        return retry < MAX_RETRY;
    }

    @Override
    public String toString() {
        return "DownloadSubTask{" +
                "parent=" + parent.id() +
                ", range=" + range +
                ", targetFileChannel=" + targetFileChannel +
                ", finished=" + finished +
                ", index=" + index +
                ", idleProcess=" + idleProcess +
                ", readBytes=" + readBytes +
                ", retry=" + retry +
                '}';
    }
}
