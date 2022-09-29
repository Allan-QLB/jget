package com.github.qlb;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface JGetTask {
    ScheduledExecutorService TIMER_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    String targetFileDirectory();
    void start();
    void finished();
    void failed();
    void stop();
    default ScheduledFuture<?> registerTimer(Runnable task, long delay, long period, TimeUnit unit) {
        return TIMER_EXECUTOR.scheduleAtFixedRate(task, delay, period, unit);
    }
}
