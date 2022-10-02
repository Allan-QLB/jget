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
            .option("d")
            .argName("directory")
            .desc("home directory")
            .hasArg().build();

    public static final Option HELP = Option.builder()
            .option("h")
            .desc("print help")
            .hasArg(false)
            .build();


    public static Options getDefaultOptions() {
        final Options options = new Options();
        options.addOption(URL);
        options.addOption(HOME_DIR);
        options.addOption(HELP);
        return options;
    }
}
