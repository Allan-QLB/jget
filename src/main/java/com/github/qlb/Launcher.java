package com.github.qlb;

import org.apache.commons.cli.*;

public class Launcher {
    public static void main(String[] args) throws ParseException {
        final DefaultParser parser = new DefaultParser();
        final CommandLine cli = parser.parse(DownloadOptions.getDefaultOptions(), args);
        final Client client = new Client(new DownloadTask(cli));
        client.start();
    }
}
