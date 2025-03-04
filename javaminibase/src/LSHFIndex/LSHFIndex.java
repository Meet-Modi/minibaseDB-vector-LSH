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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LSHF index.
 * LSHF index is a map of <Layer Number, Layer Map>
 * Each Layer map is a map of <int[] hash, heapfile>
 * Each Layer is a tree. Bunch of trees is LSHForest.
 * Each Layer = h1h2h3...hn where : hn = number of hashes per layer.
 */
public class LSHFIndex
{
    private HashKey hashKey;

    // LSHF parameters.
    private int num_layers;

    // Layers parameters
    private Layer[] layers;
    private int bin_length;
    private int num_hash_functions_per_layer;

    private Map< Integer, Map<Integer[], Heapfile> > heapFiles;
    private Map< Integer, Map<Integer[], Heapfile> > layer_maps;


    public LSHFIndex(int L, int x)
    {
        this.hashKey = new HashKey(L, x);
        this.heapFiles = new HashMap<>();
    }

    public void insertRecord(Vector100Dtype vector, RID rid) throws IOException,
                                                                    ConstructPageException,
                                                                    InsertRecException,
                                                                    HFException,
                                                                    HFBufMgrException,
                                                                    HFDiskMgrException,
                                                                    Exception
    {
        int[] hashValues = hashKey.generateHashValues(vector);
        for (int layer = 0; layer < hashValues.length; layer++)
        {
            int hashValue = hashValues[layer];
            Map<Integer, Heapfile> layerMap = heapFiles.get(layer);
            if (layerMap == null)
            {
                layerMap = new HashMap<>();
                heapFiles.put(layer, layerMap);
            }
            Heapfile heapFile = layerMap.get(hashValue);
            if (heapFile == null)
            {
                String heapFileName = "layer-" + layer + "-bin-" + hashValue;
                heapFile = new Heapfile(heapFileName); // Create a new heap file with a unique name
                layerMap.put(hashValue, heapFile);
            }
            insertRIDIntoHeapfile(heapFile, rid);
        }
    }

    private void insertRIDIntoHeapfile(Heapfile heapFile, RID rid) throws IOException, InsertRecException, Exception
    {
        Tuple tuple = new Tuple();
        tuple.setHdr((short) 2,
                new AttrType[]{new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger)}, null);
        tuple.setIntFld(1, rid.pageNo.pid);
        tuple.setIntFld(2, rid.slotNo);
        heapFile.insertRecord(tuple.returnTupleByteArray());
    }

    public List<String> getBins(Vector100Dtype vector) throws IOException, ConstructPageException
    {
        int[] hashValues = hashKey.generateHashValues(vector);
        List<String> binNames = new ArrayList<>();
        for (int layer = 0; layer < hashValues.length; layer++)
        {
            int hashValue = hashValues[layer];
            Map<Integer, Heapfile> layerMap = heapFiles.get(layer);
            if (layerMap != null)
            {
                Heapfile heapFile = layerMap.get(hashValue);
                if (heapFile != null)
                {
                    String heapFileName = "layer-" + layer + "-bin-" + hashValue;
                    binNames.add(heapFileName);
                }
            }
        }
        return binNames;
    }

    public static void main(String[] args)
    {
        try
        {
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
            for (int i = 0; i < 100; i++)
            {
                vector.vector[i] = (short) (i + 1);
            }

            // Insert the vector into the LSHFIndex
            RID rid = new RID(new PageId(1), 1);
            lshfIndex.insertRecord(vector, rid);

            // Get the bin names for the vector
            List<String> binNames = lshfIndex.getBins(vector);
            System.out.println("Bin names: " + binNames);

            // Clean up
            SystemDefs.JavabaseDB.closeDB();

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}