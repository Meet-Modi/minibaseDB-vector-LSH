package scripts.phaseThree;

import diskmgr.Pcounter;
import global.AttrType;
import global.SystemDefs;
import global.Vector100Dtype;
import heap.FieldNumberOutOfBoundException;
import heap.HFBufMgrException;
import heap.HFDiskMgrException;
import heap.HFException;
import heap.Heapfile;
import heap.InvalidSlotNumberException;
import heap.InvalidTupleSizeException;
import heap.InvalidTypeException;
import heap.SpaceNotAvailableException;
import heap.Tuple;
import iterator.FileScan;
import iterator.FileScanException;
import iterator.FldSpec;
import iterator.InvalidRelation;
import iterator.RelSpec;
import iterator.TupleUtilsException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import static global.GlobalConst.NUMBUF;
import static global.SystemDefs.JavabaseBM;

public class DbmsEntry {

    public static final int DB_SIZE_IN_PAGES = NUMBUF * 10;
    public static final short MAX_STRING_LENGTH = 64;

    private static final Scanner scanner = new Scanner(System.in);

    private static String currentOpenDb = null;

    public static void main(String[] args) throws Exception {

        while (true) {
            System.out.println("\nEnter your command");

            String input = scanner.nextLine();
            if (input.isBlank())
                break;

            String[] commandParts = input.trim().split(" ");
            if (commandParts[0].equals(SupportedCommands.OPEN_DB.getCommand())) {
                handleDbOpenCommand(commandParts);
            } else if (commandParts[0].equals(SupportedCommands.CLOSE_DB.getCommand())) {
                handleDbCloseCommand();
            } else if (commandParts[0].equals(SupportedCommands.BATCH_CREATE.getCommand())) {
                handleBatchCreateCommand(commandParts);
            } else {
                System.out.println("Unrecognized command. Quitting...");
                break;
            }
        }
        handleDbCloseCommand();
        scanner.close();
        System.out.println("Quitting...");
    }

    public static void handleDbOpenCommand(String[] commandParts) {
        if (currentOpenDb != null) {
            System.out.println(currentOpenDb + " is still open. Close it before opening another db.");
            return;
        }
        if ((commandParts.length != 2) || (!commandParts[0].equals(SupportedCommands.OPEN_DB.getCommand()))) {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.OPEN_DB.getUsage());
            return;
        }

        String dbName = commandParts[1];
        String dbPath = "/tmp/" + System.getProperty("user.name") + "." + dbName + "-db";

        if (Files.exists(Paths.get(dbPath))) {
            System.out.println(dbName + " already exists. Restarting...");
            SystemDefs.MINIBASE_RESTART_FLAG = true;
        }
        new SystemDefs(dbPath, DB_SIZE_IN_PAGES, DB_SIZE_IN_PAGES, "Clock");
        try {
            createOrOpenDbMetaDataFile(dbName);
        } catch (Exception e) {
            System.out.println("Error creating metadata file: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        System.out.println("Opened db " + dbName + " at " + dbPath);
        currentOpenDb = dbName;
    }

    public static void handleDbCloseCommand() throws Exception {
        if (currentOpenDb == null) {
            System.out.println("No DB open currently. Nothing closed.");
            return;
        }
        JavabaseBM.flushAllPages();
        System.out.println(currentOpenDb + " pages flushed and DB closed.");
        currentOpenDb = null;
    }

    public static void handleBatchCreateCommand(String[] commandParts)
            throws FileNotFoundException, IOException {
        String dataFilePath = commandParts[1];
        String relName = commandParts[2];

        if (currentOpenDb == null) {
            System.out.println("No DB open currently. Please open a new db first.");
            return;
        }

        if (commandParts.length != 3 && !commandParts[0].equals(SupportedCommands.BATCH_CREATE.getCommand())) {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.BATCH_CREATE.getUsage());
            return;
        }

        if (dataFilePath == null || relName == null) {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.BATCH_CREATE.getUsage());
            return;
        }

        if (!Files.exists(Paths.get(dataFilePath))) {
            System.out.println("Data file " + dataFilePath + " does not exist.");
            return;
        }

        try{
            if (relExists(relName)) {
                System.out.println("Relation " + relName + " already exists. Please delete it first.");
                return;
            }
        } catch (Exception e) {
            System.out.println("Error checking if relation exists: " + e.getMessage());
            e.printStackTrace();
            return;
        }
            
        Pcounter.initialize();
        System.out.println("Creating relation " + relName + " from file " + dataFilePath);
        System.out.println("Metadata file : " + getCurrentOpenDBRelMetaDataFile(relName));
        System.out.println("Data file : " + getCurrentOpenDBRelDataFile(relName));

        BufferedReader br = new BufferedReader(new FileReader(dataFilePath));
        String line = br.readLine();
        if (line == null) {
            br.close();
            throw new IOException("Empty file");
        }

        short numAttributes = Short.parseShort(line.trim());
        line = br.readLine();
        if (line == null) {
            br.close();
            throw new IOException("Attribute types missing");
        }
        String[] attributeTypes = line.split("\\s+");
        if (attributeTypes.length != numAttributes) {
            br.close();
            throw new IOException("Attribute count and number of attribute types provided mismatch");
        }
        try {
            insertIntoDbMetaDataFile(relName);
            createRelMetaDataFile(relName, numAttributes, attributeTypes);
            createDataFile(relName);
            batchInsertDataIntoRel(br, relName, numAttributes, attributeTypes);
        } catch (Exception e) {
            System.out.println("Error running batchcreate:  metadata or data files: " + e.getMessage());
            e.printStackTrace();
            br.close();
            return;
        }
        br.close();
        Pcounter.printPcounter();
    }

    // Helper methods
    private static boolean relExists(String relName) throws IOException, FileScanException,
            TupleUtilsException, InvalidRelation, Exception {
        if (checkIfRelExistsInDbMetaDataFile(relName)) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean checkIfRelExistsInDbMetaDataFile(String relName)
            throws IOException, FileScanException, TupleUtilsException, InvalidRelation, Exception {
        // No need to check if metadata file exists, as it is created when the db is opened

        short[] stringLengths = new short[1];
        stringLengths[0] = MAX_STRING_LENGTH;
        FileScan dbMetaDataScan = new FileScan(getDbMetadataFile(currentOpenDb),
                new AttrType[] { new AttrType(AttrType.attrString) }, stringLengths, (short) 1, 1,
                new FldSpec[] { new FldSpec(new RelSpec(RelSpec.outer), 1) }, null);

        Tuple t = dbMetaDataScan.get_next();
        while (t != null) {
            if (t.getStrFld(1).equals(relName)) {
                dbMetaDataScan.close();
                return true;
            }
            t = dbMetaDataScan.get_next();
        }
        dbMetaDataScan.close();
        return false;
    }

    private static void insertIntoDbMetaDataFile(String relName) throws HFException, HFBufMgrException,
            HFDiskMgrException, IOException, InvalidTypeException, InvalidTupleSizeException,
            FieldNumberOutOfBoundException, InvalidSlotNumberException, SpaceNotAvailableException {
        Heapfile dbMetaDataFile = new Heapfile(getDbMetadataFile(currentOpenDb));
        AttrType[] dbmetaDataAttrTypes = new AttrType[1];
        dbmetaDataAttrTypes[0] = new AttrType(AttrType.attrString);
        short[] stringLengths = new short[1];
        stringLengths[0] = MAX_STRING_LENGTH;
        Tuple dbMetaDataTuple = new Tuple();
        dbMetaDataTuple.setHdr((short) 1, dbmetaDataAttrTypes, stringLengths);
        dbMetaDataTuple.setStrFld(1, relName);
        dbMetaDataFile.insertRecord(dbMetaDataTuple.getTupleByteArray());
    }

    private static void batchInsertDataIntoRel(BufferedReader br, String relName, short numAttributes,
            String[] attributeTypes)
            throws HFException, HFBufMgrException, HFDiskMgrException, FieldNumberOutOfBoundException,
            FileScanException, TupleUtilsException, InvalidRelation, IOException, Exception {
        short stringAttributeCount = 0;

        Heapfile relMetaDataFile = new Heapfile(getCurrentOpenDBRelMetaDataFile(relName));
        FileScan relMetaDataScan = new FileScan(getCurrentOpenDBRelMetaDataFile(relName),
                new AttrType[] { new AttrType(AttrType.attrInteger) }, null, (short) 1, 1,
                new FldSpec[] { new FldSpec(new RelSpec(RelSpec.outer), 1) }, null);

        AttrType[] metadataAttrTypes = new AttrType[relMetaDataFile.getRecCnt()];
        Tuple t = relMetaDataScan.get_next();
        int i = 0;
        while (t != null) {
            metadataAttrTypes[i] = new AttrType((t.getIntFld(1)));
            if (metadataAttrTypes[i].attrType == AttrType.attrString) {
                stringAttributeCount++;
            }
            t = relMetaDataScan.get_next();
            i++;
        }
        relMetaDataScan.close();

        short[] stringLengths = new short[stringAttributeCount];
        Arrays.fill(stringLengths, MAX_STRING_LENGTH);

        t = new Tuple();
        t.setHdr(numAttributes, metadataAttrTypes, stringLengths);

        Heapfile file = new Heapfile(getCurrentOpenDBRelDataFile(relName));
        String tupleValue;
        boolean endOfFile = false;

        while (true) {
            ArrayList<Vector100Dtype> vectorsInDataLine = new ArrayList<>();
            for (i = 0; i < numAttributes; i++) {
                tupleValue = br.readLine();
                if (tupleValue == null) {
                    endOfFile = true;
                    break;
                }

                tupleValue = tupleValue.trim();
                AttrType type = metadataAttrTypes[i];

                switch (type.attrType) {
                    case AttrType.attrInteger:
                        t.setIntFld(i + 1, Integer.parseInt(tupleValue));
                        break;
                    case AttrType.attrReal:
                        t.setFloFld(i + 1, Float.parseFloat(tupleValue));
                        break;
                    case AttrType.attrString:
                        t.setStrFld(i + 1, tupleValue);
                        break;
                    case AttrType.attrVector100D:
                        Vector100Dtype input_vector100D = new Vector100Dtype();
                        vectorsInDataLine.add(input_vector100D);

                        String[] tupleValueSplit = tupleValue.split("\\s+");
                        for (int j = 0; j < 100; j++) {
                            input_vector100D.vector[j] = (short) Float.parseFloat(tupleValueSplit[j]);
                        }
                        t.set100DVectFld(i + 1, input_vector100D);
                        break;
                    default:
                        throw new IOException("Unknown attribute type" + type.attrType);
                }
            }
            if (endOfFile)
                break;
            file.insertRecord(t.getTupleByteArray());
        }
        JavabaseBM.flushAllPages();
        System.out.println(
                "File " + getCurrentOpenDBRelDataFile(relName) + " created with " + file.getRecCnt() + " records.");
    }

    private static void createDataFile(String relName)
            throws HFException, HFBufMgrException, HFDiskMgrException, IOException {
        new Heapfile(getCurrentOpenDBRelDataFile(relName));
    }

    private static void createRelMetaDataFile(String relName, short numAttributes, String[] attributeTypes)
            throws IOException, InvalidTypeException, InvalidTupleSizeException, HFException, HFBufMgrException,
            HFDiskMgrException, FieldNumberOutOfBoundException, InvalidSlotNumberException, SpaceNotAvailableException {

        AttrType[] dbmetaDataAttrTypes = new AttrType[1];
        dbmetaDataAttrTypes[0] = new AttrType(AttrType.attrString);
        short[] stringLengths = new short[1];
        stringLengths[0] = MAX_STRING_LENGTH;
        Tuple dbMetaDataTuple = new Tuple();
        dbMetaDataTuple.setHdr((short) 1, dbmetaDataAttrTypes, stringLengths);
        dbMetaDataTuple.setStrFld(1, relName);

        Heapfile dbMetaDataFile = new Heapfile(getDbMetadataFile(currentOpenDb));
        dbMetaDataFile.insertRecord(dbMetaDataTuple.getTupleByteArray());

        AttrType[] attrTypes = new AttrType[numAttributes];

        Tuple metaDataTuple = new Tuple();
        metaDataTuple.setHdr((short) 1, new AttrType[] { new AttrType(AttrType.attrInteger) }, null);

        Heapfile dataFileMetaData = new Heapfile(getCurrentOpenDBRelMetaDataFile(relName));
        for (int i = 0; i < numAttributes; i++) {
            int type = Integer.parseInt(attributeTypes[i].trim());
            switch (type) {
                case 1:
                    type = 1; // integer
                    break;
                case 2:
                    type = 2; // real
                    break;
                case 3:
                    type = 0; // string
                    break;
                case 4:
                    type = 5; // 100D-vector.
                    break;
                default:
                    throw new IOException("Unknown attribute type" + type);
            }
            attrTypes[i] = new AttrType(type);
            metaDataTuple.setIntFld(1, type);
            dataFileMetaData.insertRecord(metaDataTuple.getTupleByteArray());
        }
    }

    private static void createOrOpenDbMetaDataFile(String dbName)
            throws HFException, HFBufMgrException, HFDiskMgrException, IOException {
        new Heapfile(getDbMetadataFile(dbName));
        return;
    }

    private static String getDbMetaDataFileName(String dbName) {
        return dbName + "-db.metadata";
    }

    private static String getRelMetaDataFileName(String relName) {
        return relName + ".metadata";
    }

    private static String getRelDataFileName(String relName) {
        return relName + ".data";
    }

    private static String getDbMetadataFile(String dbName) {
        return "/tmp/" + System.getProperty("user.name") + "." + dbName + "."
                + getDbMetaDataFileName(dbName);
    }

    private static String getCurrentOpenDBRelMetaDataFile(String relName) {
        return "/tmp/" + System.getProperty("user.name") + "." + currentOpenDb + "." + getRelMetaDataFileName(relName);
    }

    private static String getCurrentOpenDBRelDataFile(String relName) {
        return "/tmp/" + System.getProperty("user.name") + "." + currentOpenDb + "." + getRelDataFileName(relName);
    }

}
