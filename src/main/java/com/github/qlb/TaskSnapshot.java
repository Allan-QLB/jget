package com.github.qlb;


import java.time.LocalDateTime;
import java.util.*;



public class TaskSnapshot implements Snapshot {
    private final String taskId;
    private final String url;
    private final long totalSize;
    private final String fileDirectory;
    private final String fileName;
    private LocalDateTime createTime;
    private final List<SubTaskSnapshot> subtasks = new ArrayList<>();

    public TaskSnapshot(String taskId,
                        String url,
                        long totalSize,
                        String fileDirectory,
                        String fileName,
                        LocalDateTime createTime) {
        this.taskId = taskId;
        this.url = url;
        this.totalSize = totalSize;
        this.fileDirectory = fileDirectory;
        this.fileName = fileName;
        this.createTime = createTime;
    }


    @Override
    public String id() {
        return getTaskId();
    }

    @Override
    public DownloadTask recover() {
        return DownloadTask.recoverFromSnapshot(this);
    }

    public String getTaskId() {
        return taskId;
    }

    public String getUrl() {
        return url;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public String getFileDirectory() {
        return fileDirectory;
    }

    public String getFileName() {
        return fileName;
    }

    public List<SubTaskSnapshot> getSubtasks() {
        return subtasks;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "TaskSnapshot{" +
                "taskId='" + taskId + '\'' +
                ", url='" + url + '\'' +
                ", totalSize=" + totalSize +
                ", fileDirectory='" + fileDirectory + '\'' +
                ", fileName='" + fileName + '\'' +
                ", subtasks=" + subtasks +
                '}';
    }
}
