package tests;

import LSHFIndex.LSHFIndex;
import btree.*;
import global.AttrType;
import global.SystemDefs;
import global.Vector100Dtype;
import heap.Heapfile;
import heap.Tuple;
import iterator.*;
import scripts.phaseThree.DbmsEntry;
import scripts.phaseThree.SupportedCommands;

import java.io.*;
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
    private static final HashSet<String> STRINGS_IN_75000_DATASET = new HashSet<>();
    private static final HashSet<String> STRINGS_IN_25000_DATASET = new HashSet<>();
    private static final String QUERY_SPECIFICATION_FILE_PATH = getDbPath("testQuerySpecFile.txt");
    private static final String TARGET_VECTOR_FILE_PATH = getDbPath("testTargetVecFile.txt");
    private static final String FILE_OUTPUT_STREAM_PATH = getDbPath("fileOut.txt");

    // Picked a random vector from the 75_000 dataset
    private static final String TARGET_VECTOR = "1273 -5324 -181 5219 -4663 5455 -3006 2703 -4230 1255 -4585 5449 -4241 2284 5459 7296 -9925 5501 -5258 7480 -2415 9328 -3372 -6364 1512 3869 -8219 18 6120 5027 6992 1627 8665 8558 -8882 -1036 -4477 -4989 -3045 5648 -2858 -7062 1745 -4893 -8278 -4439 -5914 -5357 596 -8324 -6161 -966 -6916 4310 -1996 7369 3667 8076 1644 -8207 -2748 -8159 9345 3694 9 3254 -897 -349 8543 1360 -3573 -2869 -8884 5151 5393 1268 3745 7578 -3695 7579 8268 -6812 9502 -5970 -5623 -6051 -3951 9651 -3459 -440 -3297 -4441 -7249 -2963 5135 -4857 2881 6137 -8614 -9442";

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
        testBTreeIndicesOnMultipleColumnsAndMultipleRelations();
        testQueries();

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
        String relMetadataFilePath = DbmsEntry.getRelMetaDataFileName(relNameOne);
        String dataFilePathFull = DbmsEntry.getRelDataFileName(relNameOne);

        Heapfile dbMetaDataFile = new Heapfile(DbmsEntry.DB_METADATA_FILE_NAME);
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
                        new short[] {DbmsEntry.MAX_STRING_LENGTH},
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

        relMetadataFilePath = DbmsEntry.getRelMetaDataFileName(relNameTwo);
        dataFilePathFull = DbmsEntry.getRelDataFileName(relNameTwo);
        
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

        String relMetadataFilePath = DbmsEntry.getRelMetaDataFileName(relNameOne);
        String dataFilePathFull = DbmsEntry.getRelDataFileName(relNameOne);

        Heapfile dbMetaDataFile = new Heapfile(DbmsEntry.DB_METADATA_FILE_NAME);
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
        readFullBTreeIndex.accept(DbmsEntry.getBTreeFileName(relNameOne, 3));

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
                        new short[] {DbmsEntry.MAX_STRING_LENGTH}
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
        searchBTreeAndVerifyRecord.accept(DbmsEntry.getBTreeFileName(relNameOne, 3));

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
                        new short[] {DbmsEntry.MAX_STRING_LENGTH},
                        (short) 4,
                        1,
                        new FldSpec[] { new FldSpec(new RelSpec(RelSpec.outer), 4) },
                        null);

                Tuple outTuple = fileScan.get_next();

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

        LSHFIndex lshfIndexBeforeDbClose = new LSHFIndex(relNameOne, 4);
        verifyLshIndexHasAllTuples.accept(lshfIndexBeforeDbClose);

        DbmsEntry.handleDbCloseCommand();

        // Reopen DB to check if files preserved
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });

        readFullBTreeIndex.accept(DbmsEntry.getBTreeFileName(relNameOne, 3));
        searchBTreeAndVerifyRecord.accept(DbmsEntry.getBTreeFileName(relNameOne, 3));

        LSHFIndex lshfIndexAfterDbClose = new LSHFIndex(relNameOne, 4);
        verifyLshIndexHasAllTuples.accept(lshfIndexAfterDbClose);

        if(! lshfIndexAfterDbClose.equals(lshfIndexBeforeDbClose))
            throw new RuntimeException("FAIL - testCreateIndex - LSHIndices before and after close are not the same! Perhaps LSHF reinitialization from state/meta files isn't working.");

        DbmsEntry.handleDbCloseCommand();
        System.out.println("PASS - testCreateIndex\n\n");
    }

//    TODO Vikram - Test multi column indices on lsh and btree after Meet's naming code fix

    private static void testBTreeIndicesOnMultipleColumnsAndMultipleRelations() throws Exception {
        String dbNameOne = "testDb";
        String twentyFiveKRelation = "rel25";
        String sampleData1Relation = "relSample1";

        Files.deleteIfExists(Paths.get(getDbPath(dbNameOne)));

        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });

        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample25_000.txt", twentyFiveKRelation });
        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample_data_1.txt", sampleData1Relation });

        final HashSet<Integer> twentyFiveKCol1 = new HashSet<>();
        final HashSet<Float> twentyFiveKCol2 = new HashSet<>();
        final HashSet<String> twentyFiveKCol3 = new HashSet<>();
        BufferedReader fileReader = new BufferedReader(new FileReader("javaminibase/src/tests/scriptTestDataFiles/sample25_000.txt"));
        fileReader.readLine();
        fileReader.readLine();
        String line = fileReader.readLine();
        while(line != null) {
            twentyFiveKCol1.add(Integer.parseInt(line));
            twentyFiveKCol2.add(Float.parseFloat(fileReader.readLine()));
            twentyFiveKCol3.add(fileReader.readLine());
            fileReader.readLine();
            line = fileReader.readLine();
        }

        final HashSet<Float> sampleDataCol1 = new HashSet<>();
        final HashSet<Float> sampleDataCol3 = new HashSet<>();
        fileReader = new BufferedReader(new FileReader("javaminibase/src/tests/scriptTestDataFiles/sample_data_1.txt"));
        fileReader.readLine();
        fileReader.readLine();
        line = fileReader.readLine();
        while(line != null) {
            sampleDataCol1.add(Float.parseFloat(line));
            fileReader.readLine();
            sampleDataCol3.add(Float.parseFloat(fileReader.readLine()));
            fileReader.readLine();
            line = fileReader.readLine();
        }

        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), twentyFiveKRelation, "1"});
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), twentyFiveKRelation, "2"});
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), twentyFiveKRelation, "3"});
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), sampleData1Relation, "1"});
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), sampleData1Relation, "3"});
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(twentyFiveKRelation, 1), twentyFiveKCol1, AttrType.attrInteger);
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(twentyFiveKRelation, 2), twentyFiveKCol2, AttrType.attrReal);
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(twentyFiveKRelation, 3), twentyFiveKCol3, AttrType.attrString);
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(sampleData1Relation, 1), sampleDataCol1, AttrType.attrReal);
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(sampleData1Relation, 3), sampleDataCol3, AttrType.attrReal);

        DbmsEntry.handleDbCloseCommand();
        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbNameOne });

        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(twentyFiveKRelation, 1), twentyFiveKCol1, AttrType.attrInteger);
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(twentyFiveKRelation, 2), twentyFiveKCol2, AttrType.attrReal);
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(twentyFiveKRelation, 3), twentyFiveKCol3, AttrType.attrString);
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(sampleData1Relation, 1), sampleDataCol1, AttrType.attrReal);
        readFullBTreeAndCompare(DbmsEntry.getBTreeFileName(sampleData1Relation, 3), sampleDataCol3, AttrType.attrReal);

        DbmsEntry.handleDbCloseCommand();
        System.out.println("PASS - testBTreeIndicesOnMultipleColumnsAndMultipleRelations\n\n");
    }

    private static void testQueries() throws Exception {
        String dbName = "testDb";
        String relName = "rel1";

        Files.deleteIfExists(Paths.get(getDbPath(dbName)));
        Files.deleteIfExists(Paths.get(TARGET_VECTOR_FILE_PATH));
        cleanupTestInputFiles();

        DbmsEntry.handleDbOpenCommand(new String[] { SupportedCommands.OPEN_DB.getCommand(), dbName });
        DbmsEntry.handleBatchCreateCommand(new String[] { SupportedCommands.BATCH_CREATE.getCommand(), "javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt", relName });
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), relName, "4", "2", "2"});

        // Picked a random target vector from 75_000 sample data file
        createFileForTestInput(TARGET_VECTOR_FILE_PATH, TARGET_VECTOR);

        Runnable forceRestartDb = () -> {
            new SystemDefs(getDbPath(dbName), DbmsEntry.DB_SIZE_IN_PAGES, DbmsEntry.DB_SIZE_IN_PAGES, "Clock");
        };

        Function<Integer, Integer> verifyRangeOutput = (maxDistance) -> {
            try {
                Vector100Dtype target = Vector100Dtype.buildVector100Dtype(TARGET_VECTOR.split(" "));

                BufferedReader fileReader = new BufferedReader(new FileReader(FILE_OUTPUT_STREAM_PATH));
                String line = fileReader.readLine();
                while((line != null) && (! line.contains("---Output Tuples---")))
                    line = fileReader.readLine();

                line = fileReader.readLine();
                int prevDistance = -1;
                boolean hasSeenZero = false;
                int vectorsSeen = 0;
                while(! line.contains("---End Output---")) {
                    if(! line.trim().startsWith("[")) {
                        line = fileReader.readLine();
                        continue;
                    }
                    Vector100Dtype currVector = Vector100Dtype.buildVector100Dtype(line.substring(1, line.length() - 1).split(", "));
                    int distance = target.get_distance(currVector);
                    if(distance < prevDistance)
                        throw new RuntimeException("FAIL - testQueries - Distance not increasing when going through query's vector results!");
                    if(distance > maxDistance)
                        throw new RuntimeException("FAIL - testQueries - Distance greater than max supplied distance!");
                    if(distance == 0)
                        hasSeenZero = true;

                    vectorsSeen++;
                    prevDistance = distance;
                    line = fileReader.readLine();
                }

                if(! hasSeenZero)
                    throw new RuntimeException("FAIL - testQueries - The result should contain the target vector itself!");

                return vectorsSeen;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Function<Integer, Integer> verifyFilterOutput = (expectedKey) -> {
            try {
                BufferedReader fileReader = new BufferedReader(new FileReader(FILE_OUTPUT_STREAM_PATH));
                String line = fileReader.readLine();
                while((line != null) && (! line.contains("---Output Tuples---")))
                    line = fileReader.readLine();

                line = fileReader.readLine();
                int recordsSeen = 0;
                while(! line.contains("---End Output---")) {
                    if(line.trim().equals(String.valueOf(expectedKey))) {
                        recordsSeen++;
                    }
                    line = fileReader.readLine();
                }
                return recordsSeen;
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        };

        testRangeQuery(relName, verifyRangeOutput, forceRestartDb);
        testNnQuery(relName, verifyRangeOutput, forceRestartDb);
        testSortQuery(relName, verifyRangeOutput);
        testFilterQuery(relName, verifyFilterOutput, forceRestartDb);

        System.out.println("PASS - testQueries\n\n");
    }

    public static void testFilterQuery(String relationName, Function<Integer, Integer> verifyFilterOutput, Runnable forceRestartDb) throws Exception {
        cleanupTestInputFiles();

        // Query on 100D column
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"Filter(4, 5, 10, N, 2, 4)");
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});

        cleanupTestInputFiles();

        // No index created but used
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"Filter(1, 19, 10, Y, 1, 4)");
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});

        cleanupTestInputFiles();

        // With Integer index
        DbmsEntry.handleIndexCreateCommand(new String[] { SupportedCommands.CREATE_INDEX.getCommand(), relationName, "1"});

        OutputStream teeStream = buildTeeStream();
        System.setOut(new PrintStream(teeStream, true));
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"Filter(1, 19, 10, Y, 1, 4)");
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});
        teeStream.close();
        if(verifyFilterOutput.apply(19) == 0)
            throw new RuntimeException("FAIL - testQueries - Filter query should return atleast 1 record!");

        // Low numbuf
        try {
            DbmsEntry.handleQueryCommand(new String[]{SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(1)});
        } catch (Exception e) {
            System.out.println("Raised exception as expected.");
            teeStream.close();
        }
        //      Simulate a program re-launch by force restarting DB
        forceRestartDb.run();

        // Positive case to ensure DB not corrupt - Remove once below methods are done
        teeStream = buildTeeStream();
        System.setOut(new PrintStream(teeStream, true));
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"Filter(1, 19, 10, Y, 1, 4)");
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});
        teeStream.close();
        if(verifyFilterOutput.apply(19) == 0)
            throw new RuntimeException("FAIL - testQueries - Filter query should return atleast 1 record!");

        // TODO Vikram - test real index once implemented

        // TODO Vikram - test string index once implemented

        // TODO Vikram - test no index once implemented

    }

    private static void testSortQuery(String relationName, Function<Integer, Integer> verifyResults) throws Exception {
        cleanupTestInputFiles();

        OutputStream teeStream = buildTeeStream();
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"Sort(4, " + TARGET_VECTOR_FILE_PATH + ", 70000, 2, 4)");
        System.setOut(new PrintStream(teeStream, true));
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});
        teeStream.close();
        verifyResults.apply(Integer.MAX_VALUE);
    }

    private static void testRangeQuery(String relationName, Function<Integer, Integer> verifyResults, Runnable forceRestartDb) throws Exception {
        cleanupTestInputFiles();

        // No Index
        OutputStream teeStream = buildTeeStream();
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"Range(4, " + TARGET_VECTOR_FILE_PATH + ", 70000, N, 1, 2, 3, 4)");
        System.setOut(new PrintStream(teeStream, true));
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});
        teeStream.close();
        verifyResults.apply(70000);

        cleanupTestInputFiles();
        teeStream = buildTeeStream();
        System.out.println("Who needs yoga when we have LSH? This will take ~2min. Relax...");

        // With Index
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"Range(4, " + TARGET_VECTOR_FILE_PATH + ", 70000, Y, 1, 3, 4)");
        System.setOut(new PrintStream(teeStream, true));
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});
        teeStream.close();
        verifyResults.apply(70000);

        cleanupTestInputFiles();
        teeStream = buildTeeStream();

        // Low numbuf
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"Range(4, " + TARGET_VECTOR_FILE_PATH + ", 70000, N, 4)");
        System.setOut(new PrintStream(teeStream, true));
        try {
            DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(10)});
        } catch (Exception e) {
            System.out.println("Raised exception as expected.");
            teeStream.close();
        }
        //      Simulate a program re-launch by force restarting DB
        forceRestartDb.run();
    }

    public static void testNnQuery(String relationName, Function<Integer, Integer> verifyResults, Runnable forceRestartDb) throws Exception {
        cleanupTestInputFiles();

        // No Index
        OutputStream teeStream = buildTeeStream();
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"NN(4, " + TARGET_VECTOR_FILE_PATH + ", 5, N, 3, 4)");
        System.setOut(new PrintStream(teeStream, true));
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});
        teeStream.close();
        if(verifyResults.apply(Integer.MAX_VALUE) != 5)
            throw new RuntimeException("FAIL - testQueries - NN query should contain 5 results!");

        cleanupTestInputFiles();
        teeStream = buildTeeStream();
        System.out.println("Indices help prune and speed up queries....or do they? This is LSH. This will take ~2min. Relax...");

        // With Index
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"NN(4, " + TARGET_VECTOR_FILE_PATH + ", 5, Y, 1, 4)");
        System.setOut(new PrintStream(teeStream, true));
        DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(DbmsEntry.DB_SIZE_IN_PAGES)});
        teeStream.close();
        if(verifyResults.apply(Integer.MAX_VALUE) != 5)
            throw new RuntimeException("FAIL - testQueries - NN query should contain 5 results!");

        cleanupTestInputFiles();
        teeStream = buildTeeStream();

        // Low numbuf
        createFileForTestInput(QUERY_SPECIFICATION_FILE_PATH,"NN(4, " + TARGET_VECTOR_FILE_PATH + ", 5, N, 1, 2, 4)");
        System.setOut(new PrintStream(teeStream, true));
        try {
            DbmsEntry.handleQueryCommand(new String[] { SupportedCommands.QUERY.getCommand(), relationName, "rel2NotUsed", QUERY_SPECIFICATION_FILE_PATH, String.valueOf(10)});
        } catch (Exception e) {
            System.out.println("Raised exception as expected.");
            teeStream.close();
        }
        //      Simulate a program re-launch by force restarting DB
        forceRestartDb.run();
    }

    private static String getDbPath(String dbName) {
        return "/tmp/" + System.getProperty("user.name") + "." + dbName + "-db";
    }

    private static void createFileForTestInput(String fileName, String data) throws Exception {
        File file = new File(fileName);
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(data);
        writer.close();
    }

    private static void cleanupTestInputFiles() throws Exception {
        Files.deleteIfExists(Paths.get(QUERY_SPECIFICATION_FILE_PATH));
        Files.deleteIfExists(Paths.get(FILE_OUTPUT_STREAM_PATH));
    }

    private static OutputStream buildTeeStream() throws Exception {
        return new OutputStream() {
            final PrintStream consoleOut = System.out;
            final FileOutputStream fileOut = new FileOutputStream(FILE_OUTPUT_STREAM_PATH);
            @Override
            public void write(int b) throws IOException {
                consoleOut.write(b);
                fileOut.write(b);
            }

            @Override
            public void flush() throws IOException {
                consoleOut.flush();
                fileOut.flush();
            }

            @Override
            public void close() throws IOException {
                fileOut.close();
                System.setOut(consoleOut);
            }
        };
    }

    private static <T> void readFullBTreeAndCompare(String btreeFileName, HashSet<T> expectedSet, int attrType) throws Exception {
        HashSet<T> keysFromTree = new HashSet<>();

        BTreeFile bTreeFile = new BTreeFile(btreeFileName);
        BTFileScan scan = bTreeFile.new_scan(null, null);
        KeyDataEntry entry = scan.get_next();
        while(entry != null) {
            if(attrType == AttrType.attrInteger)
                keysFromTree.add((T)(((IntegerKey)entry.key).getKey()));
            else if(attrType == AttrType.attrReal)
                keysFromTree.add((T)(((RealKey)entry.key).getKey()));
            else
                keysFromTree.add((T)(((StringKey)entry.key).getKey()));
            entry = scan.get_next();
        }
        scan.DestroyBTreeFileScan();
        bTreeFile.close();

        if(keysFromTree.isEmpty() || (! keysFromTree.equals(expectedSet)))
            throw new RuntimeException("FAIL - Mismatch between data in txt file and bTree!");
    }
}
