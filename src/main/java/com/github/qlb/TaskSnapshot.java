package com.github.qlb;


import java.io.IOException;
import java.util.*;



public class TaskSnapshot implements Snapshot {
    private final String taskId;
    private final String url;
    private final long totalSize;
    private final String fileDirectory;
    private final String fileName;
    private final List<SubTaskSnapshot> subtasks = new ArrayList<>();

    public TaskSnapshot(String taskId, String url, long totalSize, String fileDirectory, String fileName) {
        this.taskId = taskId;
        this.url = url;
        this.totalSize = totalSize;
        this.fileDirectory = fileDirectory;
        this.fileName = fileName;
    }


    @Override
    public String id() {
        return getTaskId();
    }

    @Override
    public DownloadTask recover() throws IOException {
        DownloadTask task = new DownloadTask(taskId, url, fileDirectory);
        for (SubTaskSnapshot subtask : subtasks) {
            task.addSubTask(subtask.recover(task));
        }
        return task;
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
