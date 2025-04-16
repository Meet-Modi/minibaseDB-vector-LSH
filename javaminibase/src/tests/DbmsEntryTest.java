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

    public static final short MAX_STRING_LENGTH = 64;

    public static void main(String[] args) throws Exception {
        testCreateNewDbCloseAndReopen();
        testBatchCreate();
        testCreateIndex();

        // Re-run the tests to ensure after create index page pinned exception everything is working
        testCreateNewDbCloseAndReopen();
        testBatchCreate();
        System.out.println("All tests passed!");
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
        String dbNameOne = "testDbOne";
        String relNameOne = "rel1";
        String relNameTwo = "rel2";
        
        Files.deleteIfExists(Paths.get(getDbPath(dbNameOne)));
    
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });
        if (!Files.exists(Paths.get(getDbPath(dbNameOne))))
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB file missing!");

        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", relNameOne });

        String dbMetaDataFilePath = System.getProperty("user.name") + "." + dbNameOne + "-db.metadata";
        String relMetadataFilePath = System.getProperty("user.name") + "." + dbNameOne + "." + relNameOne + ".metadata";
        String dataFilePathFull = System.getProperty("user.name") + "." + dbNameOne + "." + relNameOne + ".data";

        Heapfile dbMetaDataFile = new Heapfile(dbMetaDataFilePath);
        Heapfile metadataFile = new Heapfile(relMetadataFilePath);
        Heapfile dataFile = new Heapfile(dataFilePathFull);

        if (dbMetaDataFile.getRecCnt() != 1)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB metadata file record count mismatch after batch create!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - Metadata file record count mismatch after batch create!");
        if (dataFile.getRecCnt() != 75000)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - Data file record count mismatch after batch create!");
        
        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample25_000.txt", relNameTwo });
        
        relMetadataFilePath = System.getProperty("user.name") + "." + dbNameOne + "." + relNameTwo + ".metadata";
        dataFilePathFull = System.getProperty("user.name") + "." + dbNameOne + "." + relNameTwo + ".data";
        
        metadataFile = new Heapfile(relMetadataFilePath);
        dataFile = new Heapfile(dataFilePathFull);

        if (dbMetaDataFile.getRecCnt() != 2)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB metadata file record count mismatch after batch create!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - Metadata file record count mismatch after batch create!");
        if (dataFile.getRecCnt() != 25000)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - Data file record count mismatch after batch create!");

        try{
            DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", relNameOne });
        } catch (Exception e) {
            System.out.println("Expected exception: " + e.getMessage());
        }
        
        DbmsEntry.handleDbCloseCommand();
    }

    private static void testCreateIndex() throws Exception { 
        String dbNameOne = "testDb";
        String relNameOne = "rel1";
        String indexName = "index1";

        Files.deleteIfExists(Paths.get(getDbPath(dbNameOne)));
        Files.deleteIfExists(Paths.get(getDbPath(dbNameOne)));
    
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });
        if (!Files.exists(Paths.get(getDbPath(dbNameOne))))
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB file missing!");

        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", relNameOne });

        String dbMetaDataFilePath = System.getProperty("user.name") + "." + dbNameOne + "-db.metadata";
        String relMetadataFilePath = System.getProperty("user.name") + "." + dbNameOne + "." + relNameOne + ".metadata";
        String dataFilePathFull = System.getProperty("user.name") + "." + dbNameOne + "." + relNameOne + ".data";

        Heapfile dbMetaDataFile = new Heapfile(dbMetaDataFilePath);
        Heapfile metadataFile = new Heapfile(relMetadataFilePath);
        Heapfile dataFile = new Heapfile(dataFilePathFull);

        if (dbMetaDataFile.getRecCnt() != 1)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB metadata file record count mismatch after batch create!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - Metadata file record count mismatch after batch create!");
        if (dataFile.getRecCnt() != 75000)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - Data file record count mismatch after batch create!");

        // Integer index on column 0
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), relNameOne, "1"});
        if (dbMetaDataFile.getRecCnt() != 2)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - DB metadata file record count mismatch after batch create!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - Metadata file record count mismatch after batch create!");
        if (dataFile.getRecCnt() != 75000)
            throw new RuntimeException("FAIL - testCreateNewDbCloseAndReopen - Data file record count mismatch after batch create!");

        DbmsEntry.handleDbCloseCommand();
    }
    
    private static String getDbPath(String dbName) {
        return "/tmp/" + System.getProperty("user.name") + "." + dbName + "-db";
    }

}
