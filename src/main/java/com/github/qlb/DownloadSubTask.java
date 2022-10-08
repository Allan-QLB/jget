package com.github.qlb;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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

    public DownloadSubTask(DownloadTask parent, int index, Range range) throws IOException {
        this.parent = parent;
        this.index = index;
        this.range = range;
        this.finished = false;
        this.client = new Client(this);
        this.targetFileChannel = createTempFile(readBytes);
    }

    public DownloadSubTask(DownloadTask parent, int index, Range range, long readBytes) throws IOException {
        this.parent = parent;
        this.index = index;
        this.range = range;
        if (range.size() > 0 && readBytes >= range.size()) {
            this.finished = true;
        } else {
            this.finished = false;
        }
        this.client = new Client(this);
        this.targetFileChannel = createTempFile(readBytes);
        this.readBytes = readBytes;
    }

    private SeekableByteChannel createTempFile(long position) throws IOException {
        File dir = new File(parent.targetFileDirectory());
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
        return Files.newByteChannel(new File(dir, targetFileName()).toPath(),
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE)
                .position(position);
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
        if (targetFileChannel != null && targetFileChannel.isOpen()) {
            targetFileChannel.close();
        }
        targetFileChannel = createTempFile(0);
        readBytes = 0;
    }

    public void finished() {
        try {
            targetFileChannel.close();
            client.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        do {
            written += targetFileChannel.write(contentBuffer);
        } while (written < remaining);
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
