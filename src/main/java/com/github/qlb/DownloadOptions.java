package com.github.qlb;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class DownloadOptions {

    public static final Option URL = Option.builder()
            .option("u")
            .argName("url")
            .desc("download url")
            .hasArg()
            .build();

    public static final Option HOME_DIR = Option.builder()
            .option("H")
            .argName("directory")
            .desc("home directory")
            .hasArg().build();

    public static final Option LIST_TASKS = Option.builder()
            .option("l")
            .desc("list unfinished tasks")
            .hasArg(false)
            .build();

    public static final Option RESUME = Option.builder()
            .option("r")
            .desc("resume unfinished task of given index")
            .hasArg()
            .optionalArg(true)
            .argName("index of listed tasks")
            .build();
    public static final Option RESUME_ALL = Option.builder()
            .option("R")
            .desc("resume all unfinished tasks")
            .hasArg(false)
            .build();

    public static final Option HELP = Option.builder()
            .option("h")
            .desc("print help")
            .hasArg(false)
            .build();

    public static final Option DELETE = Option.builder()
            .option("d")
            .desc("delete task of given index")
            .hasArg()
            .optionalArg(true)
            .argName("index of listed tasks")
            .build();
    public static final Option DELETE_ALL = Option.builder()
            .option("D")
            .desc("delete all tasks")
            .hasArg(false)
            .build();


    public static Options getDefaultOptions() {
        return new Options()
                .addOption(URL)
                .addOption(HOME_DIR)
                .addOption(HELP)
                .addOption(LIST_TASKS)
                .addOption(RESUME)
                .addOption(RESUME_ALL)
                .addOption(DELETE)
                .addOption(DELETE_ALL)
                ;
    }
}
