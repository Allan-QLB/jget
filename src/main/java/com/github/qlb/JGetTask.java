package com.github.qlb;

public interface JGetTask {
    String targetFileDirectory();
    void start();
    void finished();
    void failed();
    void stop();
}
