package scripts.phaseThree;

import btree.*;
import diskmgr.Pcounter;
import global.AttrType;
import global.RID;
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
import heap.Scan;
import heap.SpaceNotAvailableException;
import heap.Tuple;
import iterator.*;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.IntStream;

import LSHFIndex.LSHFIndex;
import scripts.Query;

import static global.GlobalConst.NUMBUF;
import static global.SystemDefs.JavabaseBM;

public class DbmsEntry {
    public static final int DB_SIZE_IN_PAGES = NUMBUF * 10;
    public static final short MAX_STRING_LENGTH = 64;
    public static final String DB_METADATA_FILE_NAME = "db.metadata";
    private static final Scanner scanner = new Scanner(System.in);
    private static String currentOpenDb = null;

    // DBMETADATA attribute definitions
    private static final AttrType[] DBMETADATA_TUPLE_ATTR_TYPES = new AttrType[1];
    static
    {
        DBMETADATA_TUPLE_ATTR_TYPES[0] = new AttrType(AttrType.attrInteger);
    }
    private static final FldSpec[] DBMETADATA_TUPLE_PROJ_LIST = new FldSpec[DBMETADATA_TUPLE_ATTR_TYPES.length];
    static
    {
        IntStream.range(0, DBMETADATA_TUPLE_PROJ_LIST.length).forEach(i -> DBMETADATA_TUPLE_PROJ_LIST[i] = new FldSpec(new RelSpec(RelSpec.outer), i + 1));
    }

    enum IndexType {LSHF, BTREE};

    public static void main(String[] args) throws Exception
    {
        while (true)
        {
            Pcounter.initialize();
            System.out.println("\nEnter your command");

            String input = scanner.nextLine();
            if (input.isBlank())
                break;

            String[] commandParts = input.trim().split(" ");
            if (commandParts[0].equals(SupportedCommands.OPEN_DB.getCommand()))
            {
                handleDbOpenCommand(commandParts);
            }
            else if (commandParts[0].equals(SupportedCommands.CLOSE_DB.getCommand()))
            {
                handleDbCloseCommand();
            }
            else if (commandParts[0].equals(SupportedCommands.BATCH_CREATE.getCommand()))
            {
                handleBatchCreateCommand(commandParts);
            }
            else if (commandParts[0].equals(SupportedCommands.CREATE_INDEX.getCommand()))
            {
                handleIndexCreateCommand(commandParts);
            }
            else if (commandParts[0].equals(SupportedCommands.BATCH_INSERT.getCommand()))
            {
                handleBatchInsertCommand(commandParts);
            }       
            else if (commandParts[0].equals(SupportedCommands.QUERY.getCommand()))
            {
                handleQueryCommand(commandParts);
            }
            else
            {
                System.out.println("Unrecognized command. Quitting...");
                break;
            }
            Pcounter.printPcounter();
        }
        handleDbCloseCommand();
        scanner.close();
        System.out.println("Quitting...");
    }

    public static void handleDbOpenCommand(String[] commandParts) throws Exception {
        handleDbOpenCommand(commandParts, DB_SIZE_IN_PAGES);
    }

    public static void handleDbOpenCommand(String[] commandParts, int numBuf)
            throws
            Exception
    {
        if (currentOpenDb != null)
        {
            System.out.println(currentOpenDb + " is still open. Close it before opening another db.");
            return;
        }
        if ((commandParts.length != 2) || (!commandParts[0].equals(SupportedCommands.OPEN_DB.getCommand())))
        {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.OPEN_DB.getUsage());
            return;
        }

        String dbName = commandParts[1];
        String dbPath = getDbPath(dbName);

        SystemDefs.MINIBASE_RESTART_FLAG = false;
        if (Files.exists(Paths.get(dbPath)))
        {
            System.out.println(dbName + " already exists. Restarting...");
            SystemDefs.MINIBASE_RESTART_FLAG = true;
        }
        new SystemDefs(dbPath, DB_SIZE_IN_PAGES, numBuf, "Clock");
        createOrOpenDbMetaDataFile(dbName);

        System.out.println("Opened db " + dbName + " at " + dbPath);
        currentOpenDb = dbName;
    }

    public static void handleDbCloseCommand()
            throws
            Exception
    {
        if (currentOpenDb == null)
        {
            System.out.println("No DB open currently. Nothing closed.");
            return;
        }
        SystemDefs.JavabaseBM.flushAllPages();
        System.out.println(currentOpenDb + " pages flushed and DB closed.");
        currentOpenDb = null;
    }

    public static void handleBatchCreateCommand(String[] commandParts) throws
                                                                       Exception
    {
        if (currentOpenDb == null)
        {
            System.out.println("No DB open currently. Please open a new db first.");
            return;
        }

        if (commandParts.length != 3 || !commandParts[0].equals(SupportedCommands.BATCH_CREATE.getCommand()))
        {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.BATCH_CREATE.getUsage());
            return;
        }

        String dataFilePath = commandParts[1];
        String relName = commandParts[2];

        if (dataFilePath == null || relName == null)
        {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.BATCH_CREATE.getUsage());
            return;
        }

        if (!Files.exists(Paths.get(dataFilePath)))
        {
            System.out.println("Data file " + dataFilePath + " does not exist.");
            return;
        }

        if (relExists(relName))
        {
            System.out.println("Relation " + relName + " already exists.");
            return;
        }

        System.out.println("Creating relation " + relName + " from file " + dataFilePath);
        System.out.println("Db Metadata file : " + DB_METADATA_FILE_NAME);
        System.out.println("Relation Metadata file : " + getRelMetaDataFileName(relName));
        System.out.println("Data file : " + getRelDataFileName(relName));

        BufferedReader br = new BufferedReader(new FileReader(dataFilePath));
        String line = br.readLine();
        if (line == null)
        {
            br.close();
            throw new Exception("Empty file");
        }

        short numAttributes = Short.parseShort(line.trim());
        line = br.readLine();
        if (line == null)
        {
            br.close();
            throw new Exception("Attribute types missing");
        }
        String[] attributeTypes = line.split("\\s+");
        if (attributeTypes.length != numAttributes)
        {
            br.close();
            throw new Exception("Attribute count and number of attribute types provided mismatch");
        }
        insertRelIntoDbMetaDataFile(relName);
        createRelMetaDataFile(relName, numAttributes, attributeTypes);
        createDataFile(relName);
        batchInsertDataIntoRel(br, relName, numAttributes);
        br.close();
    }

    public static void handleIndexCreateCommand(String[] commandParts)
    {
        String relName = commandParts[1];
        String columnId = commandParts[2];

        if (currentOpenDb == null)
        {
            System.out.println("No DB open currently. Please open a new db first.");
            return;
        }

        if (commandParts.length < 3 || !commandParts[0].equals(SupportedCommands.CREATE_INDEX.getCommand()))
        {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.CREATE_INDEX.getUsage());
            return;
        }

        if (relName == null || columnId == null)
        {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.CREATE_INDEX.getUsage());
            return;
        }

        AttrType[] attrTypes;
        int columnIdInt = Integer.parseInt(columnId.trim());

        try
        {
            if (!relExists(relName))
            {
                System.out.println("Relation " + relName + " does not exist. Please create it first.");
                return;
            }

            if (indexExists(relName, columnIdInt))
            {
                System.out.println("Index on column " + columnIdInt + " of relation " + relName + " already exists.");
                return;
            }
            attrTypes = getRelationAttrTypes(relName);
        }
        catch (Exception e)
        {
            System.out.println("Error checking if relation exists: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (columnIdInt < 1 || columnIdInt > attrTypes.length)
        {
            System.out.println("Invalid column id. Please provide a valid column id.");
            return;
        }


        if (attrTypes[columnIdInt - 1].attrType == AttrType.attrVector100D)
        {
            if (commandParts.length < 5)
            {
                System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.CREATE_INDEX.getUsage());
                return;
            }

            int numLayers = Integer.parseInt(commandParts[3]);
            int numHashes = Integer.parseInt(commandParts[4]);
            int binLength = 1_000_000_000;
            try
            {
                LSHFIndex lshfIndex = new LSHFIndex(relName, numLayers, binLength, numHashes, columnIdInt);
                populateLSHFIndexOnExistingRelColumn(lshfIndex, relName, columnIdInt);
                insertIndexIntoDbMetaDataFile(relName, columnIdInt, IndexType.LSHF.toString());
            }
            catch (Exception e)
            {
                System.out.println("Error creating LSHF index: " + e.getMessage());
                e.printStackTrace();
                return;
            }
            System.out.println("LSHF index created on column " + columnIdInt + " of relation " + relName);
        }
        else if ((attrTypes[columnIdInt - 1].attrType == AttrType.attrString) || (attrTypes[columnIdInt - 1].attrType == AttrType.attrInteger)
                || (attrTypes[columnIdInt - 1].attrType == AttrType.attrReal))
        {
            int keyType, keySize;
            switch (attrTypes[columnIdInt - 1].attrType)
            {
                case AttrType.attrString:
                    keyType = AttrType.attrString;
                    keySize = MAX_STRING_LENGTH;
                    break;
                case AttrType.attrInteger:
                    keyType = AttrType.attrInteger;
                    keySize = 4;
                    break;
                case AttrType.attrReal:
                    keyType = AttrType.attrReal;
                    keySize = 4;
                    break;
                default:
                    System.out.println("Index creation not supported for this type of column. Please use LSHF index for vector columns.");
                    return;
            }

            try
            {
                BTreeFile bTreeFile = new BTreeFile(getBTreeFileName(relName, columnIdInt), keyType, keySize, 1); // TODO : Full Delete for now
                populateBTreeIndexOnExistingRelColumn(bTreeFile, relName, columnIdInt);
                insertIndexIntoDbMetaDataFile(relName, columnIdInt, IndexType.BTREE.toString());
            }
            catch (Exception e)
            {
                System.out.println("Error creating BTree index: " + e.getMessage());
                e.printStackTrace();
                return;
            }
            System.out.println("BTree index created on column " + columnIdInt + " of relation " + relName);
        }
        else
        {
            // TODO : Handle scenario where the column is of type symbol or null.
            System.out.println("Index creation not supported for this type of column. Please use LSHF index for vector columns.");
        }
    }

    public static void handleBatchInsertCommand(String[] commandParts) throws Exception 
    {
        if (currentOpenDb == null) {
            System.out.println("No DB open currently. Please open a new db first.");
            return;
        }

        String dataFilePath = commandParts[1];
        String relName = commandParts[2];

        if (commandParts.length != 3 || !commandParts[0].equals(SupportedCommands.BATCH_INSERT.getCommand())) {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.BATCH_INSERT.getUsage());
            return;
        }

        if (dataFilePath == null || relName == null) {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.BATCH_INSERT.getUsage());
            return;
        }

        if (!Files.exists(Paths.get(dataFilePath))) {
            System.out.println("Data file " + dataFilePath + " does not exist.");
            return;
        }

        try {
            if(!relExists(relName)) {
                System.out.println("Relation " + relName + " does not exist. Please create it first.");
                return;
            }
        }
        catch (Exception e) {
            System.out.println("Error checking relation: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        BufferedReader br = new BufferedReader(new FileReader(dataFilePath));
        String line = br.readLine();
        if (line == null)
        {
            br.close();
            System.out.println("Empty file");
            return;
        }

        short numAttributes = Short.parseShort(line.trim());
        line = br.readLine();
        if (line == null)
        {
            br.close();
            System.out.println("Attribute types missing");
            return;
        }
        String[] attributeTypes = line.split("\\s+");
        if (attributeTypes.length != numAttributes)
        {
            br.close();
            System.out.println("Attribute count and number of attribute types provided mismatch");
            return;
        }

        if(! Arrays.equals(
                Arrays.stream(getRelationAttrTypes(relName)).mapToInt(t -> t.attrType).toArray(),
                Arrays.stream(attributeTypes).mapToInt(s -> getMinibaseAttrTypeForInputAttrType(Integer.parseInt(s))).toArray()
        )) {
            System.out.println("Mismatch between attribute types in file and in relation");
            return;
        }

        batchInsertDataIntoRel(br, relName, numAttributes);
        br.close();
    }

    public static void handleQueryCommand(String[] commandParts) throws
                                                                 Exception
    {
        String rel1Name = commandParts[1];
        String rel2Name = commandParts[2];
        String querySpecificaionFile = commandParts[3];
        String numBuf = commandParts[4];

        // Basic null input error checks
        if (currentOpenDb == null)
        {
            System.out.println("No DB open currently. Please open a new db first.");
            return;
        }
        if (commandParts.length != 5 && !commandParts[0].equals(SupportedCommands.QUERY.getCommand()))
        {
            System.out.println("Incorrect usage. Correct usage = " + SupportedCommands.QUERY.getUsage());
            return;
        }
        if (rel1Name == null)
        {
            System.out.println("Relation 1 name is null. Please provide a valid relation name.");
            return;
        }
        if(! checkIfRelExistsInDbMetaDataFile(rel1Name))
        {
            System.out.println(rel1Name + " relation does not exist. Please ensure it has been created before using it.");
            return;
        }
        // TODO Divesh - Check if rel2Name exists (Similar to above) in DbMetadata for Joins, since that's the only operator that uses rel2
        if (rel2Name == null)
        {
            System.out.println("Relation 2 name is null. Please provide a valid relation name.");
            return;
        }
        if (querySpecificaionFile == null)
        {
            System.out.println("query specification file is null. Please provide a valid query specific file.");
            return;
        }

        // Start reading Query specification and process queries
        BufferedReader br = new BufferedReader(new FileReader(querySpecificaionFile));
        String querySpecification = br.readLine();
        if (querySpecification == null)
        {
            System.out.println("query specification file is null. Please provide a valid query specific file.");
            return;
        }
        querySpecification = querySpecification.trim();

        // Restart DB with numbuf
        final String CLOSED_DB_NAME = currentOpenDb;
        handleDbCloseCommand();
        handleDbOpenCommand(new String[] {SupportedCommands.OPEN_DB.getCommand(), CLOSED_DB_NAME}, Integer.parseInt(numBuf));

        // Query specification handling
        if ( querySpecification.startsWith("Sort(") || querySpecification.startsWith("Range(") || querySpecification.startsWith("NN("))
        {
            Query.queryHandler(querySpecificaionFile, numBuf, rel1Name, getRelationAttrTypes(rel1Name));
        }

        else if (querySpecification.startsWith("Filter("))
        {
            int outputFieldNumbers[];

            String specifications = querySpecification.substring("Filter(".length(), querySpecification.length() - 1);
            String[] parameters = specifications.split(",");

            // Extract the parameters from the query specification.
            int non_vector_field_number = Integer.parseInt(parameters[0].trim());
            int target_value = Integer.parseInt(parameters[1].trim());
            String k_value = parameters[2].trim();
            String indexOption = parameters[3].trim();

            if(indexOption.equals("Y") && ! DbmsEntry.checkIfIndexExistsInDbMetaDataFile(rel1Name, non_vector_field_number)) {
                System.out.println("Index option is Y but index does not exist for relation = " + rel1Name + " on fieldNumber = " + non_vector_field_number + ". Pls create an index before using it");
                return;
            }

            AttrType[] attrTypes = getRelationAttrTypes(rel1Name);
            if(attrTypes[non_vector_field_number - 1].attrType == AttrType.attrVector100D) {
                System.out.println("Filter query is not possible on a 100D vector column.");
                return;
            }

            outputFieldNumbers = new int[parameters.length - 4];
            for (int i = 4; i < parameters.length; i++)
            {
                outputFieldNumbers[i - 4] = Integer.parseInt(parameters[i].trim());
            }

            if (indexOption.equals("Y"))
            {
                // TODO Divesh: Currently, btreeFile scan only works for integer and string.
                //       Need to test for integers first. then extend functionality for other data types.
                Heapfile dataFile = new Heapfile(getRelDataFileName(rel1Name));
                Heapfile queryResult = new Heapfile(Query.QUERY_RESULTS_HEAPFILE_NAME);
                KeyClass key = new IntegerKey(target_value);
                BTreeFile bTreeIndexFile = new BTreeFile(getBTreeFileName(rel1Name, non_vector_field_number));
                BTFileScan btScan = bTreeIndexFile.new_scan(key, key);
                try {
                    System.out.println("\n ---Output Tuples---");

                    KeyDataEntry entry = btScan.get_next();
                    while (entry != null) {
                        Tuple resultTuple = dataFile.getRecord(((LeafData) entry.data).getData());
                        resultTuple.setHdr(
                                (short) attrTypes.length,
                                attrTypes,
                                TupleUtils.getStrFieldLengthsForConstantStrSizes(attrTypes));

                        TupleUtils.printFieldsFromTuple(resultTuple, attrTypes, outputFieldNumbers);
                        queryResult.insertRecord(resultTuple.getTupleByteArray());
                        entry = btScan.get_next();
                    }
                } finally {
                    btScan.DestroyBTreeFileScan();
                    bTreeIndexFile.close();
                }
                System.out.println("\n ---End Output---");
            }
            else
            {
                // use normal file scan
            }
        }
        else if (querySpecification.startsWith("DJOIN("))
        {

            // Extract Range Query
            String rangeQuery = br.readLine();

            // remove spaces and trailling ,
            rangeQuery = rangeQuery.trim();
            rangeQuery = rangeQuery.substring(0, rangeQuery.length() - 1);

            String distanceJoinRangeQuerySpecificationFile = rel1Name + rel2Name + "DJOIN";
            // Step 2: using results from step 1, join on relation 2.
        }

        // Restart DB with old buffer count
        handleDbCloseCommand();
        handleDbOpenCommand(new String[] {SupportedCommands.OPEN_DB.getCommand(), CLOSED_DB_NAME});
    }

    // Helper methods
    private static void populateBTreeIndexOnExistingRelColumn(BTreeFile bTreeFile, String relName, int columnId)
            throws
            HFException,
            HFBufMgrException,
            HFDiskMgrException,
            IOException,
            InvalidTypeException,
            InvalidTupleSizeException,
            FieldNumberOutOfBoundException,
            InvalidSlotNumberException,
            SpaceNotAvailableException,
            FileScanException,
            TupleUtilsException,
            InvalidRelation,
            Exception
    {

        Heapfile heapfile = new Heapfile(getRelDataFileName(relName));
        Scan scan = heapfile.openScan();
        RID rid = new RID();
        Tuple tuple = new Tuple();

        AttrType[] attrTypes = getRelationAttrTypes(relName);
        short stringAttributeCount = 0;

        for (int i = 0; i < attrTypes.length; i++)
        {
            if (attrTypes[i].attrType == AttrType.attrString)
            {
                stringAttributeCount++;
            }
        }

        short[] stringLengths = new short[stringAttributeCount];
        Arrays.fill(stringLengths, MAX_STRING_LENGTH);

        while ((tuple = scan.getNext(rid)) != null)
        {
            tuple.setHdr((short) attrTypes.length, attrTypes, stringLengths);
            KeyClass key = null;
            if (attrTypes[columnId - 1].attrType == AttrType.attrString)
            {
                key = new StringKey(tuple.getStrFld(columnId));
            }
            else if (attrTypes[columnId - 1].attrType == AttrType.attrInteger)
            {
                key = new IntegerKey(tuple.getIntFld(columnId));
            }
            else if (attrTypes[columnId - 1].attrType == AttrType.attrReal)
            {
                key = new RealKey(tuple.getFloFld(columnId));
            }
            else
            {
                throw new IOException("Unknown attribute type" + attrTypes[columnId].attrType);
            }
            bTreeFile.insert(key, rid);
        }
        scan.closescan();
        bTreeFile.close();
    }

    private static void populateLSHFIndexOnExistingRelColumn(LSHFIndex lshfIndex, String relName, int columnId)
            throws
            HFException,
            HFBufMgrException,
            HFDiskMgrException,
            IOException,
            InvalidTypeException,
            InvalidTupleSizeException,
            FieldNumberOutOfBoundException,
            InvalidSlotNumberException,
            SpaceNotAvailableException,
            FileScanException,
            TupleUtilsException,
            InvalidRelation,
            Exception
    {

        Heapfile heapfile = new Heapfile(getRelDataFileName(relName));
        Scan scan = heapfile.openScan();
        RID rid = new RID();
        Tuple tuple = new Tuple();

        AttrType[] attrTypes = getRelationAttrTypes(relName);
        short stringAttributeCount = 0;

        for (int i = 0; i < attrTypes.length; i++)
        {
            if (attrTypes[i].attrType == AttrType.attrString)
            {
                stringAttributeCount++;
            }
        }

        short[] stringLengths = new short[stringAttributeCount];
        Arrays.fill(stringLengths, MAX_STRING_LENGTH);

        while ((tuple = scan.getNext(rid)) != null)
        {
            tuple.setHdr((short) attrTypes.length, attrTypes, stringLengths);
            lshfIndex.insertRecord(tuple.get100DVectFld(columnId), rid);
        }
        scan.closescan();
    }

    public static AttrType[] getRelationAttrTypes(String relName)
            throws
            IOException,
            FileScanException,
            TupleUtilsException,
            InvalidRelation,
            Exception
    {
        Heapfile relMetaDataFile = new Heapfile(getRelMetaDataFileName(relName));
        FileScan relMetaDataScan = new FileScan(getRelMetaDataFileName(relName),
                new AttrType[]{new AttrType(AttrType.attrInteger)}, null, (short) 1, 1,
                new FldSpec[]{new FldSpec(new RelSpec(RelSpec.outer), 1)}, null);

        AttrType[] metadataAttrTypes = new AttrType[relMetaDataFile.getRecCnt()];
        Tuple t = relMetaDataScan.get_next();
        int i = 0;
        while (t != null)
        {
            metadataAttrTypes[i] = new AttrType((t.getIntFld(1)));
            t = relMetaDataScan.get_next();
            i++;
        }
        relMetaDataScan.close();
        return metadataAttrTypes;
    }

    private static boolean indexExists(String relName, int columnId)
            throws
            IOException,
            FileScanException,
            TupleUtilsException,
            InvalidRelation,
            Exception
    {
        // TODO : Check if the heap file also exists?
        if (checkIfIndexExistsInDbMetaDataFile(relName, columnId))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    private static boolean relExists(String relName)
            throws
            IOException,
            FileScanException,
            TupleUtilsException,
            InvalidRelation,
            Exception
    {
        // TODO : Check if the heap file also exists?
        if (checkIfRelExistsInDbMetaDataFile(relName))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public static boolean checkIfIndexExistsInDbMetaDataFile(String relName, int columnId) throws
                                                                                           Exception
    {
        // No need to check if metadata file exists, as it is created when the db is
        // opened

        short[] stringLengths = new short[1];
        stringLengths[0] = MAX_STRING_LENGTH;
        FileScan dbMetaDataScan = new FileScan(DB_METADATA_FILE_NAME,
                new AttrType[]{new AttrType(AttrType.attrString)}, stringLengths, (short) 1, 1,
                new FldSpec[]{new FldSpec(new RelSpec(RelSpec.outer), 1)}, null);

        Tuple t = dbMetaDataScan.get_next();
        while (t != null)
        {
            if (t.getStrFld(1).startsWith("index:" + relName + "." + columnId))
            {
                dbMetaDataScan.close();
                return true;
            }
            t = dbMetaDataScan.get_next();
        }
        dbMetaDataScan.close();
        return false;
    }

    private static boolean checkIfRelExistsInDbMetaDataFile(String relName)
            throws
            IOException,
            FileScanException,
            TupleUtilsException,
            InvalidRelation,
            Exception
    {
        // No need to check if metadata file exists, as it is created when the db is
        // opened

        short[] stringLengths = new short[1];
        stringLengths[0] = MAX_STRING_LENGTH;
        FileScan dbMetaDataScan = new FileScan(DB_METADATA_FILE_NAME,
                new AttrType[]{new AttrType(AttrType.attrString)}, stringLengths, (short) 1, 1,
                new FldSpec[]{new FldSpec(new RelSpec(RelSpec.outer), 1)}, null);

        Tuple t = dbMetaDataScan.get_next();
        while (t != null)
        {
            if (t.getStrFld(1).equals("relation:" + relName))
            {
                dbMetaDataScan.close();
                return true;
            }
            t = dbMetaDataScan.get_next();
        }
        dbMetaDataScan.close();
        return false;
    }

    private static void insertRelIntoDbMetaDataFile(String relName)
            throws
            HFException,
            HFBufMgrException,
            HFDiskMgrException,
            IOException,
            InvalidTypeException,
            InvalidTupleSizeException,
            FieldNumberOutOfBoundException,
            InvalidSlotNumberException,
            SpaceNotAvailableException
    {
        Heapfile dbMetaDataFile = new Heapfile(DB_METADATA_FILE_NAME);
        AttrType[] dbmetaDataAttrTypes = new AttrType[1];
        dbmetaDataAttrTypes[0] = new AttrType(AttrType.attrString);
        short[] stringLengths = new short[1];
        stringLengths[0] = MAX_STRING_LENGTH;
        Tuple dbMetaDataTuple = new Tuple();
        dbMetaDataTuple.setHdr((short) 1, dbmetaDataAttrTypes, stringLengths);
        dbMetaDataTuple.setStrFld(1, "relation:" + relName);
        dbMetaDataFile.insertRecord(dbMetaDataTuple.getTupleByteArray());
    }

    private static void insertIndexIntoDbMetaDataFile(String relName, int columnId, String indexType)
            throws
            HFException,
            HFBufMgrException,
            HFDiskMgrException,
            IOException,
            InvalidTypeException,
            InvalidTupleSizeException,
            FieldNumberOutOfBoundException,
            InvalidSlotNumberException,
            SpaceNotAvailableException
    {
        Heapfile dbMetaDataFile = new Heapfile(DB_METADATA_FILE_NAME);
        AttrType[] dbmetaDataAttrTypes = new AttrType[1];
        dbmetaDataAttrTypes[0] = new AttrType(AttrType.attrString);
        short[] stringLengths = new short[1];
        stringLengths[0] = MAX_STRING_LENGTH;
        Tuple dbMetaDataTuple = new Tuple();
        dbMetaDataTuple.setHdr((short) 1, dbmetaDataAttrTypes, stringLengths);
        dbMetaDataTuple.setStrFld(1, "index:" + relName + "." + columnId + "." + indexType);
        dbMetaDataFile.insertRecord(dbMetaDataTuple.getTupleByteArray());
    }

    private static void batchInsertDataIntoRel(BufferedReader br, String relName, short numAttributes)
            throws
            Exception
    {
        AttrType[] metadataAttrTypes = getRelationAttrTypes(relName);
        short[] stringLengths = TupleUtils.getStrFieldLengthsForConstantStrSizes(metadataAttrTypes);

        Tuple t = new Tuple();
        t.setHdr(numAttributes, metadataAttrTypes, stringLengths);

        Heapfile file = new Heapfile(getRelDataFileName(relName));
        String tupleValue;
        boolean endOfFile = false;

        List<String[]> indexInfos = getAllRelationIndexInfos(relName);

        while (true)
        {
            ArrayList<Vector100Dtype> vectorsInDataLine = new ArrayList<>();
            for (int i = 0; i < numAttributes; i++)
            {
                tupleValue = br.readLine();
                if (tupleValue == null)
                {
                    endOfFile = true;
                    break;
                }

                tupleValue = tupleValue.trim();
                AttrType type = metadataAttrTypes[i];

                switch (type.attrType)
                {
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
                        for (int j = 0; j < 100; j++)
                        {
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
            RID rid = file.insertRecord(t.getTupleByteArray());
            updateIndexesOnInsert(relName, indexInfos, t, rid);
        }
        JavabaseBM.flushAllPages();
        System.out.println("File " + getRelDataFileName(relName) + " created with " + file.getRecCnt() + " records.");
    }

    private static void updateIndexesOnInsert(String relName, List<String[]> indexInfos, Tuple t, RID rid) throws Exception {    
        for (String[] indexInfo : indexInfos) {
            int columnId = Integer.parseInt(indexInfo[0]);
            String indexType = indexInfo[1];
            if (indexType.equals(IndexType.BTREE.toString()))
            {
                BTreeFile bTreeFile = new BTreeFile(getBTreeFileName(relName, columnId));
                AttrType[] attrTypes = getRelationAttrTypes(relName);
                KeyClass key = null;

                switch (attrTypes[columnId - 1].attrType) 
                {
                    case AttrType.attrString:
                        key = new StringKey(t.getStrFld(columnId));
                        break;
                    case AttrType.attrInteger:
                        key = new IntegerKey(t.getIntFld(columnId));
                        break;
                    case AttrType.attrReal:
                        key = new RealKey(t.getFloFld(columnId));
                        break;
                    default:
                        throw new IOException("Unsupported attribute type for BTree index: " + attrTypes[columnId - 1].attrType);
                }
                bTreeFile.insert(key, rid);
                bTreeFile.close();
            }
            else if (indexType.equals(IndexType.LSHF.toString()))
            {
                LSHFIndex lshfIndex = new LSHFIndex(relName, columnId);
                Vector100Dtype vector = t.get100DVectFld(columnId);
                lshfIndex.insertRecord(vector, rid);
                // No lshfIndex.close() method in the current implementation
            }
        }
    }

    // returns a list of index info for the relation
    // example: [["1", "BTREE"], ["2", "LSHF"]]
    // where 1 is the column number and Btree is the index type
    private static List<String[]> getAllRelationIndexInfos(String relName) throws Exception
    {   
        List<String[]> indexInfo = new ArrayList<>();

        short[] stringLengths = new short[1];
        stringLengths[0] = MAX_STRING_LENGTH;
        FileScan dbMetaDataScan = new FileScan(DB_METADATA_FILE_NAME, new AttrType[]{new AttrType(AttrType.attrString)}, stringLengths, (short) 1, 1, new FldSpec[]{new FldSpec(new RelSpec(RelSpec.outer), 1)}, null);

        Tuple tuple = dbMetaDataScan.get_next();
        
        while (tuple != null)
        {
            if(tuple.getStrFld(1).startsWith("index:" + relName))
            {
                String indexString = tuple.getStrFld(1);
                String[] parts = indexString.split("\\.");
                indexInfo.add(new String[]{parts[1], parts[2]});
            }
            tuple = dbMetaDataScan.get_next();
        }
        dbMetaDataScan.close();
        return indexInfo;
    }

    private static void createDataFile(String relName)
            throws
            HFException,
            HFBufMgrException,
            HFDiskMgrException,
            IOException
    {
        new Heapfile(getRelDataFileName(relName));
    }

    private static void createRelMetaDataFile(String relName, short numAttributes, String[] attributeTypes)
            throws
            Exception
    {

        AttrType[] attrTypes = new AttrType[numAttributes];

        Tuple metaDataTuple = new Tuple();
        metaDataTuple.setHdr((short) 1, new AttrType[]{new AttrType(AttrType.attrInteger)}, null);

        Heapfile dataFileMetaData = new Heapfile(getRelMetaDataFileName(relName));
        for (int i = 0; i < numAttributes; i++)
        {
            int type = getMinibaseAttrTypeForInputAttrType(Integer.parseInt(attributeTypes[i].trim()));
            attrTypes[i] = new AttrType(type);
            metaDataTuple.setIntFld(1, type);
            dataFileMetaData.insertRecord(metaDataTuple.getTupleByteArray());
        }
    }

    private static int getMinibaseAttrTypeForInputAttrType(int inType) {
        return switch (inType) {
            case 1 -> 1; // integer
            case 2 -> 2; // real
            case 3 -> 0; // string
            case 4 -> 5; // 100D-vector.
            default -> throw new RuntimeException("Unknown attribute type " + inType);
        };
    }

    private static void createOrOpenDbMetaDataFile(String dbName)
            throws
            HFException,
            HFBufMgrException,
            HFDiskMgrException,
            IOException
    {
        new Heapfile(DB_METADATA_FILE_NAME);
    }

    public static String getBTreeFileName(String relName, int columnId)
    {
        return relName + "." + columnId;
    }

    public static String getRelDataFileName(String relName)
    {
        return relName + ".data";
    }

    private static String getDbPath(String dbName)
    {
        return "/tmp/" + System.getProperty("user.name") + "." + dbName + "-db";
    }

    public static String getRelMetaDataFileName(String relName)
    {
        return relName + ".metadata";
    }
}