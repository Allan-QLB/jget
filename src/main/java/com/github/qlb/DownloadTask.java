package com.github.qlb;

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

public class DownloadTask extends HttpTask {
    private static final int TEMP_FILE_READ_BATCH_SIZE = 8192;
    private static final long NO_SIZE = 0;
    private static final String DEFAULT_DIR = Optional.ofNullable(System.getenv("HOME"))
            .orElse(System.getenv("HOMEPATH"));
    private State state;
    private final Http http;
    private final String targetDirectory;
    private final List<DownloadSubTask> subTasks = new ArrayList<>();
    private long totalSize = NO_SIZE;
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
        failed,
        finished
    }

    enum Unit {
        B(1), KB(1 << 10), MB(1 << 20), GB(1 << 30);
        private final int factor;
        Unit(final int factor) {
            this.factor = factor;
        }
    }

    public void subTaskFinished() {
        for (DownloadSubTask subTask : subTasks) {
            if (!subTask.isFinished()) {
                return;
            }
        }
        finished();
    }

    private void mergeTempFiles() {
        try (SeekableByteChannel target = Files.newByteChannel(new File(targetFileDirectory(), http.getFileName()).toPath(),
                StandardOpenOption.WRITE, StandardOpenOption.CREATE)){
            for (DownloadSubTask subTask : subTasks) {
                File tempFile = new File(targetFileDirectory(), subTask.getName());
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
            final long size = subTask.getRange().size();
            if (size > 0) {
                totalSize += size;
            }
        } else {
            throw new IllegalStateException("Can not add subtask on state " + state);
        }
    }

    public boolean isFinished() {
        return state == State.finished;
    }

    @Override
    public Http getHttp() {
        return http;
    }

    public void startSubTasks() {
        if (state == State.started || state == State.finished) {
            return;
        }
        for (DownloadSubTask subTask : subTasks) {
            subTask.start();
        }
        state = State.started;
        System.out.println("Download Task " + this + "start");
        progress = registerTimer(this::printProgress, 1, 1, TimeUnit.SECONDS);
        disconnect();
    }

    public void discarded() {
        disconnect();
    }

    public void disconnect() {
        this.client.shutdown();
    }

    @Override
    public void finished() {
        mergeTempFiles();
        state = State.finished;
        TIMER_EXECUTOR.shutdown();
    }

    @Override
    public void failed() {
        System.out.println("task " + this + " failed");
        stop();
        TIMER_EXECUTOR.shutdown();
        disconnect();
        state = State.failed;
    }

    @Override
    public void stop() {
        for (DownloadSubTask subTask : subTasks) {
            subTask.stop();
        }
    }

    public void subTaskFailed(DownloadSubTask subTask) {
        failed();
    }

    @Override
    public String targetFileDirectory() {
        return targetDirectory != null ? targetDirectory : DEFAULT_DIR;
    }

    public synchronized void reportRead(long readBytes) {
        //totalRead += readBytes;
    }

    private long totalRead() {
        long totalRead = 0L;
        for (DownloadSubTask subTask : subTasks) {
            totalRead += subTask.getReadBytes();
        }
        return totalRead;
    }

    private void printProgress() {
        Unit[] values = Unit.values();
        double readUnited = 0D;
        Unit unit = Unit.B;
        final long totalRead = totalRead();
        for (int i = values.length - 1; i >= 0; i--) {
            readUnited = totalRead * 1.0 / values[i].factor;
            if (readUnited >= 0.5) {
                unit = values[i];
                break;
            }
        }
        if (totalSize != NO_SIZE) {
            System.out.printf("\r%d%%, transferred: %.2f%s%n", totalRead * 100 / totalSize, readUnited, unit);
        } else {
            System.out.printf("transferred: %.2f%s%n", readUnited, unit);
        }
        if (isFinished()) {
            progress.cancel(false);
        }
    }

}
