package scripts;

import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

import bufmgr.PagePinnedException;
import diskmgr.Pcounter;
import global.*;
import heap.Heapfile;
import heap.Tuple;
import index.NNIndexScan;
import index.RSIndexScan;
import iterator.FileScan;
import iterator.FldSpec;
import iterator.Iterator;
import iterator.RelSpec;

import static global.SystemDefs.JavabaseBM;

public class Query
{
    private static String QUERY_RESULTS_HEAPFILE_NAME;
    public static int numAttributes;
    public static AttrType[] attrTypes;
    public static short[] strLengths;


    private static int[] outputFieldNumbers;
    private static Iterator scan;
    private static Heapfile queryResult;
    private static AttrType[] outputTupleAttrTypes;

    public static int numBuffersForSort;

    public static void main(String[] args) throws
                                           Exception
    {
        ScriptMetrics.setTimeStarted();

        if (args.length != 4)
        {
            System.err.println("query requires 4 arguments.");
            System.exit(1);
        }
        String db_name = args[0].trim();
        String query_specification_file_name = args[1].trim();
        String index_option = args[2].trim();
        int num_buffers = Integer.parseInt(args[3].trim());

//        Allocate 1/4th total buffers for sort
        numBuffersForSort = num_buffers / 4;
        restartDb(db_name, num_buffers);
        Pcounter.initialize();

        BufferedReader br = new BufferedReader(new FileReader(query_specification_file_name));
        String query_specification = br.readLine();
        if (query_specification == null)
        {
            System.err.println("query_specification is null.");
        }
        query_specification = query_specification.trim();

        if (query_specification.startsWith("Range("))
        {
            String specifications = query_specification.substring("Range(".length(), query_specification.length() - 1);
            String[] parameters = specifications.split(",");
            if (parameters.length < 3)
            {
                throw new IOException("query_specification requires at least 3 parameters.");
            }

            // Extract the parameters from the query specification.
            int vector_field_number = Integer.parseInt(parameters[0].trim());
            String target_vector_file_name = parameters[1].trim();
            int distance = Integer.parseInt(parameters[2].trim());
            outputFieldNumbers = new int[parameters.length - 3];
            for (int i = 3; i < parameters.length; i++)
            {
                outputFieldNumbers[i - 3] = Integer.parseInt(parameters[i].trim());
            }

            // Extract the target vector from the target vector file
            Vector100Dtype target_vector = read_target_vector(target_vector_file_name);

            // Print the query details
            System.out.println("Range Query Parsed:");
            System.out.println("QA: " + vector_field_number + ", D: " + distance + ", target vector: " + Arrays.toString(target_vector.vector));
            System.out.println("Output fields: " + Arrays.toString(outputFieldNumbers));

            FldSpec[] projList = new FldSpec[outputFieldNumbers.length];
            IntStream.range(0, outputFieldNumbers.length).forEach(i -> projList[i] = new FldSpec(new RelSpec(RelSpec.outer), outputFieldNumbers[i]));

            /*TODO: heapfile name is not unique. Need to use relation names
                    for phase 3
            */
            scan = new RSIndexScan(
//                    Choose to use LSHFIndex or not
                    index_option.equals("Y") ? new IndexType(IndexType.Lsh) : null, null, null, attrTypes, strLengths, numAttributes, outputFieldNumbers.length, projList, null, vector_field_number, target_vector, distance);
        }
        else if (query_specification.startsWith("NN("))
        {
            String specifications = query_specification.substring("NN(".length(), query_specification.length() - 1);
            String[] parameters = specifications.split(",");
            if (parameters.length < 3)
            {
                throw new IOException("query_specification requires at least 3 parameters.");
            }

            // Extract the parameters from the query specification.
            int vector_field_number = Integer.parseInt(parameters[0].trim());
            String target_vector_file_name = parameters[1].trim();
            int number_of_nearest_neighbors = Integer.parseInt(parameters[2].trim());
            outputFieldNumbers = new int[parameters.length - 3];
            for (int i = 3; i < parameters.length; i++)
            {
                outputFieldNumbers[i - 3] = Integer.parseInt(parameters[i].trim());
            }

            // Extract the target vector from the target vector file
            Vector100Dtype target_vector = read_target_vector(target_vector_file_name);

            // Print the query details
            System.out.println("NN Query Parsed:");
            System.out.println("QA (vectorFieldNumber): " + vector_field_number + ", K: " + number_of_nearest_neighbors + ", target vector: " + Arrays.toString(target_vector.vector));
            System.out.println("Output fields: " + Arrays.toString(outputFieldNumbers));

            FldSpec[] projList = new FldSpec[outputFieldNumbers.length];
            IntStream.range(0, outputFieldNumbers.length).forEach(i -> projList[i] = new FldSpec(new RelSpec(RelSpec.outer), outputFieldNumbers[i]));

            /*TODO: heapfile name is not unique. Need to use relation names
                    for phase 3
            */
            scan = new NNIndexScan(
//                    Choose to use LSHFIndex or not
                    index_option.equals("Y") ? new IndexType(IndexType.Lsh) : null, null, null, attrTypes, strLengths, numAttributes, outputFieldNumbers.length, projList, null, vector_field_number, target_vector, number_of_nearest_neighbors);
        }
        else
        {
            throw new RuntimeException("query_specification is not a valid query_specification.");
        }

        System.out.println("\n ---Output Tuples---");
        try
        {
            ScriptMetrics.setTimeDataSortStart();
//        Iterate over scan
            prepareOutputTupleAttrTypes();

            Tuple t = scan.get_next();
            while (t != null)
            {
                printOutputTuple(t);
                t = scan.get_next();
                ScriptMetrics.incrementNumberOfTuplesReturned();
            }
            scan.close();
            ScriptMetrics.setTimeDataSortEnd();
        }
        finally
        {
            try
            {
                JavabaseBM.flushAllPages();
            }
            catch (PagePinnedException ignored)
            {
            }
        }
        System.out.println("\n ---End Output---");

        Pcounter.printPcounter();
        ScriptMetrics.setTimeEnded();
        ScriptMetrics.printMetricsReport();
    }

    private static void printOutputTuple(Tuple outTuple) throws
                                                         Exception
    {
        System.out.println();

        for (int i = 0; i < outputTupleAttrTypes.length; i++)
        {
            switch (outputTupleAttrTypes[i].attrType)
            {
                case AttrType.attrInteger:
                    System.out.println("" + outTuple.getIntFld(i + 1));
                    break;
                case AttrType.attrString:
                    System.out.println(outTuple.getStrFld(i + 1));
                    break;
                case AttrType.attrReal:
                    System.out.println("" + outTuple.getFloFld(i + 1));
                    break;
                case AttrType.attrVector100D:
                    System.out.println(outTuple.get100DVectFld(i + 1));
                    break;
            }
        }
    }

    private static void prepareOutputTupleAttrTypes()
    {
        ArrayList<AttrType> attrTypesList = new ArrayList<>();
        Arrays.stream(outputFieldNumbers).forEach(outNum -> attrTypesList.add(attrTypes[outNum - 1]));
        outputTupleAttrTypes = attrTypesList.toArray(new AttrType[0]);
    }

    public static Vector100Dtype read_target_vector(String target_vector_file_name) throws
                                                                                    Exception
    {
        short[] vector = new short[100];
        Vector100Dtype target_vector;
        BufferedReader br = new BufferedReader(new FileReader(target_vector_file_name));

        String line = br.readLine();
        if (line == null)
        {
            throw new IOException("Target vector file " + target_vector_file_name + " is empty.");
        }

        String[] target_vector_strings = line.trim().split("\\s+");
        if (target_vector_strings.length != 100)
        {
            throw new IOException("Target vector file " + target_vector_file_name + " must contain exactly 100 integers");
        }
        for (int i = 0; i < 100; i++)
        {
            vector[i] = Short.parseShort(target_vector_strings[i]);
        }
        target_vector = new Vector100Dtype(vector);
        return target_vector;
    }

    public static void restartDb(String dbName, int numBuf) throws
                                                            Exception
    {
//        Restart Minibase
        SystemDefs.MINIBASE_RESTART_FLAG = true;
        new SystemDefs(BatchInsert.getDbFileSystemPath(dbName), BatchInsert.DB_SIZE_IN_PAGES, numBuf, "Clock");

//        Parse Db Meta data
        FileScan dbMetadataScan = new FileScan(BatchInsert.DB_DATA_METADATA_HEAP_FILE_NAME, new AttrType[]{new AttrType(AttrType.attrInteger)}, null, (short) 1, 1, new FldSpec[]{new FldSpec(new RelSpec(RelSpec.outer), 1)}, null);

        ArrayList<Integer> metadataAttrTypes = new ArrayList<>();
        Tuple t = dbMetadataScan.get_next();
        while (t != null)
        {
            metadataAttrTypes.add(t.getIntFld(1));
            t = dbMetadataScan.get_next();
        }
        dbMetadataScan.close();

        ArrayList<AttrType> attrTypesList = new ArrayList<>();
        int strAttributeCounts = 0;
        for (Integer type : metadataAttrTypes)
        {
            attrTypesList.add(new AttrType(type));
            if (type == AttrType.attrString) strAttributeCounts++;
        }

        numAttributes = metadataAttrTypes.size();
        attrTypes = attrTypesList.toArray(new AttrType[0]);
        strLengths = new short[strAttributeCounts];
        IntStream.range(0, strAttributeCounts).forEach(i -> strLengths[i] = BatchInsert.MAX_STRING_LENGTH);
    }

    private static Heapfile openDeleteAndOpenHeapFile(String fileName) throws
                                                                       Exception
    {
        Heapfile hf = new Heapfile(fileName);
        hf.deleteFile();
        return new Heapfile(fileName);
    }

    public static void queryHandler(String dbName, String querySpecificationFileName, String numBuf, String relName) throws
                                                                                                     Exception
    {
        ScriptMetrics.setTimeStarted();

        String db_name = dbName;
        String query_specification_file_name = querySpecificationFileName;

        int num_buffers = Integer.parseInt(numBuf);

//        Allocate 1/4th total buffers for sort
        numBuffersForSort = num_buffers / 4;
        restartDb(db_name, num_buffers);
        Pcounter.initialize();

        BufferedReader br = new BufferedReader(new FileReader(query_specification_file_name));
        String query_specification = br.readLine();
        if (query_specification == null)
        {
            System.err.println("query_specification is null.");
        }
        query_specification = query_specification.trim();

        if (query_specification.startsWith("Range("))
        {
            String specifications = query_specification.substring("Range(".length(), query_specification.length() - 1);
            String[] parameters = specifications.split(",");

            // Extract the parameters from the query specification.
            int vector_field_number = Integer.parseInt(parameters[0].trim());
            String target_vector_file_name = parameters[1].trim();
            int distance = Integer.parseInt(parameters[2].trim());
            String indexOption = parameters[3].trim();

            outputFieldNumbers = new int[parameters.length - 4];
            for (int i = 4; i < parameters.length; i++)
            {
                outputFieldNumbers[i - 4] = Integer.parseInt(parameters[i].trim());
            }

            // Extract the target vector from the target vector file
            Vector100Dtype target_vector = read_target_vector(target_vector_file_name);

            // Print the query details
            System.out.println("Range Query Parsed:");
            System.out.println("QA: " + vector_field_number + ", D: " + distance + ", target vector: " + Arrays.toString(target_vector.vector));
            System.out.println("Output fields: " + Arrays.toString(outputFieldNumbers));

            FldSpec[] projList = new FldSpec[outputFieldNumbers.length];
            IntStream.range(0, outputFieldNumbers.length).forEach(i -> projList[i] = new FldSpec(new RelSpec(RelSpec.outer), outputFieldNumbers[i]));

            /*TODO Vikram:
                    1. heapfile name is not unique. Need to use relation names
                        for phase 3
                    2. Need to ensure that the index scan below is accessing the correct index
            */
            // Setup name for QUERY_RESULTS_HEAPFILE
            QUERY_RESULTS_HEAPFILE_NAME = "Range"+relName+parameters[0].trim()+target_vector_file_name;
            queryResult = openDeleteAndOpenHeapFile(QUERY_RESULTS_HEAPFILE_NAME);
            scan = new RSIndexScan(
//                    Choose to use LSHFIndex or not
                    indexOption.equals("Y") ? new IndexType(IndexType.Lsh) : null, relName, null, attrTypes, strLengths, numAttributes, outputFieldNumbers.length, projList, null, vector_field_number, target_vector, distance);
        }
        else if (query_specification.startsWith("NN("))
        {
            String specifications = query_specification.substring("NN(".length(), query_specification.length() - 1);
            String[] parameters = specifications.split(",");

            // Extract the parameters from the query specification.
            int vector_field_number = Integer.parseInt(parameters[0].trim());
            String target_vector_file_name = parameters[1].trim();
            int number_of_nearest_neighbors = Integer.parseInt(parameters[2].trim());
            String indexOption = parameters[3].trim();
            outputFieldNumbers = new int[parameters.length - 4];
            for (int i = 4; i < parameters.length; i++)
            {
                outputFieldNumbers[i - 4] = Integer.parseInt(parameters[i].trim());
            }

            // Extract the target vector from the target vector file
            Vector100Dtype target_vector = read_target_vector(target_vector_file_name);

            // Print the query details
            System.out.println("NN Query Parsed:");
            System.out.println("QA (vectorFieldNumber): " + vector_field_number + ", K: " + number_of_nearest_neighbors + ", target vector: " + Arrays.toString(target_vector.vector));
            System.out.println("Output fields: " + Arrays.toString(outputFieldNumbers));

            FldSpec[] projList = new FldSpec[outputFieldNumbers.length];
            IntStream.range(0, outputFieldNumbers.length).forEach(i -> projList[i] = new FldSpec(new RelSpec(RelSpec.outer), outputFieldNumbers[i]));

             /*TODO Vikram:
                    1. heapfile name is not unique. Need to use relation names
                        for phase 3
                    2. Need to ensure that the index scan below is accessing the correct index
            */
            // Setup name for QUERY_RESULTS_HEAPFILE
            QUERY_RESULTS_HEAPFILE_NAME = "NN"+relName+parameters[0].trim()+target_vector_file_name;
            queryResult = openDeleteAndOpenHeapFile(QUERY_RESULTS_HEAPFILE_NAME);
            scan = new NNIndexScan(
//                    Choose to use LSHFIndex or not
                    indexOption.equals("Y") ? new IndexType(IndexType.Lsh) : null, relName, null, attrTypes, strLengths, numAttributes, outputFieldNumbers.length, projList, null, vector_field_number, target_vector, number_of_nearest_neighbors);
        }
        else if (query_specification.startsWith("Sort("))
        {
            String specifications = query_specification.substring("Sort(".length(), query_specification.length() - 1);
            String[] parameters = specifications.split(",");

            // Extract the parameters from the query specification.
            int vector_field_number = Integer.parseInt(parameters[0].trim());
            String target_vector_file_name = parameters[1].trim();
            int distance = Integer.parseInt(parameters[2].trim());
            String indexOption = "N";

            outputFieldNumbers = new int[parameters.length - 3];
            for (int i = 3; i < parameters.length; i++)
            {
                outputFieldNumbers[i - 3] = Integer.parseInt(parameters[i].trim());
            }

            // Extract the target vector from the target vector file
            Vector100Dtype target_vector = read_target_vector(target_vector_file_name);

            // Print the query details
            System.out.println("Range Query Parsed:");
            System.out.println("QA: " + vector_field_number + ", D: " + distance + ", target vector: " + Arrays.toString(target_vector.vector));
            System.out.println("Output fields: " + Arrays.toString(outputFieldNumbers));

            FldSpec[] projList = new FldSpec[outputFieldNumbers.length];
            IntStream.range(0, outputFieldNumbers.length).forEach(i -> projList[i] = new FldSpec(new RelSpec(RelSpec.outer), outputFieldNumbers[i]));

            /*TODO Vikram:
                    1. heapfile name is not unique. Need to use relation names
                        for phase 3
                    2. Need to ensure that the index scan below is accessing the correct index
            */
            // Setup name for QUERY_RESULTS_HEAPFILE
            QUERY_RESULTS_HEAPFILE_NAME = "Sort"+relName+parameters[0].trim()+target_vector_file_name;
            queryResult = openDeleteAndOpenHeapFile(QUERY_RESULTS_HEAPFILE_NAME);
            scan = new RSIndexScan(null, relName, relName, attrTypes, strLengths, numAttributes, outputFieldNumbers.length, projList, null, vector_field_number, target_vector, distance);
        }
        else if (query_specification.startsWith("Filter("))
        {
            // need to check specifications for implementation.
        }
        else
        {
            throw new RuntimeException("query_specification is not a valid query_specification.");
        }

        System.out.println("\n ---Output Tuples---");
        try
        {
            ScriptMetrics.setTimeDataSortStart();
//        Iterate over scan
            prepareOutputTupleAttrTypes();

            Tuple t = scan.get_next();
            while (t != null)
            {
                printOutputTuple(t);
                // Saving query results in the queryResult heapfile to handle joins
                queryResult.insertRecord(t.getTupleByteArray());
                t = scan.get_next();
                ScriptMetrics.incrementNumberOfTuplesReturned();
            }
            scan.close();
            ScriptMetrics.setTimeDataSortEnd();
        }
        finally
        {
            try
            {
                JavabaseBM.flushAllPages();
            }
            catch (PagePinnedException ignored)
            {
            }
        }
        System.out.println("\n ---End Output---");

        Pcounter.printPcounter();
        ScriptMetrics.setTimeEnded();
        ScriptMetrics.printMetricsReport();
    }
}
