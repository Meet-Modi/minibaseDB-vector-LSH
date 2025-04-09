package scripts.phaseThree;

public enum SupportedCommands {
    OPEN_DB ("open_database", "open_database <db name>"),
    CLOSE_DB ("close_database", "close_database"),
    BATCH_CREATE("batchcreate", "batchcreate <data file name> <rel name>");

    private final String command;
    private final String usage;
    SupportedCommands(String cmd, String usage) {
        command = cmd;
        this.usage = usage;
    }

    public String getCommand() {
        return command;
    }

    public String getUsage() {
        return usage;
    }
}
