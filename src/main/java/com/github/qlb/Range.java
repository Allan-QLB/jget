package com.github.qlb;

public class Range {
    private final long start;
    private final long end;

    public Range(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public long size() {
        return end - start + 1;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }
}
