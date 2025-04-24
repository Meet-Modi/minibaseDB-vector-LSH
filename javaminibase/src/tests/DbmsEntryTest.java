package tests;

import LSHFIndex.LSHFIndex;
import btree.*;
import global.AttrType;
import global.Vector100Dtype;
import heap.Heapfile;
import heap.Tuple;
import iterator.*;
import scripts.phaseThree.DbmsEntry;
import scripts.phaseThree.SupportedCommands;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class DbmsEntryTest {

    public static final short MAX_STRING_LENGTH = 64;
    public static final HashSet<String> STRINGS_IN_75000_DATASET = new HashSet<>();
    public static final HashSet<String> STRINGS_IN_25000_DATASET = new HashSet<>();


    public static void main(String[] args) throws Exception {
        // Initialization
        BiConsumer<String, HashSet<String>> readStringsToHashSet = (txtFilePath, hashSet) -> {
            try {
                BufferedReader fileReader = new BufferedReader(new FileReader(txtFilePath));
                fileReader.readLine();
                fileReader.readLine();

                String line = fileReader.readLine();
                while(line != null) {
                    fileReader.readLine();
                    hashSet.add(fileReader.readLine());
                    fileReader.readLine();
                    line = fileReader.readLine();
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        };
        readStringsToHashSet.accept("javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", STRINGS_IN_75000_DATASET);
        readStringsToHashSet.accept("javaminibase/src/tests/scriptTestDataFiles/sample25_000.txt", STRINGS_IN_25000_DATASET);

        // Tests
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

        System.out.println("PASS - testCreateNewDbCloseAndReopen\n\n");
    }

    private static void testBatchCreate() throws Exception {
        String dbNameOne = "testDbOne";
        String relNameOne = "rel1";
        String relNameTwo = "rel2";
        
        Files.deleteIfExists(Paths.get(getDbPath(dbNameOne)));
    
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });
        if (!Files.exists(Paths.get(getDbPath(dbNameOne))))
            throw new RuntimeException("FAIL - testBatchCreate - DB file missing!");

        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", relNameOne });
        String dbMetaDataFilePath = DbmsEntry.getDbMetadataFilePath(dbNameOne);
        String relMetadataFilePath = DbmsEntry.getRelMetaDataFilePath(dbNameOne, relNameOne);
        String dataFilePathFull = DbmsEntry.getRelDataFilePath(dbNameOne, relNameOne);

        Heapfile dbMetaDataFile = new Heapfile(dbMetaDataFilePath);
        Heapfile metadataFile = new Heapfile(relMetadataFilePath);
        Heapfile dataFile = new Heapfile(dataFilePathFull);

        if (dbMetaDataFile.getRecCnt() != 1)
            throw new RuntimeException("FAIL - testBatchCreate - DB metadata file record count mismatch after batch create!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testBatchCreate - Metadata file record count mismatch after batch create!");
        if (dataFile.getRecCnt() != 75000)
            throw new RuntimeException("FAIL - testBatchCreate - Data file record count mismatch after batch create!");

        BiConsumer<String, HashSet<String>> verifyDataFileContents = (heapDataFileName, targetStringSet) -> {
            HashSet<String> stringsInHeapDataFile = new HashSet<>();
            try {
                FileScan fileScan = new FileScan(heapDataFileName,
                        new AttrType[] { new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrReal), new AttrType(AttrType.attrString), new AttrType(AttrType.attrVector100D) },
                        new short[] {MAX_STRING_LENGTH},
                        (short) 4,
                        1,
                        new FldSpec[] { new FldSpec(new RelSpec(RelSpec.outer), 3) },
                        null);

                Tuple outTuple = fileScan.get_next();
                while (outTuple != null) {
                    stringsInHeapDataFile.add(outTuple.getStrFld(1));
                    outTuple = fileScan.get_next();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if(stringsInHeapDataFile.isEmpty() || (! stringsInHeapDataFile.equals(targetStringSet)))
                throw new RuntimeException("FAIL - testBatchCreate - Mismatch between data in txt file and heap file!");
        };
        verifyDataFileContents.accept(dataFilePathFull, STRINGS_IN_75000_DATASET);

        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample25_000.txt", relNameTwo });
        

        relMetadataFilePath = DbmsEntry.getRelMetaDataFilePath(dbNameOne, relNameTwo);
        dataFilePathFull = DbmsEntry.getRelDataFilePath(dbNameOne, relNameTwo);
        
        metadataFile = new Heapfile(relMetadataFilePath);
        dataFile = new Heapfile(dataFilePathFull);

        if (dbMetaDataFile.getRecCnt() != 2)
            throw new RuntimeException("FAIL - testBatchCreate - DB metadata file record count mismatch after batch create!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testBatchCreate - Metadata file record count mismatch after batch create!");
        if (dataFile.getRecCnt() != 25000)
            throw new RuntimeException("FAIL - testBatchCreate - Data file record count mismatch after batch create!");
        verifyDataFileContents.accept(dataFilePathFull, STRINGS_IN_25000_DATASET);

        try{
            DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", relNameOne });
        } catch (Exception e) {
            System.out.println("Expected exception: " + e.getMessage());
        }
        
        DbmsEntry.handleDbCloseCommand();

        // Reopen DB and make sure files preserved
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });
        verifyDataFileContents.accept(dataFilePathFull, STRINGS_IN_25000_DATASET);

        DbmsEntry.handleDbCloseCommand();
        System.out.println("PASS - testBatchCreate\n\n");
    }

    private static void testCreateIndex() throws Exception { 
        String dbNameOne = "testDb";
        String relNameOne = "rel1";

        Files.deleteIfExists(Paths.get(getDbPath(dbNameOne)));

        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });
        if (!Files.exists(Paths.get(getDbPath(dbNameOne))))
            throw new RuntimeException("FAIL - testCreateIndex - DB file missing!");

        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", relNameOne });

        String dbMetaDataFilePath = DbmsEntry.getDbMetadataFilePath(dbNameOne);
        String relMetadataFilePath = DbmsEntry.getRelMetaDataFilePath(dbNameOne, relNameOne);
        String dataFilePathFull = DbmsEntry.getRelDataFilePath(dbNameOne, relNameOne);

        Heapfile dbMetaDataFile = new Heapfile(dbMetaDataFilePath);
        Heapfile metadataFile = new Heapfile(relMetadataFilePath);
        Heapfile dataFile = new Heapfile(dataFilePathFull);

        if (dbMetaDataFile.getRecCnt() != 1)
            throw new RuntimeException("FAIL - testCreateIndex - DB metadata file record count mismatch after batch create!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testCreateIndex - Metadata file record count mismatch after batch create!");
        if (dataFile.getRecCnt() != 75000)
            throw new RuntimeException("FAIL - testCreateIndex - Data file record count mismatch after batch create!");

        // Integer index on column 0
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), relNameOne, "3"});
        if (dbMetaDataFile.getRecCnt() != 2)
            throw new RuntimeException("FAIL - testCreateIndex - DB metadata file record count mismatch after index create!");
        if (! DbmsEntry.checkIfIndexExistsInDbMetaDataFile(relNameOne, 3))
            throw new RuntimeException("FAIL - testCreateIndex - DB metadata file does not contain newly created index!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testCreateIndex - Metadata file record count mismatch after index create!");
        if (dataFile.getRecCnt() != 75000)
            throw new RuntimeException("FAIL - testCreateIndex - Data file record count mismatch after index create!");

        Consumer<String> readFullBTreeIndex = (btreeFileName) -> {
            HashSet<String> stringsFromBTree = new HashSet<>();
            try {
                BTreeFile bTreeFile = new BTreeFile(btreeFileName);
                BTFileScan scan = bTreeFile.new_scan(null, null);
                KeyDataEntry entry = scan.get_next();
                while(entry != null) {
                    stringsFromBTree.add(((StringKey)entry.key).getKey());
                    entry = scan.get_next();
                }
                scan.DestroyBTreeFileScan();
                bTreeFile.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if(stringsFromBTree.isEmpty() || (! stringsFromBTree.equals(STRINGS_IN_75000_DATASET)))
                throw new RuntimeException("FAIL - testCreateIndex - Mismatch between data in txt file and bTree!");

        };
        readFullBTreeIndex.accept(DbmsEntry.getRelNameColSpace(relNameOne, 3));

        Consumer<String> searchBTreeAndVerifyRecord = (btreeFileName) -> {
            try {
                BTreeFile bTreeFile = new BTreeFile(btreeFileName);
                StringKey key = new StringKey("rpYDFdMw");
                BTFileScan scan = bTreeFile.new_scan(key, key);

                KeyDataEntry entry = scan.get_next();

                Tuple resultTuple = dataFile.getRecord(((LeafData)entry.data).getData());
                resultTuple.setHdr(
                        (short) 4,
                        new AttrType[] { new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrReal), new AttrType(AttrType.attrString), new AttrType(AttrType.attrVector100D) },
                        new short[] {MAX_STRING_LENGTH}
                );

                if((resultTuple.getIntFld(1) != 84) || (resultTuple.getFloFld(2) != 76.2f) || (! resultTuple.getStrFld(3).equals("rpYDFdMw")))
                    throw new RuntimeException("FAIL - testCreateIndex - Mismatch between data in txt file and bTree!");

                String expectedVectorString = "-5031 -271 9785 8427 -7795 -9298 1360 -3782 -2346 9816 4719 -8765 4041 -6622 -3626 9888 -9503 -4853 -3686 6774 9106 -8172 -9958 -3108 -2164 9752 5426 6778 9610 8539 3941 -8397 -3686 -4388 -6680 -8042 -9707 -8228 4126 -1472 9487 -931 1916 -7374 4201 -8861 2660 -8566 7364 -6006 -6271 7783 -1550 3683 -3944 -4154 2956 9236 8115 -3703 -6100 8766 -4591 4280 -5161 -9186 -1791 -8328 -7890 4439 -6359 -3091 -6304 -9814 -3398 -25 -3581 6082 -2503 1076 -3364 3267 -5319 -3064 -9006 8816 4470 -1552 1111 5983 9011 -617 -1498 -534 6834 9070 5232 2159 1966 5359";
                short[] targetVector = new short[100];
                int i = 0;
                for(String s : expectedVectorString.split(" ")) {
                    targetVector[i] = Short.parseShort(s);
                    i++;
                }
                if(! resultTuple.get100DVectFld(4).equals(new Vector100Dtype(targetVector)))
                    throw new RuntimeException("FAIL - testCreateIndex - Mismatch between data in txt file and bTree!");

                scan.DestroyBTreeFileScan();
                bTreeFile.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        searchBTreeAndVerifyRecord.accept(DbmsEntry.getRelNameColSpace(relNameOne, 3));

        // LSH Index on Vector field
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), relNameOne, "4", "2", "2"});
        if (dbMetaDataFile.getRecCnt() != 3)
            throw new RuntimeException("FAIL - testCreateIndex - DB metadata file record count mismatch after index create!");
        if (! DbmsEntry.checkIfIndexExistsInDbMetaDataFile(relNameOne, 4))
            throw new RuntimeException("FAIL - testCreateIndex - DB metadata file does not contain newly created index!");
        if (metadataFile.getRecCnt() != 4)
            throw new RuntimeException("FAIL - testCreateIndex - Metadata file record count mismatch after index create!");
        if (dataFile.getRecCnt() != 75000)
            throw new RuntimeException("FAIL - testCreateIndex - Data file record count mismatch after index create!");

        Consumer<LSHFIndex> verifyLshIndexHasAllTuples = (lshfIndex) -> {
            Function<String, Integer> getBinSize = (binName) -> {
              try {
                  return new Heapfile(binName).getRecCnt();
              } catch (Exception e) {
                  throw new RuntimeException(e);
              }
            };

            HashSet<String> layer1UniqueBins = new HashSet<>();
            HashSet<String> layer2UniqueBins = new HashSet<>();
            try {
                FileScan fileScan = new FileScan(dataFilePathFull,
                        new AttrType[] { new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrReal), new AttrType(AttrType.attrString), new AttrType(AttrType.attrVector100D) },
                        new short[] {MAX_STRING_LENGTH},
                        (short) 4,
                        1,
                        new FldSpec[] { new FldSpec(new RelSpec(RelSpec.outer), 4) },
                        null);

                Tuple outTuple = fileScan.get_next();
                int i = 0;

                while (outTuple != null) {
                    List<String> binNames = lshfIndex.getBinHeapFileNames(outTuple.get100DVectFld(1));
                    layer1UniqueBins.add(binNames.get(0));
                    layer2UniqueBins.add(binNames.get(1));

                    outTuple = fileScan.get_next();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            int layer1RecordCount = layer1UniqueBins.stream().mapToInt(getBinSize::apply).sum();
            int layer2RecordCount = layer2UniqueBins.stream().mapToInt(getBinSize::apply).sum();;
            if((layer1RecordCount != 75_000) || (layer2RecordCount != 75_000))
                throw new RuntimeException("FAIL - testCreateIndex - Record count mismatch between LSHFIndex and data heap file!");
        };

        LSHFIndex lshfIndexBeforeDbClose = new LSHFIndex(DbmsEntry.getRelNameColSpace(relNameOne, 4), 4);
        verifyLshIndexHasAllTuples.accept(lshfIndexBeforeDbClose);

        DbmsEntry.handleDbCloseCommand();

        // Reopen DB to check if files preserved
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });
        readFullBTreeIndex.accept(DbmsEntry.getRelNameColSpace(relNameOne, 3));
        searchBTreeAndVerifyRecord.accept(DbmsEntry.getRelNameColSpace(relNameOne, 3));

        LSHFIndex lshfIndexAfterDbClose = new LSHFIndex(DbmsEntry.getRelNameColSpace(relNameOne, 4), 4);
        verifyLshIndexHasAllTuples.accept(lshfIndexAfterDbClose);

        if(! lshfIndexAfterDbClose.equals(lshfIndexBeforeDbClose))
            throw new RuntimeException("FAIL - testCreateIndex - LSHIndices before and after close are not the same! Perhaps LSHF reinitialization from state/meta files isn't working.");

        DbmsEntry.handleDbCloseCommand();
        System.out.println("PASS - testCreateIndex\n\n");
    }
    
    private static String getDbPath(String dbName) {
        return "/tmp/" + System.getProperty("user.name") + "." + dbName + "-db";
    }

}
