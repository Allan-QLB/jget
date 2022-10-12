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
    private final Map<String, JGetTask> activeTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> periodicalSnapshotting;
    private volatile ScheduledFuture<?> periodicalShowProgress;

    {
        try {
            loadTasks();
        } catch (IOException e) {
           throw new RuntimeException(e);
        }
    }


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
        activeTasks.put(task.id(), task);
        if (periodicalShowProgress == null) {
            periodicalShowProgress = scheduler.scheduleAtFixedRate(this::showActiveTaskProgress, 1, 1, TimeUnit.SECONDS);
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
        List<SnapshottingTask> sortedTasks = currentTasks.values().stream()
                .sorted(Comparator.comparing(JGetTask::createTime).reversed()).collect(Collectors.toList());
        for (int i = 0; i < sortedTasks.size(); i++) {
            SnapshottingTask task = sortedTasks.get(i);
            String summary = String.format("[%d]-[%s][%s][%s%%][%s]",
                    i + 1,
                    task.id(),
                    task.targetFileName(),
                    task.finishedPercent() == JGetTask.UNKNOWN_PERCENT ? "-" : String.valueOf(task.finishedPercent()),
                    task.createTime()
            );
            System.out.println(summary);
        }

    }

    public void resumeTask(int index) {
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
    }

    public void resumeTasks() {
        Iterator<Map.Entry<String, SnapshottingTask>> iterator = currentTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SnapshottingTask> entry = iterator.next();
            entry.getValue().ready();
        }
    }

    public void remove(int index) {
        List<SnapshottingTask> sortedTasks = currentTasks.values().stream()
                .sorted(Comparator.comparing(JGetTask::createTime).reversed()).collect(Collectors.toList());
        if (index <= 0 || index > sortedTasks.size()) {
            throw new IllegalArgumentException("index should > 0 and <= " + sortedTasks.size());
        }
        remove(sortedTasks.get(index - 1));
    }

    public void remove(JGetTask task) {
        final String taskId = task.id();
        try {
            currentTasks.remove(taskId);
            Snapshots.remove(taskId);
            removeActive(taskId);
            task.deleted();
            System.out.println("remove task " + taskId);
        } catch (IOException e) {
            LOG.error("Error remove task {} ", taskId, e);
        }
    }

    public void removeActive(String id) {
        activeTasks.remove(id);
        if (activeTasks.isEmpty()) {
            scheduler.shutdown();
        }
    }

    private void persistTasksPeriodically() {
        for (Map.Entry<String, SnapshottingTask> taskEntry : currentTasks.entrySet()) {
            try {
                LOG.debug("Persist task {} Periodically", taskEntry.getValue());
                Snapshots.persist(taskEntry.getValue().snapshot());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void showActiveTaskProgress() {
        StringBuilder progress = new StringBuilder();
        for (Map.Entry<String, JGetTask> taskEntry : activeTasks.entrySet()) {
            final JGetTask task = taskEntry.getValue();
            progress.append(String.format("[%s][%s][%s%%][%s/%s][%s]%n",
                    task.id(),
                    task.targetFileName(),
                    task.finishedPercent() == JGetTask.UNKNOWN_PERCENT ? "-" : String.valueOf(task.finishedPercent()),
                    unitedSize(task.getReadBytes()),
                    unitedSize(task.getTotalBytes()),
                    task.createTime()));
        }
        System.out.print(progress);
    }

    private static String unitedSize(long size) {
        if (size <= 0) {
            return "-";
        }
        Unit[] values = Unit.values();
        double united = 0D;
        Unit unit = Unit.B;
        for (int i = values.length - 1; i >= 0; i--) {
            united = size * 1.0 / values[i].getFactor();
            if (united >= 0.5) {
                unit = values[i];
                break;
            }
        }
        return String.format("%.2f%s", united, unit);
    }

    public boolean hasUnfinishedTask() {
        return !currentTasks.isEmpty();
    }

    public void clearTasks() {
        try {
            for (Map.Entry<String, SnapshottingTask> taskEntry : currentTasks.entrySet()) {
                Snapshots.remove(taskEntry.getKey());
            }
            currentTasks.clear();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}


enum Unit {
    B(1), KB(1 << 10), MB(1 << 20), GB(1 << 30);
    private final int factor;

    Unit(final int factor) {
        this.factor = factor;
    }

    public int getFactor() {
        return factor;
    }
}