package com.github.qlb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class DownloadTask {
    private static final int TEMP_FILE_READ_BATCH_SIZE = 8192;
    private static final String DEFAULT_DIR = System.getenv("HOME")
            + File.separator + "jget" + File.separator + "download";
    private State state;
    private final Http http;
    private final List<DownloadSubTask> subTasks = new ArrayList<>();

    public DownloadTask(String url) {
        this.http = new Http(url);
        this.state = State.created;
    }

    enum State {
        created,
        init,
        started,
        finished
    }

    public void subTaskFinished() {
        for (DownloadSubTask subTask : subTasks) {
            if (!subTask.isFinished()) {
                return;
            }
        }
        mergeTempFiles();
        state = State.finished;
    }

    private void mergeTempFiles() {
        try (SeekableByteChannel target = Files.newByteChannel(new File(fileDirectory(), http.getFileName()).toPath(),
                StandardOpenOption.WRITE, StandardOpenOption.CREATE)){
            for (DownloadSubTask subTask : subTasks) {
                File tempFile = new File(fileDirectory(), subTask.getName());
                byte[] buffer = new byte[TEMP_FILE_READ_BATCH_SIZE];
                try (InputStream input = Files.newInputStream(tempFile.toPath(), StandardOpenOption.READ)) {
                    int readBytes;
                    while ((readBytes = input.read(buffer)) > 0) {
                        target.write(ByteBuffer.wrap(buffer, 0, readBytes));
                    }

                }
                Files.delete(tempFile.toPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initiated() {
        state = State.init;
    }

    void addSubTask(DownloadSubTask subTask) {
        subTasks.add(subTask);
    }

    public boolean isFinished() {
        return state == State.finished;
    }

    public Http getHttp() {
        return http;
    }

    public void start() {
        if (state == State.started || state == State.finished) {
            return;
        }
        state = State.started;
        for (DownloadSubTask subTask : subTasks) {
            subTask.start();
        }
    }

    public String fileDirectory() {
        return DEFAULT_DIR;
    }

}
