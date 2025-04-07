package scripts.phaseThree;

import global.SystemDefs;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

import static global.GlobalConst.NUMBUF;
import static global.SystemDefs.JavabaseBM;

public class DbmsEntry {

    public static final int DB_SIZE_IN_PAGES = NUMBUF * 10;

    private static final Scanner scanner = new Scanner(System.in);

    private static String currentOpenDb = null;

    public static void main(String[] args) throws Exception {

        while(true) {
            System.out.println("\nEnter your command");

            String input = scanner.nextLine();
            if(input.isBlank())
                break;

            String[] commandParts = input.trim().split(" ");
            if(commandParts[0].equals(SupportedCommands.OPEN_DB.getCommand())) {
                handleDbOpenCommand(commandParts);
            } else if(commandParts[0].equals(SupportedCommands.CLOSE_DB.getCommand())) {
                handleDbCloseCommand();
            }
            else {
                System.out.println("Unrecognized command. Quitting...");
                break;
            }
        }
        handleDbCloseCommand();
        scanner.close();
        System.out.println("Quitting...");
    }

    public static void handleDbOpenCommand(String[] commandParts) {
        if(currentOpenDb != null) {
            System.out.println(currentOpenDb + " is still open. Close it before opening another db.");
            return;
        }
        if((commandParts.length != 2) || (! commandParts[0].equals(SupportedCommands.OPEN_DB.getCommand()))) {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.OPEN_DB.getUsage());
            return;
        }

        String dbName = commandParts[1];
        String dbPath = "/tmp/"  + System.getProperty("user.name") + "."+ dbName + "-db";

        if(Files.exists(Paths.get(dbPath))) {
            System.out.println(dbName + " already exists. Restarting...");
            SystemDefs.MINIBASE_RESTART_FLAG = true;
        }
        new SystemDefs(dbPath, DB_SIZE_IN_PAGES, DB_SIZE_IN_PAGES, "Clock");

        System.out.println("Opened db " + dbName + " at " + dbPath);
        currentOpenDb = dbName;
    }

    public static void handleDbCloseCommand() throws Exception {
        if(currentOpenDb == null) {
            System.out.println("No DB open currently. Nothing closed.");
            return;
        }
        JavabaseBM.flushAllPages();
        System.out.println(currentOpenDb + " pages flushed and DB closed.");
        currentOpenDb = null;
    }

}
