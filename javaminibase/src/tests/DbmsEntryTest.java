package tests;

import global.AttrType;
import heap.Heapfile;
import heap.Tuple;
import iterator.FileScan;
import iterator.FldSpec;
import iterator.RelSpec;
import scripts.phaseThree.DbmsEntry;
import scripts.phaseThree.SupportedCommands;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class DbmsEntryTest {

    public static void main(String[] args) throws Exception {
        testCreateNewDbCloseAndReopen();
        System.out.println("****************");
        testBatchCreate();
    }

    private static void testCreateNewDbCloseAndReopen() throws Exception {
        String dbNameOne = "testDb";
        String dbNameTwo = "testDbTwo";

        Files.deleteIfExists(Paths.get(getDbPath(dbNameOne)));
        Files.deleteIfExists(Paths.get(getDbPath(dbNameTwo)));

        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });

        if (!Files.exists(Paths.get(getDbPath(dbNameOne))))
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB file missing!");

        Consumer<Integer[]> insertDataToTestHeapFile = (numsToInsert) -> {
            try {
                Heapfile file = new Heapfile("testFile");

                Tuple t = new Tuple();
                t.setHdr((short) 1, new AttrType[] { new AttrType(AttrType.attrInteger) }, null);

                for (int i : numsToInsert) {
                    t.setIntFld(1, i);
                    file.insertRecord(t.getTupleByteArray());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // Insert dummy data
        insertDataToTestHeapFile.accept(new Integer[] { 10, 20 });
        DbmsEntry.handleDbCloseCommand();

        // Open another Db
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameTwo });
        insertDataToTestHeapFile.accept(new Integer[] { 100, 200 });
        DbmsEntry.handleDbCloseCommand();

        // Reopen DbOne
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });

        // Read dummy data from dbOne
        Consumer<List<Integer>> readDataFromTestHeapFile = (expectedNums) -> {
            try {
                Heapfile file = new Heapfile("testFile");
                if (file.getRecCnt() != 2)
                    throw new RuntimeException(
                            "FAIL - testCreateNewDbCloseAndReopen - Heap file record count mismatch after reopening!");

                FileScan fileScan = new FileScan("testFile",
                        new AttrType[] { new AttrType(AttrType.attrInteger) },
                        null,
                        (short) 1,
                        1,
                        new FldSpec[] { new FldSpec(new RelSpec(RelSpec.outer), 1) },
                        null);

                Tuple outTuple = fileScan.get_next();
                ArrayList<Integer> results = new ArrayList<>();
                while (outTuple != null) {
                    results.add(outTuple.getIntFld(1));
                    outTuple = fileScan.get_next();
                }

                if (!results.equals(expectedNums))
                    throw new RuntimeException(
                            "FAIL - testCreateNewDbCloseAndReopen - File data mismatch after reopening!");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        readDataFromTestHeapFile.accept(Arrays.asList(10, 20));
        DbmsEntry.handleDbCloseCommand();

        // Read dummy data from dbTwo
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameTwo });

        readDataFromTestHeapFile.accept(Arrays.asList(100, 200));
        DbmsEntry.handleDbCloseCommand();
    }

    private static void testBatchCreate() throws Exception {
        System.out.println("This is a test for batch create, this first opens two databases, runs two batch inserts. Then again runs the same batch insert but this fails and exists");


        String dbNameOne = "testDb";
        String dbNameTwo = "testDbTwo";

        // Files.deleteIfExists(Paths.get(getDbPath(dbNameOne)));
        // Files.deleteIfExists(Paths.get(getDbPath(dbNameTwo)));

        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });
        if (!Files.exists(Paths.get(getDbPath(dbNameOne))))
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB file missing!");

        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample25_000.txt", "rel1" });
        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", "rel2" });

        DbmsEntry.handleDbCloseCommand();
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameTwo });
        if (!Files.exists(Paths.get(getDbPath(dbNameTwo))))
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB file missing!");
        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample25_000.txt", "rel1" });
        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", "rel2" });
        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample25_000.txt", "rel1" });
        DbmsEntry.handleDbCloseCommand();        
    }

    private static String getDbPath(String dbName) {
        return "/tmp/" + System.getProperty("user.name") + "." + dbName + "-db";
    }

}
