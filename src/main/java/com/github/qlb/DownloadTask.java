package com.github.qlb;

import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DownloadTask extends HttpTask implements SnapshottingTask {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadTask.class);
    private static final String DEFAULT_DIR = Optional.ofNullable(System.getenv("HOME"))
            .orElse(System.getenv("HOMEPATH"));
    private final String id;
    private State state;
    private final Http http;
    private final String targetDirectory;
    private final List<DownloadSubTask> subTasks = new ArrayList<>();
    private long totalSize = UNKNOWN_TOTAL_SIZE;
    private SeekableByteChannel tmpFile;

    public DownloadTask(CommandLine cli) {
        this(cli.getOptionValue(DownloadOptions.URL),
                cli.getOptionValue(DownloadOptions.HOME_DIR, DEFAULT_DIR) + File.separator + "jget" + File.separator + "download");
    }

    public DownloadTask(String url, String targetDirectory) {
        this.id = UUID.randomUUID().toString();
        this.http = new Http(url);
        this.targetDirectory = targetDirectory;
        this.state = State.created;
    }

    private DownloadTask(String id, String url, String targetDirectory, LocalDateTime createTime) {
        this.id = id;
        this.http = new Http(url);
        this.targetDirectory = targetDirectory;
        this.state = State.created;
        this.createTime = createTime;
    }

    public static DownloadTask recoverFromSnapshot(@Nonnull TaskSnapshot snapshot) {
        DownloadTask task = new DownloadTask(snapshot.getTaskId(), snapshot.getUrl(), snapshot.getFileDirectory(), snapshot.getCreateTime());
        for (SubTaskSnapshot subtask : snapshot.getSubtasks()) {
            task.addSubTask(subtask.recover(task));
        }
        return task;
    }

    @Override
    public String id() {
        return id;
    }

    enum State {
        created,
        ready,
        started,
        failed,
        finished
    }

    public void subTaskFinished() {
        for (DownloadSubTask subTask : subTasks) {
            if (!subTask.isFinished()) {
                return;
            }
        }
        finished();
    }

    private void renameTempFiles() {
        try {
            tmpFile.close();
            Files.move(tmpFilePath(), new File(targetFileDirectory(), targetFileName()).toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    @Override
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
            if (!subTask.isFinished()) {
                subTask.setTargetFileChannel(tmpFile);
                subTask.start();
            }
        }
        state = State.started;
        LOG.info("Start task {}", this);
        TaskManager.INSTANCE.addTask(this);
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
        renameTempFiles();
        state = State.finished;
        TaskManager.INSTANCE.remove(this);
    }

    @Override
    public void failed() {
        LOG.error("task {} failed", this);
        stop();
        disconnect();
        if (tmpFile != null && tmpFile.isOpen()) {
            try {
                tmpFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        TaskManager.INSTANCE.removeActive(id);
        state = State.failed;
    }

    @Override
    public void ready() {
        try {
            tmpFile = createTempFile();
            state = State.ready;
            startSubTasks();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleted() {
        try {
            Files.delete(tmpFilePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private SeekableByteChannel createTempFile() throws IOException {
        File dir = new File(targetFileDirectory());
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
        return Files.newByteChannel(new File(dir, id).toPath(),
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE);
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

    @Override
    public String targetFileName() {
        return http.getFileName();
    }

    @Override
    public int finishedPercent() {
        if (totalSize == UNKNOWN_TOTAL_SIZE) {
            return UNKNOWN_PERCENT;
        }
        return (int) (getReadBytes() * 100/ totalSize);
    }

    public synchronized void reportRead(long readBytes) {
        //totalRead += readBytes;
    }

    @Override
    public long getReadBytes() {
        long totalRead = 0L;
        for (DownloadSubTask subTask : subTasks) {
            totalRead += subTask.getReadBytes();
        }
        return totalRead;
    }

    @Override
    public long getTotalBytes() {
        return totalSize;
    }

    @Override
    public TaskSnapshot snapshot() {
        final TaskSnapshot taskSnapshot = new TaskSnapshot(id, getHttp().getUrl(), totalSize,
                targetFileDirectory(), http.getFileName(), createTime);
        for (DownloadSubTask subTask : subTasks) {
            taskSnapshot.getSubtasks().add(subTask.snapshot());
        }
        return taskSnapshot;
    }

    private Path tmpFilePath() {
        return new File(targetFileDirectory(), id).toPath();
    }

    @Override
    public String toString() {
        return "DownloadTask{" +
                "state=" + state +
                ", http=" + http +
                ", targetDirectory='" + targetDirectory + '\'' +
                ", subTasks=" + subTasks +
                ", totalSize=" + totalSize +
                '}';
    }
}
