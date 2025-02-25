package tests;

import global.*;
import heap.*;

import java.io.*;

public class TestMultipleVectorTuples {
    public static void main(String[] args) {
        try {
            SystemDefs sysdef = new SystemDefs("minibaseDB", 1000, 100, "Clock");

            AttrType[] attrTypes = new AttrType[1];
            attrTypes[0] = new AttrType(AttrType.attrVector100D);

            Heapfile heapfile = new Heapfile("vectorHeapFile");

            for (int i = 0; i < 5; i++) {
                Vector100Dtype v = new Vector100Dtype();
                for (int j = 0; j < 100; j++) {
                    v.vector[j] = (short) ((i + 1) * j);  // Unique values per tuple
                }

                Tuple tuple = new Tuple();
                tuple.setHdr((short) 1, attrTypes, null);
                tuple.set100DVectFld(1, v);

                heapfile.insertRecord(tuple.getTupleByteArray());
                System.out.println("Inserted Tuple " + (i + 1) + " with Vector100Dtype.");
            }

            Scan scan = new Scan(heapfile);
            Tuple retrievedTuple;
            int count = 1;

            while ((retrievedTuple = scan.getNext(new RID())) != null) {
                retrievedTuple.setHdr((short) 1, attrTypes, null);
                Vector100Dtype v = retrievedTuple.get100DVectFld(1);
                System.out.println("Retrieved Tuple " + count + " | Magnitude: " + v.get_magnitude());
                count++;
            }

            scan.closescan();

            // Shutdown MiniBase
            SystemDefs.JavabaseBM.flushAllPages();
            SystemDefs.JavabaseDB.closeDB();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
