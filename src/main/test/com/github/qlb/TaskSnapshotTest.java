package com.github.qlb;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TaskSnapshotTest {
    public static final String TEST_DB = "jgettest";

    @Test
    void testPersistLoad() throws IOException {
        TaskSnapshot snapshot = new TaskSnapshot("a", "b", 100L, "world", "hello");
        Snapshots.persist(TEST_DB, snapshot);
        TaskSnapshot load = Snapshots.load(TEST_DB, "a");
        assertNotNull(load);
        assertEquals("a", load.getTaskId());
        assertEquals("b", load.getUrl());
        assertEquals(100L, load.getTotalSize());
        assertEquals("world", load.getFileDirectory());
        assertEquals("hello", load.getFileName());
        assertNotNull(load.getSubtasks());
        assertTrue(load.getSubtasks().isEmpty());
    }

}