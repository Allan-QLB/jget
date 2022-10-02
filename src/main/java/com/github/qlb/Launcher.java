package com.github.qlb;

import org.apache.commons.cli.*;

public class Launcher {
    public static void main(String[] args) {
        final DefaultParser parser = new DefaultParser();
        HelpFormatter helpFormatter = new HelpFormatter();
        final Options options = DownloadOptions.getDefaultOptions();
        try {
            final CommandLine cli = parser.parse(options, args);
            if (cli.hasOption(DownloadOptions.HELP)) {
                helpFormatter.printHelp("jget", options);
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
}
