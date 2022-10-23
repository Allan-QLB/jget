package com.github.qlb;

import java.io.IOException;

public interface Snapshot {
    String id();
    SnapshottingTask recover() throws IOException;
}
