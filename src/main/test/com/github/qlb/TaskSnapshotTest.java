package com.github.qlb;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TaskSnapshotTest {
    public static final String TEST_DB = "jgettest";

    @Test
    void testPersistLoad() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        TaskSnapshot snapshot = new TaskSnapshot("a", "b", 100L, "world", "hello", now);
        Snapshots.persist(TEST_DB, snapshot);
        TaskSnapshot load = Snapshots.load(TEST_DB, "a");
        assertNotNull(load);
        assertEquals("a", load.getTaskId());
        assertEquals("b", load.getUrl());
        assertEquals(100L, load.getTotalSize());
        assertEquals("world", load.getFileDirectory());
        assertEquals("hello", load.getFileName());
        assertEquals(now, load.getCreateTime());
        assertNotNull(load.getSubtasks());
        assertTrue(load.getSubtasks().isEmpty());
    }

}