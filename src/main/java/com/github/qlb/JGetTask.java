package com.github.qlb;

import java.io.IOException;

public interface JGetTask {
    String targetFileDirectory();
    void start();
    void restart() throws IOException;
    void finished();
    void failed();
    void stop();
}
