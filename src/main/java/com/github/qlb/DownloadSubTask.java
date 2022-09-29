package com.github.qlb;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DownloadSubTask extends HttpTask implements Retryable {
    private static final int MAX_RETRY = 3;
    private final DownloadTask parent;
    private final Range range;
    private volatile ByteChannel targetFileChannel;
    private boolean finished;
    private final String name = UUID.randomUUID().toString();
    private volatile ScheduledFuture<?> idleProcess;
    private long readBytes;
    private int retry;

    public DownloadSubTask(DownloadTask parent, Range range) throws IOException {
        this.parent = parent;
        this.range = range;
        this.finished = false;
        this.client = new Client(this);
        this.targetFileChannel = createTempFile();
    }

    private ByteChannel createTempFile() throws IOException {
        File dir = new File(parent.targetFileDirectory());
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
        return Files.newByteChannel(new File(parent.targetFileDirectory(), name).toPath(),
                StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    @Override
    public String targetFileDirectory() {
        return parent.targetFileDirectory();
    }

    public void restart() {
        try {
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
        targetFileChannel = createTempFile();
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
        System.out.println("subtask " + this + " failed");
        parent.subTaskFailed(this);
    }

    @Override
    public void stop() {
        client.shutdown();
    }

    public boolean isFinished() {
        return finished;
    }

    public Range getRange() {
        return range;
    }

    public String getName() {
        return name;
    }

    public DownloadTask getParent() {
        return parent;
    }

    public long getReadBytes() {
        return readBytes;
    }

    public void receive(ChannelHandlerContext ctx, HttpContent httpContent) throws IOException {
        System.out.println(this +" received content!! ");
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
        if (readBytes == range.size() || httpContent instanceof LastHttpContent) {
            System.out.println("subtask finished");
            finished();
        } else {
            idleProcess = ctx.channel().eventLoop().schedule(this::processIdle, 2, TimeUnit.MINUTES);
        }
    }

    private void processIdle() {
        System.out.println("task " + this + " is idle, restart!");
        restart();
    }

    @Override
    public Http getHttp() {
        return parent.getHttp();
    }

    @Override
    public void retry() {
        retry ++;
        System.out.println("restart sub task " + this);
        restart();


    }

    @Override
    public boolean canRetry() {
        return retry < MAX_RETRY;
    }
}
