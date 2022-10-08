package com.github.qlb;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface JGetTask {
    int UNKNOWN_PERCENT = -1;
    ScheduledExecutorService TIMER_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    default String id() {
       return UUID.randomUUID().toString();
    }
    String targetFileDirectory();
    String targetFileName();
    default int finishedPercent() {
        return UNKNOWN_PERCENT;
    }
    void start();
    void ready();

    boolean isFinished();
    void finished();
    void failed();
    void stop();
    LocalDateTime createTime();
    default ScheduledFuture<?> registerTimer(Runnable task, long delay, long period, TimeUnit unit) {
        return TIMER_EXECUTOR.scheduleAtFixedRate(task, delay, period, unit);
    }

}
