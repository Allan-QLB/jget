package com.github.qlb;

import org.apache.commons.cli.*;

import java.util.Scanner;

public class Launcher {
    public static void main(String[] args) {
        final DefaultParser parser = new DefaultParser();
        HelpFormatter helpFormatter = new HelpFormatter();
        final Options options = DownloadOptions.getDefaultOptions();
        try {
            final CommandLine cli = parser.parse(options, args);
            if (cli.hasOption(DownloadOptions.HELP)) {
                helpFormatter.printHelp("jget", options);
            } else if (cli.hasOption(DownloadOptions.LIST_TASKS)) {
                TaskManager.INSTANCE.printTasks();
            } else if (cli.hasOption(DownloadOptions.RESUME_ALL)) {
                TaskManager.INSTANCE.resumeTasks();
            } else if (cli.hasOption(DownloadOptions.RESUME)) {
                if (TaskManager.INSTANCE.hasUnfinishedTask()) {
                    String resumeTargetIndexString = cli.getOptionValue(DownloadOptions.RESUME);
                    int index;
                    try {
                        index = Integer.parseInt(resumeTargetIndexString);
                    } catch (Exception e) {
                        index = selectUnfinishedTask();
                    }
                    TaskManager.INSTANCE.resumeTask(index);
                }
            } else if (cli.hasOption(DownloadOptions.DELETE)) {
                if (TaskManager.INSTANCE.hasUnfinishedTask()) {
                    String resumeTargetIndexString = cli.getOptionValue(DownloadOptions.RESUME);
                    int index;
                    try {
                        index = Integer.parseInt(resumeTargetIndexString);
                    } catch (Exception e) {
                        index = selectUnfinishedTask();
                    }
                    TaskManager.INSTANCE.remove(index);
                }
            } else if (cli.hasOption(DownloadOptions.DELETE_ALL)) {
                TaskManager.INSTANCE.clearTasks();
            } else if (cli.hasOption(DownloadOptions.URL)) {
                new DownloadTask(cli).start();
            } else {
                throw new ParseException("Missing argument url");
            }
        } catch (ParseException e) {
            e.printStackTrace();
            helpFormatter.printHelp("jget", options);
        }
    }

    private static int selectUnfinishedTask() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                TaskManager.INSTANCE.printTasks();
                String next = scanner.next();
                try {
                    return Integer.parseInt(next);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }
}
