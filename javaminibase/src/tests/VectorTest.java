package tests;

import java.io.*;

import global.*;
import bufmgr.*;
import diskmgr.*;
import heap.*;
import iterator.*;
import index.*;
import LSHFIndex.*;

import java.util.Random;

class VectorDriver extends TestDriver implements GlobalConst
{
    private static Vector100Dtype vectorData1[] ;
    private static int NUM_VECTOR_RECORDS = 10;
    private static short VECTOR_REC_LEN1 = 200;

    private static int SORTPGNUM = 12;

    public VectorDriver()
    {
        super("vectortest");
    }

    public boolean runTests()
    {

        System.out.println("\n" + "Running " + testName() + " tests...." + "\n");

        SystemDefs sysdef = new SystemDefs(dbpath, 300, NUMBUF, "Clock");

        // Kill anything that might be hanging around
        String newdbpath;
        String newlogpath;
        String remove_logcmd;
        String remove_dbcmd;
        String remove_cmd = "/bin/rm -rf ";

        newdbpath = dbpath;
        newlogpath = logpath;

        remove_logcmd = remove_cmd + logpath;
        remove_dbcmd = remove_cmd + dbpath;

        // Commands here is very machine dependent.  We assume
        // user are on UNIX system here
        try
        {
            Runtime.getRuntime().exec(remove_logcmd);
            Runtime.getRuntime().exec(remove_dbcmd);
        }
        catch (IOException e)
        {
            System.err.println("" + e);
        }

        remove_logcmd = remove_cmd + newlogpath;
        remove_dbcmd = remove_cmd + newdbpath;

        //This step seems redundant for me.  But it's in the original
        //C++ code.  So I am keeping it as of now, just in case I
        //I missed something
        try
        {
            Runtime.getRuntime().exec(remove_logcmd);
            Runtime.getRuntime().exec(remove_dbcmd);
        }
        catch (IOException e)
        {
            System.err.println("" + e);
        }

        //Run the tests. Return type different from C++
        boolean _pass = runAllTests();

        //Clean up again
        try
        {
            Runtime.getRuntime().exec(remove_logcmd);
            Runtime.getRuntime().exec(remove_dbcmd);
        }
        catch (IOException e)
        {
            System.err.println("" + e);
        }

        System.out.println("\n" + "..." + testName() + " tests ");
        System.out.println(_pass == OK ? "completely successfully" : "failed");
        System.out.println(".\n\n");

        return _pass;
    }

    protected boolean test1()
    {

        System.out.println("------------------------ TEST 1 --------------------------");

        boolean status = OK;
        vectorData1 = generate_vector_data(10);
        AttrType[] attrType = new AttrType[1];
        attrType[0] = new AttrType(AttrType.attrVector100D);
        short[] attrSize = new short[1];
        attrSize[0] = VECTOR_REC_LEN1;

        TupleOrder[] order = new TupleOrder[1];
        order[0] = new TupleOrder(TupleOrder.Ascending);

        // Create target vector [1,1,1]
        Vector100Dtype target = new Vector100Dtype();
        target.vector[0] = 0;
        target.vector[1] = 0;
        target.vector[2] = 0;

        // k nearest neighbors.
        int k = 3;

        // create target_tuple for comparisions.
        Tuple target_tuple = new Tuple();
        try
        {
            target_tuple.setHdr((short) 1, attrType, attrSize);
        }
        catch (Exception e)
        {
            status = FAIL;
            e.printStackTrace();
        }

        // set field 1 to target
        try
        {
            target_tuple.set100DVectFld(1, target);
        }
        catch (Exception e)
        {
            status = FAIL;
            e.printStackTrace();
        }

        // create a tuple of appropriate size
        Tuple t = new Tuple();
        try
        {
            t.setHdr((short) 1, attrType, attrSize);
        }
        catch (Exception e)
        {
            status = FAIL;
            e.printStackTrace();
        }

        int size = t.size();

        // Create unsorted data file "test1.in"
        RID rid;
        Heapfile f = null;
        try
        {
            f = new Heapfile("test1.in");
        }
        catch (Exception e)
        {
            status = FAIL;
            e.printStackTrace();
        }

        t = new Tuple(size);
        try
        {
            t.setHdr((short) 1, attrType, attrSize);
        }
        catch (Exception e)
        {
            status = FAIL;
            e.printStackTrace();
        }

        for (int i = NUM_VECTOR_RECORDS-1; i >= 0; i--)
        {
            try
            {
                t.set100DVectFld(1, vectorData1[i]);
            }
            catch (Exception e)
            {
                status = FAIL;
                e.printStackTrace();
            }

            try
            {
                rid = f.insertRecord(t.returnTupleByteArray());
            }
            catch (Exception e)
            {
                status = FAIL;
                e.printStackTrace();
            }
            try
            {
                System.out.println("inserted:"+t.get100DVectFld(1).vector[0]);
            }
            catch (Exception e)
            {
                status = FAIL;
                e.printStackTrace();
            }
        }
        System.err.println("Test1 -- Vector Insert OK");

        // create an iterator by open a file scan
        FldSpec[] projlist = new FldSpec[1];
        RelSpec rel = new RelSpec(RelSpec.outer);
        projlist[0] = new FldSpec(rel, 1);


        FileScan fscan = null;

        try
        {
            fscan = new FileScan("test1.in", attrType, attrSize, (short) 1, 1, projlist, null);
        }
        catch (Exception e)
        {
            status = FAIL;
            e.printStackTrace();
        }

        System.out.println("\nTest1 -- fscan OK");
        // Sort "test1.in"
        Sort sort = null;
        try
        {
            sort = new Sort(attrType, (short) 1, attrSize, fscan, 1, order[0], VECTOR_REC_LEN1, SORTPGNUM, target, k);
        }
        catch (Exception e)
        {
            status = FAIL;
            e.printStackTrace();
        }
        System.out.println("\nTest1 -- sort OK");

        int count = 0;
        t = null;
        int outval = -10;

        try
        {
            t = sort.get_next();
        }

        catch (Exception e)
        {
            status = FAIL;
            e.printStackTrace();
        }
        System.out.println("\nTest1 -- sort get_next OK");
        boolean flag = true;

        while (t != null)
        {
            if (count >= NUM_VECTOR_RECORDS)
            {
                System.err.println("Test1 -- OOPS! too many records");
                status = FAIL;
                flag = false;
                break;
            }
            try
            {
                AttrType comp_type = new AttrType(AttrType.attrVector100D);
                outval = TupleUtils.CompareTupleWithTuple((AttrType) comp_type, target_tuple, 1, t, 1);
                short retrieved_vector_val = t.get100DVectFld(1).vector[0];
                short target_vector_val = target_tuple.get100DVectFld(1).vector[1];
                System.out.println("Taget:"+target_vector_val+" Retrived vector:"+retrieved_vector_val+" vector distance:" + outval);
            }
            catch (Exception e)
            {
                status = FAIL;
                e.printStackTrace();
            }

            count++;

            try
            {
                t = sort.get_next();
            }
            catch (Exception e)
            {
                status = FAIL;
                e.printStackTrace();
            }
        }

//        if (count < NUM_RECORDS)
//        {
//            System.err.println("Test1 -- OOPS! too few records");
//            status = FAIL;
//        }
//        else if (flag && status)
//        {
//            System.err.println("Test1 -- Sorting OK");
//        }
//
//        // clean up
//        try
//        {
//            sort.close();
//        }
//        catch (Exception e)
//        {
//            status = FAIL;
//            e.printStackTrace();
//        }
//
//        System.err.println("------------------- TEST 1 completed ---------------------\n");
//
//        return status;
        return true;
    }

    @Override
    protected boolean test2()
    {
        // Step 1: Generate input text file to read 10,000 random vectors from.

        // Step 2: Specify the number of hash functions per layer (n). This need to read query structure from project document.

        // Step 3: Specify the number of layers (L). This need to read query structure from project document.

        // Step 4: instantiate the Hasher.                   ( h_1,1 h_1,2 h_1,3 h_1,4 h_1,5,
        //                                                     h_2,1 h_2,2 h_2,3 h_2,4 h_2,5,
        //                                                     h_3,1 h_3,2 h_3,3 h_3,4 h_3,5 )

        // Step 5: The hasher is an abstraction that generates the hash values above. ^^^^^^^^^^
        //         using this hasher, insert all 10,000 random vectors into respective heap files
        //         with filenames given by matrix below in next step.
        //
        //         HERE WE ARE READY TO TEST INCOMING QUERIES
        // Step 6: For incoming query, access the corresponding 3 heap file with names ( h_11 : h_12 : h_13 : h_14 : h_15,
        //                                                                               h_21 : h_22 : h_23 : h_24 : h_25,
        //                                                                               h_31 : h_32 : h_33 : h_34 : h_35 )

        // Step 7: compute union of all tuples from all 3 files into a temp heapfile
        // Step 8: run Sort() on the temp heapfile k times for k-NN search. if k = 0, return all results.
        //         need to figure out details about the specifics from the project description.

        return true;
    }

    protected boolean test3()
    {
        return true;
    }

    protected boolean test4()
    {
        return true;
    }

    protected boolean test5()
    {
        return true;
    }

    protected boolean test6()
    {
        return true;
    }

    protected String testName()
    {
        return "Vector";
    }

    protected  Vector100Dtype[] generate_vector_data(int count)
    {
        Vector100Dtype array[] = new Vector100Dtype[count];
        for (int i = 0; i < count; i++)
        {
            array[i] = new Vector100Dtype();
            for (int j = 0; j < 3; j++)
            {
                array[i].vector[j] = (short) (i+1);
            }
        }
        return array;
    }

}
public class VectorTest
{
    public static void main(String argv[])
    {
        boolean vectorstatus;

        VectorDriver driver = new VectorDriver();

        vectorstatus = driver.runTests();
        if (vectorstatus != true)
        {
            System.out.println("Error ocurred during vector tests");
        }
        else
        {
            System.out.println("Vector tests completed successfully");
        }
    }
}
