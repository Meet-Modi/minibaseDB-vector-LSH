package LSHFIndex;

import bufmgr.PageNotReadException;
import global.AttrType;
import global.RID;
import global.Vector100Dtype;
import heap.*;
import iterator.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

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

    private static final String LAYER_STATE_HEAPFILE_NAME =  "LayerState";
    private static final String LAYER_METADATA_HEAPFILE_NAME = "LayerMetaData";

    private static final AttrType[] META_TUPLE_ATTR_TYPES = new AttrType[3];
    static {
        META_TUPLE_ATTR_TYPES[0] = new AttrType(AttrType.attrInteger);
        META_TUPLE_ATTR_TYPES[1] = new AttrType(AttrType.attrInteger);
        META_TUPLE_ATTR_TYPES[2] = new AttrType(AttrType.attrInteger);
    }
    private static final FldSpec[] META_TUPLE_PROJ_LIST = new FldSpec[META_TUPLE_ATTR_TYPES.length];
    static {
        IntStream.range(0, META_TUPLE_PROJ_LIST.length).forEach(i -> META_TUPLE_PROJ_LIST[i] = new FldSpec(new RelSpec(RelSpec.outer), i+1));
    }

    private static final AttrType[] STATE_TUPLE_ATTR_TYPES = new AttrType[4];
    static {
        STATE_TUPLE_ATTR_TYPES[0] = new AttrType(AttrType.attrInteger);
        STATE_TUPLE_ATTR_TYPES[1] = new AttrType(AttrType.attrInteger);
        STATE_TUPLE_ATTR_TYPES[2] = new AttrType(AttrType.attrVector100D);
        STATE_TUPLE_ATTR_TYPES[3] = new AttrType(AttrType.attrInteger);
    }
    private static final FldSpec[] STATE_TUPLE_PROJ_LIST = new FldSpec[STATE_TUPLE_ATTR_TYPES.length];
    static {
        IntStream.range(0, STATE_TUPLE_PROJ_LIST.length).forEach(i -> STATE_TUPLE_PROJ_LIST[i] = new FldSpec(new RelSpec(RelSpec.outer), i+1));
    }

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

        Heapfile LayerState = new Heapfile(LAYER_STATE_HEAPFILE_NAME);

        // States to store for each Layer.
        // int: LayerNumber, int: HashNumber, 100DVector: HashRandom_Vector, int: HashShift
        Tuple temp = new Tuple();

        // Set tuple header.
        temp.setHdr((short) STATE_TUPLE_ATTR_TYPES.length, STATE_TUPLE_ATTR_TYPES, new short[0]);

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
        Heapfile LayerMetaData = new Heapfile(LAYER_METADATA_HEAPFILE_NAME);
        Tuple temp2 = new Tuple();

        temp2.setHdr((short) META_TUPLE_ATTR_TYPES.length, META_TUPLE_ATTR_TYPES, new short[0]);

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
    public LSHFIndex()
            throws
            InvalidTupleSizeException,
            IOException,
            FieldNumberOutOfBoundException, HFDiskMgrException, HFException, HFBufMgrException, InvalidRelation, FileScanException, TupleUtilsException, PageNotReadException, UnknowAttrType, PredEvalException, WrongPermat, JoinsException, InvalidTypeException {

        FileScan metaScan = new FileScan(LAYER_METADATA_HEAPFILE_NAME,
                META_TUPLE_ATTR_TYPES,
                new short[0],
                (short) META_TUPLE_ATTR_TYPES.length,
                META_TUPLE_ATTR_TYPES.length,
                META_TUPLE_PROJ_LIST,
                null);

        FileScan stateScan = new FileScan(LAYER_STATE_HEAPFILE_NAME,
                STATE_TUPLE_ATTR_TYPES,
                new short[0],
                (short) STATE_TUPLE_ATTR_TYPES.length,
                STATE_TUPLE_ATTR_TYPES.length,
                STATE_TUPLE_PROJ_LIST,
                null);

        RID rid = new RID();
        Tuple temp = metaScan.get_next();

        // Scan the metadata file and setup the structure of Layers and hashes.
        this.numLayers = temp.getIntFld(1);
        this.noOfHashFunctionsPerLayer = temp.getIntFld(2);
        this.binLength = temp.getIntFld(3);

        this.layers  = new Layer[this.numLayers];
        for (int i = 0; i < this.numLayers; i++)
        {

            Vector100Dtype[] projVectors = new Vector100Dtype[this.noOfHashFunctionsPerLayer];
            int[] shifts = new int[this.noOfHashFunctionsPerLayer];
            for (int j = 0; j < this.noOfHashFunctionsPerLayer; j++)
            {
                temp = stateScan.get_next();
                projVectors[j] = temp.get100DVectFld(3);
                shifts[j] = temp.getIntFld(4);
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

    public void insertRecord(Vector100Dtype vector, RID rid) throws Exception
    {
        String[] hashValues = getAllLayersHash(vector);
        for (int layer = 0; layer < hashValues.length; layer++)
        {
            String hashValue = hashValues[layer];
            Heapfile heapFile = new Heapfile("layer-" + layer + "-bin-" + hashValue);
            insertRIDIntoHeapfile(heapFile, rid);
        }
    }

    private void insertRIDIntoHeapfile(Heapfile heapFile, RID rid) throws Exception
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

    @Override
    public boolean equals(Object obj) {
        if(! (obj instanceof LSHFIndex))
            return false;
        LSHFIndex otherIndex = (LSHFIndex) obj;

        return ((this.binLength == otherIndex.binLength) &&
                (this.numLayers == otherIndex.numLayers) &&
                (this.noOfHashFunctionsPerLayer == otherIndex.noOfHashFunctionsPerLayer) &&
                (Arrays.equals(this.layers, otherIndex.layers)));
    }

}
