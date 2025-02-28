package LSHFIndex;

import global.AttrType;
import global.PageId;
import global.RID;
import global.SystemDefs;
import global.Vector100Dtype;
import heap.HFBufMgrException;
import heap.HFDiskMgrException;
import heap.HFException;
import heap.Heapfile;
import heap.InvalidTypeException;
import heap.Tuple;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LSHFIndex {
    private HashKey hashKey;
    private Map<Integer, Heapfile> heapFiles;

    public LSHFIndex(int L, int x) {
        this.hashKey = new HashKey(L, x);
        this.heapFiles = new HashMap<>();
    }

    public void insertRecord(Vector100Dtype vector, RID rid) throws IOException,
            ConstructPageException,
            InsertRecException,
            HFException,
            HFBufMgrException,
            HFDiskMgrException,
            Exception {
        int[] hashValues = hashKey.generateHashValues(vector);
        for (int i = 0; i < hashValues.length; i++) {
            int hashValue = hashValues[i];
            Heapfile heapFile = heapFiles.get(hashValue);
            if (heapFile == null) {
                heapFile = new Heapfile(null); // Create a new heap file with a unique name
                heapFiles.put(hashValue, heapFile);
            }
            insertRIDIntoHeapfile(heapFile, rid);
        }
    }

    private void insertRIDIntoHeapfile(Heapfile heapFile, RID rid) throws IOException, InsertRecException, Exception {
        Tuple tuple = new Tuple();
        tuple.setHdr((short) 2,
                new AttrType[] { new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger) }, null);
        tuple.setIntFld(1, rid.pageNo.pid);
        tuple.setIntFld(2, rid.slotNo);
        heapFile.insertRecord(tuple.returnTupleByteArray());
    }

    public static void main(String[] args) {
        try {
            // Initialize the system
            String dbpath = "/tmp/minibase.lshfindex.test";
            String logpath = "/tmp/minibase.lshfindex.log";
            SystemDefs sysdef = new SystemDefs(dbpath, 1000, 100, "Clock");

            // Create a new LSHFIndex
            int L = 5; // Number of hash functions (layers)
            int x = 10; // Number of bins
            LSHFIndex lshfIndex = new LSHFIndex(L, x);

            // Create a sample vector
            Vector100Dtype vector = new Vector100Dtype();
            for (int i = 0; i < 100; i++) {
                vector.vector[i] = (short) (i + 1);
            }

            // Insert the vector into the LSHFIndex
            RID rid = new RID(new PageId(1), 1);
            lshfIndex.insertRecord(vector, rid);

            // Clean up
            SystemDefs.JavabaseDB.closeDB();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}