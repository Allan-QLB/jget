package com.github.qlb;

import java.time.LocalDateTime;
import java.util.UUID;


public interface JGetTask {
    int UNKNOWN_PERCENT = -1;
    long UNKNOWN_TOTAL_SIZE = 0;
    default String id() {
       return UUID.randomUUID().toString();
    }
    String targetFileDirectory();
    String targetFileName();
    default int finishedPercent() {
        return UNKNOWN_PERCENT;
    }

    default long getReadBytes() {
        return 0;
    }

    default long getTotalBytes() {
        return UNKNOWN_TOTAL_SIZE;
    }

    default void deleted() {

    }

    void start();
    void ready();

    boolean isFinished();
    void finished();
    void failed();
    void stop();
    LocalDateTime createTime();


}
