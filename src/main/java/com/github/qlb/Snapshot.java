package com.github.qlb;

import java.io.IOException;

public interface Snapshot {
    String id();
    JGetTask recover() throws IOException;
}
