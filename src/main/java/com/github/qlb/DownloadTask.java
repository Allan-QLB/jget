package com.github.qlb;

import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.cli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DownloadTask {
    public static final ScheduledExecutorService PROGRESS_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static final int TEMP_FILE_READ_BATCH_SIZE = 8192;
    private static final String DEFAULT_DIR = Optional.ofNullable(System.getenv("HOME"))
            .orElse(System.getenv("HOMEPATH"));
    private State state;
    private final Http http;
    private final String targetDirectory;
    private final List<DownloadSubTask> subTasks = new ArrayList<>();
    private long totalSize;
    private volatile long totalRead;
    private ScheduledFuture<?> progress;

    public DownloadTask(CommandLine cli) {
        this(cli.getOptionValue(DownloadOptions.URL),
                cli.getOptionValue(DownloadOptions.HOME_DIR, DEFAULT_DIR) + File.separator + "jget" + File.separator + "download");

    }

    public DownloadTask(String url, String targetDirectory) {
        this.http = new Http(url);
        this.targetDirectory = targetDirectory;
        this.state = State.created;
    }

    enum State {
        created,
        init,
        started,
        finished
    }

    enum Unit {
        B(1), KB(1 << 10), MB(1 << 20), GB(1 << 30);
        private final int factor;
        private Unit(final int factor) {
            this.factor = factor;
        }
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
            System.out.println("merge temp files finish");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initiated() {
        state = State.init;
    }

    void addSubTask(DownloadSubTask subTask) {
        if (state == State.created) {
            subTasks.add(subTask);
            totalSize += subTask.getRange().size();
        } else {
            throw new IllegalStateException("Can not add subtask on state " + state);
        }
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
        for (DownloadSubTask subTask : subTasks) {
            subTask.start();
        }
        state = State.started;
        System.out.println("Download Task " + this + "start");
        progress = PROGRESS_EXECUTOR.scheduleAtFixedRate(this::printProgress, 1, 1, TimeUnit.SECONDS);
    }

    public String fileDirectory() {
        return targetDirectory != null ? targetDirectory : DEFAULT_DIR;
    }

    public synchronized void reportRead(long readBytes) {
        totalRead += readBytes;
    }

    private void printProgress() {
        Unit[] values = Unit.values();
        double readUnited = 0D;
        Unit unit = Unit.B;
        for (int i = values.length - 1; i >= 0; i--) {
            readUnited = totalRead * 1.0 / values[i].factor;
            if (readUnited >= 0.5) {
                unit = values[i];
                break;
            }
        }
        System.out.printf("\r%.2f, transferred: %.2f%s%n", totalRead * 1.0 / totalSize, readUnited, unit);
        if (isFinished()) {
            progress.cancel(false);
        }
    }

}
