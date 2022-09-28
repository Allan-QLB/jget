package com.github.qlb;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class DownloadSubTask implements HttpTask {
    private static final int MAX_RETRY = 3;
    private final DownloadTask parent;
    private final Range range;
    private volatile ByteChannel targetFileChannel;
    private boolean finished;
    private final String name = UUID.randomUUID().toString();
    private long readBytes;
    private final Client client;
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

    public void start() {
        this.client.start();
    }

    @Override
    public void restart() {
        try {
            targetFileChannel.close();
            targetFileChannel = createTempFile();
            start();
        } catch (IOException e) {
            e.printStackTrace();
            failed();
        }

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
        if (++retry <= MAX_RETRY) {
            System.out.println("restart sub task " + this);
            restart();
        } else {
            parent.subTaskFailed(this);
        }
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

    public void receive(HttpContent httpContent) throws IOException {
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
        }
    }

    @Override
    public Http getHttp() {
        return parent.getHttp();
    }
}
