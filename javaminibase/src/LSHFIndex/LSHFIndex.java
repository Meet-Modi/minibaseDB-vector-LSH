package LSHFIndex;

import global.AttrType;
import global.RID;
import global.Vector100Dtype;
import heap.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import btree.IndexInsertRecException;
import btree.IndexSearchException;
import btree.InsertException;
import btree.IteratorException;
import btree.KeyTooLongException;
import btree.LeafDeleteException;
import btree.LeafInsertRecException;
import btree.PinPageException;
import btree.UnpinPageException;

/**
 * LSHF index.
 * LSHF index is a map of <Layer Number, Layer Map>
 * Each Layer map is a map of <int[] hash, heapfile>
 * Each Layer is a tree. Bunch of trees is LSHForest.
 * Each Layer = h1h2h3...hn where : hn = number of hashes per layer.
 */
public class LSHFIndex
{

    private int numLayers;
    private int noOfHashFunctionsPerLayer;


    private Layer[] layers; // L
    private int binLength; // k

    public Heapfile LayerState;
    public Heapfile LayerMetaData;
    private String layerState =  "LayerState";
    private String layerMetaData = "LayerMetaData";

    /**
     * Create LSHF Index class
     *
     * @param Layers No of layers. Input parameter.
     * @param binLength bin length
     * @param hashFunctionsPerLayer No of hash functions per layer. Input parameter.
     */
    public LSHFIndex(int Layers, int binLength, int hashFunctionsPerLayer) throws HFDiskMgrException,
                                                                                  HFException,
                                                                                  HFBufMgrException,
                                                                                  IOException,
                                                                                  SpaceNotAvailableException,
                                                                                  FieldNumberOutOfBoundException,
                                                                                  InvalidTupleSizeException,
                                                                                  InvalidSlotNumberException,
                                                                                  InvalidTypeException

    {
        // Meta Data
        this.numLayers = Layers;
        this.binLength = binLength;
        this.noOfHashFunctionsPerLayer = hashFunctionsPerLayer;

        this.layers = new Layer[numLayers];

        // Initialize layers
        for (int i = 0; i < numLayers; i++)
        {
            layers[i] = new Layer(noOfHashFunctionsPerLayer, binLength);
        }

        // Store Layer States to disk

        LayerState = new Heapfile(layerState);

        // States to store for each Layer.
        // int: LayerNumber, int: HashNumber, 100DVector: HashRandom_Vector, int: HashShift
        Tuple temp = new Tuple();

        short numFlds = 4;
        AttrType[] attrTypes = new AttrType[numFlds];
        short[] strsizes = new short[numFlds];


        // Define Attr types.
        attrTypes[0] = new AttrType(AttrType.attrInteger);
        attrTypes[1] = new AttrType(AttrType.attrInteger);
        attrTypes[2] = new AttrType(AttrType.attrVector100D);
        attrTypes[3] = new AttrType(AttrType.attrInteger);

        // Set tuple header.
        temp.setHdr(numFlds, attrTypes, strsizes);

        for(int i = 0; i< numLayers; i++)
        {
            // Get this layer's proj vectors and hashShifts.
            Vector100Dtype[] projVectors = layers[i].getProjectionVectors();
            int[] shifts = layers[i].getHashShifts();

            // Iterate over all the hashfunctions in the layer.
            for(int j=0; j< noOfHashFunctionsPerLayer; j++)
            {
                temp.setIntFld(1,i);
                temp.setIntFld(2,j);
                temp.set100DVectFld(3, projVectors[j]);
                temp.setIntFld(4, shifts[j]);

                // Insert tuple into heapfile.
                LayerState.insertRecord(temp.getTupleByteArray());
            }
        }

        // Now we store layer meta data to another heap file layerMetaData
        // No. Layers, No. Hashes per layer, Bin width.
        LayerMetaData = new Heapfile(layerMetaData);
        Tuple temp2 = new Tuple();

        numFlds = 3;
        attrTypes = new AttrType[numFlds];
        attrTypes[0] = new AttrType(AttrType.attrInteger);
        attrTypes[1] = new AttrType(AttrType.attrInteger);
        attrTypes[2] = new AttrType(AttrType.attrInteger);
        strsizes = new short[numFlds];

        temp2.setHdr(numFlds, attrTypes, strsizes);

        temp2.setIntFld(1,this.numLayers);
        temp2.setIntFld(2,this.noOfHashFunctionsPerLayer);
        temp2.setIntFld(3,this.binLength);

        LayerMetaData.insertRecord(temp2.getTupleByteArray());

    }

    /**
    * LayerStateFile
     * int:LayerNumber, int:HashNumber, 100DVector:HashRandom_Vector, int:HashShift
     *
     * MetaDataFile
     * int: numLayers, int: noOfHashFunctionsPerLayer, int:binLength
     *
     * This constructor is to restore an existing LSHF index with its randomized vectors and shifts.
    * */
    public LSHFIndex(Heapfile LayerStateFile, Heapfile MetaDataFile)
            throws
            InvalidTupleSizeException,
            IOException,
            FieldNumberOutOfBoundException,
            HFDiskMgrException,
            HFException,
            HFBufMgrException
    {

        Scan metaScan = MetaDataFile.openScan();
        Scan stateScan = LayerStateFile.openScan();
        RID rid = new RID();
        Tuple temp = new Tuple();

        // Scan the metadata file and setup the structure of Layers and hashes.
        temp = metaScan.getNext(rid);
        if (temp == null)
        {
            System.out.println ("No such RID");
        }
        else
        {
            this.numLayers = temp.getIntFld(1);
            this.noOfHashFunctionsPerLayer = temp.getIntFld(2);
            this.binLength = temp.getIntFld(3);
        }

        this.layers  = new Layer[this.numLayers];
        for (int i = 0; i < this.numLayers; i++)
        {

            Vector100Dtype[] projVectors = new Vector100Dtype[this.noOfHashFunctionsPerLayer];
            int[] shifts = new int[this.noOfHashFunctionsPerLayer];
            for (int j = 0; j < this.noOfHashFunctionsPerLayer; j++)
            {
                temp = stateScan.getNext(rid);
                if (temp == null)
                {
                    System.out.println ("No such RID");
                }
                else
                {
                    projVectors[j] = temp.get100DVectFld(3);
                    shifts[j] = temp.getIntFld(4);
                }
            }

            // Initialize layer[i] with the new values.
            layers[i] = new Layer(projVectors,shifts, binLength);
        }

    }


    public String[] getAllLayersHash(Vector100Dtype vector)
    {
        String[] hashValues = new String[numLayers];
        for (int i = 0; i < numLayers; i++)
        {
            hashValues[i] = layers[i].GetLayerHashAsString(vector);
        }
        return hashValues;
    }

    public void insertRecord(Vector100Dtype vector, RID rid) throws IOException,
                                                                    HFException,
                                                                    HFBufMgrException,
                                                                    HFDiskMgrException,
                                                                    Exception
    {
        String[] hashValues = getAllLayersHash(vector);
        for (int layer = 0; layer < hashValues.length; layer++)
        {
            String hashValue = hashValues[layer];
            Heapfile heapFile = new Heapfile("layer-" + layer + "-bin-" + hashValue);
            insertRIDIntoHeapfile(heapFile, rid);
        }
    }

    private void insertRIDIntoHeapfile(Heapfile heapFile, RID rid) throws IOException, Exception
    {
        Tuple tuple = new Tuple();
        tuple.setHdr((short) 2, new AttrType[]{new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger)}, null);
        tuple.setIntFld(1, rid.pageNo.pid);
        tuple.setIntFld(2, rid.slotNo);
        heapFile.insertRecord(tuple.returnTupleByteArray());
    }

    public List<String> getBinHeapFileNames(Vector100Dtype vector) throws IOException
    {

        String[] hashValues = getAllLayersHash(vector);
        List<String> binNames = new ArrayList<>();
        for (int layer = 0; layer < hashValues.length; layer++)
        {
            String hashValue = hashValues[layer];
            String heapFileName = "layer-" + layer + "-bin-" + hashValue;
            binNames.add(heapFileName);
        }
        return binNames;
    }
}
