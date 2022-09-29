package com.github.qlb;

public abstract class HttpTask implements JGetTask {
    protected Client client;

    public HttpTask() {
        this.client = new Client(this);
    }

    @Override
    public void start() {
        this.client.start();
    }

    public abstract Http getHttp();
}
