package com.github.qlb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public enum TaskManager {
    INSTANCE;
    private static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);
    private final Map<String, SnapshottingTask> currentTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> periodicalSnapshotting;


    public void addTask(@Nonnull JGetTask task) {
        if (task instanceof SnapshottingTask) {
            currentTasks.put(task.id(), (SnapshottingTask) task);
            try {
                System.out.println("persist task " + task.id());
                Snapshots.persist(((SnapshottingTask) task).snapshot());
                if (periodicalSnapshotting == null) {
                    periodicalSnapshotting = scheduler.scheduleAtFixedRate(this::persistTasksPeriodically, 2, 2, TimeUnit.SECONDS);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void snapShotTasks() throws IOException {
        for (Snapshotting currentTask : currentTasks.values()) {
            Snapshot snapshot = currentTask.snapshot();
            Snapshots.persist(snapshot);
        }
    }

    public void loadTasks() throws IOException {
        Map<String, DownloadTask> map = new HashMap<>();
        for (TaskSnapshot taskSnapshot : Snapshots.loadAllTasks()) {
            DownloadTask recover = taskSnapshot.recover();
            if (map.put(recover.id(), recover) != null) {
                throw new IllegalStateException("Duplicate key");
            }
        }
        currentTasks.putAll(map);
    }

    public void printTasks() {
        try {
            loadTasks();
            List<SnapshottingTask> sortedTasks = currentTasks.values().stream()
                    .sorted(Comparator.comparing(JGetTask::createTime).reversed()).collect(Collectors.toList());
            for (int i = 0; i < sortedTasks.size(); i++) {
                SnapshottingTask task = sortedTasks.get(i);
                String summary = String.format("[%d]-[%s][%s][%s%%][%s]",
                        i+1,
                        task.id(),
                        task.targetFileName(),
                        task.finishedPercent() == JGetTask.UNKNOWN_PERCENT ? "-" : String.valueOf(task.finishedPercent()),
                        task.createTime()
                        );
                System.out.println(summary);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void resumeTask(int index) {
        try {
            loadTasks();
            List<SnapshottingTask> sortedTasks = currentTasks.values().stream()
                    .sorted(Comparator.comparing(JGetTask::createTime).reversed()).collect(Collectors.toList());
            if (index <= 0 || index > sortedTasks.size()) {
                throw new IllegalArgumentException("index should > 0 and <= " + sortedTasks.size());
            }
            for (int i = 0; i < currentTasks.size(); i++) {
                if (i + 1 == index) {
                    sortedTasks.get(i).ready();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void resumeTasks() {
        try {
            loadTasks();
            Iterator<Map.Entry<String, SnapshottingTask>> iterator = currentTasks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, SnapshottingTask> entry = iterator.next();
                entry.getValue().ready();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void remove(int index) {
        try {
            loadTasks();
            List<SnapshottingTask> sortedTasks = currentTasks.values().stream()
                    .sorted(Comparator.comparing(JGetTask::createTime).reversed()).collect(Collectors.toList());
            if (index <= 0 || index > sortedTasks.size()) {
                throw new IllegalArgumentException("index should > 0 and <= " + sortedTasks.size());
            }
            remove(sortedTasks.get(index - 1).id());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    public void remove(String id) {
        try {
            currentTasks.remove(id);
            Snapshots.remove(id);
            System.out.println("remove task " + id);
        } catch (IOException e) {
            LOG.error("Error remove task {} ", id, e);
        }
    }

    private void persistTasksPeriodically() {
        for (Map.Entry<String, SnapshottingTask> taskEntry : currentTasks.entrySet()) {
            try {
                LOG.debug("Persist task {} Periodically", taskEntry.getValue());
                //System.out.println("persist task " + taskEntry.getValue().id() +  " periodically");
                Snapshots.persist(taskEntry.getValue().snapshot());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void clearTasks() {
        try {
            loadTasks();
            for (Map.Entry<String, SnapshottingTask> taskEntry : currentTasks.entrySet()) {
                Snapshots.remove(taskEntry.getKey());
            }
            currentTasks.clear();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }
}
