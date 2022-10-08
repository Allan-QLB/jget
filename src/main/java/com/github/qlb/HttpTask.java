package com.github.qlb;

import java.time.LocalDateTime;

public abstract class HttpTask implements JGetTask {
    protected Client client;
    protected LocalDateTime createTime;

    public HttpTask() {
        this.client = new Client(this);
        this.createTime = LocalDateTime.now();
    }

    @Override
    public void start() {
        if (!isFinished()) {
            this.client.start();
        } else {
            throw new IllegalStateException("task " + this + " is already finished");
        }
    }

    @Override
    public LocalDateTime createTime() {
        return createTime;
    }

    public abstract Http getHttp();
}
