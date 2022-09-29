package com.github.qlb;

public interface Retryable {
    void retry();
    boolean canRetry();
}

