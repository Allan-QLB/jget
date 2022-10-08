package com.github.qlb;

import java.io.IOException;

public class SubTaskSnapshot {
    private final int index;
    private final Range range;
    private final long finishedSize;

    public SubTaskSnapshot(int index, Range range, long finishedSize) {
        this.index = index;
        this.range = range;
        this.finishedSize = finishedSize;
    }

    public DownloadSubTask recover(DownloadTask parent) throws IOException {
        return new DownloadSubTask(parent, index, range, finishedSize);
    }

    @Override
    public String toString() {
        return "SubTaskSnapshot{" +
                "index=" + index +
                ", range=" + range +
                ", finishedSize=" + finishedSize +
                '}';
    }
}
