package com.github.qlb;

import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;

public class DownloadTask {
    private State state;
    private final Http http;
    private final List<DownloadSubTask> subTasks = new ArrayList<>();

    public DownloadTask(String url) {
        this.http = new Http(url);
        this.state = State.created;
    }

    enum State {
        created, init, started, finished
    }

    public void subTaskFinished() {
        for (DownloadSubTask subTask : subTasks) {
            if (!subTask.isFinished()) {
                return;
            }
        }
        System.out.println("task " + this + " finish");
        state = State.finished;
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

}
