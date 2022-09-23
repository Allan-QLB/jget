package com.github.qlb;

import org.apache.commons.cli.*;

public class Launcher {
    public static void main(String[] args) throws ParseException {
        final Options options = new Options();
        final Option option = Option.builder()
                .option("u")
                .argName("url")
                .hasArg()
                .desc("url")
                .required()
                .build();
        options.addOption(option);
        final DefaultParser parser = new DefaultParser();
        final CommandLine cli = parser.parse(options, args);
        final String url = cli.getOptionValue("u");
        final Client client = new Client(new DownloadTask(url));
        client.start();
    }
}
