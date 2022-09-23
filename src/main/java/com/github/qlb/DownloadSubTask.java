package com.github.qlb;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class DownloadSubTask {
    private final DownloadTask parent;
    private final Range range;
    private final ByteChannel targetFileChannel;
    private boolean finished;
    private final String name = UUID.randomUUID().toString();
    private long readBytes;
    private final Client client;

    public DownloadSubTask(DownloadTask parent, Range range) throws IOException {
        this.parent = parent;
        this.range = range;
        this.finished = false;
        this.client = new Client(this);
        this.targetFileChannel = createTempFile();
    }

    private ByteChannel createTempFile() throws IOException {
        File dir = new File(parent.fileDirectory());
        if (!dir.exists()) {
            Files.createDirectory(dir.toPath());
        }
        return Files.newByteChannel(new File(parent.fileDirectory(), name).toPath(),
                StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }
    public void start() {
        this.client.start();
    }

    public void finished() {
        try {
            targetFileChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.finished = true;
        parent.subTaskFinished();
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

    public void accept(ByteBuffer content) throws IOException {
        readBytes += targetFileChannel.write(content);
        if (readBytes == (range.getEnd() -  range.getStart() + 1)) {
            System.out.println("subtask finished");
            finished();
        }
    }
}
