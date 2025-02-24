package tests;

import global.*;
import heap.*;

import java.io.*;

public class TestVector100D {
    public static void main(String[] args) {
        try {
            //Step 1: Create a 100D vector and assign values
            Vector100Dtype v1 = new Vector100Dtype();
            Vector100Dtype v2 = new Vector100Dtype();

            for (int i = 0; i < 100; i++) {
                v1.vector[i] = (short) (i * 2);  // Example: 0, 2, 4, ..., 198
                v2.vector[i] = (short) (i * 3);  // Example: 0, 3, 6, ..., 297
            }

            //Step 2: Compute vector magnitudes
            System.out.println("Magnitude of v1: " + v1.get_magnitude());
            System.out.println("Magnitude of v2: " + v2.get_magnitude());

            //Step 3: Compute distance between vectors
            int distance = v1.get_distance(v2);
            System.out.println("Distance between v1 and v2: " + distance);

            //Step 4: Insert vector into a tuple
            AttrType[] attrTypes = new AttrType[1];
            attrTypes[0] = new AttrType(AttrType.attrVector100D);

            Tuple tuple = new Tuple();
            tuple.setHdr((short) 1, attrTypes, null);
            tuple.set100DVectFld(1, v1);

            //Step 5: Store tuple in a HeapFile
            SystemDefs sysdef = new SystemDefs("minibaseDB", 1000, 100, "Clock");
            Heapfile heapfile = new Heapfile("testVectorHeapFile");
            RID rid = heapfile.insertRecord(tuple.getTupleByteArray());
            System.out.println("Tuple with Vector100D inserted successfully!");

            //Step 6: Retrieve tuple and verify
            Scan scan = new Scan(heapfile);
            Tuple retrievedTuple = scan.getNext(new RID());

            if (retrievedTuple != null) {
                retrievedTuple.setHdr((short) 1, attrTypes, null);
                Vector100Dtype retrievedVector = retrievedTuple.get100DVectFld(1);
                System.out.println("Retrieved Vector Magnitude: " + retrievedVector.get_magnitude());
            }

            scan.closescan();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
