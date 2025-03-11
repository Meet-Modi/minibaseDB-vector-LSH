package index;

import LSHFIndex.LSHFIndex;
import bufmgr.PageNotReadException;
import global.AttrType;
import global.IndexType;
import global.TupleOrder;
import global.Vector100Dtype;
import heap.Heapfile;
import heap.InvalidTupleSizeException;
import heap.InvalidTypeException;
import heap.Tuple;
import iterator.*;
import scripts.BatchInsert;

import java.io.IOException;
import java.util.stream.IntStream;

public class RSIndexScan extends Iterator {

    private final Vector100Dtype target;
    private final Tuple targetTuple;
    private LSHFIndex lshfIndex;
    private final AttrType[] attrTypes;
    private final short numAttributes;
    private final short[] strLengths;
    private final int vectorFieldNumber;
    private final FldSpec[] projList;
    private final int numAttributesOut;

    private FileScan unionFileScan;
    private Sort sort;
    private final int maxDistance;

    private final Tuple outTuple;

    public RSIndexScan(IndexType index,
                       java.lang.String relName, java.lang.String indName,
                       AttrType[] types, short[] str_sizes, int noInFlds,
                       int noOutFlds, FldSpec[] outFlds,
                       CondExpr[] selects,
                       int fldNum,
                       Vector100Dtype query, int distance) throws Exception {

        if((index != null) && (index.indexType != IndexType.Lsh))
            throw new RuntimeException("RSIndexScan can only be used with index type LSH");

        target = query;
        targetTuple = new Tuple();
        targetTuple.setHdr((short) 1, new AttrType[]{new AttrType(AttrType.attrVector100D)}, new short[0]);
        targetTuple.set100DVectFld(1, target);

        attrTypes = types;
        numAttributes = (short)noInFlds;
        strLengths = str_sizes;
        vectorFieldNumber = fldNum;
        this.maxDistance = distance;
        projList = outFlds;
        numAttributesOut = noOutFlds;

        outTuple = new Tuple();
        TupleUtils.setup_op_tuple(outTuple, new AttrType[noOutFlds], attrTypes, numAttributes, strLengths, projList, numAttributesOut);

        if(index != null)
            lshfIndex = new LSHFIndex(fldNum);
    }

    @Override
    public Tuple get_next() throws Exception {
        if(unionFileScan == null) {
//            Called first time
            if(lshfIndex != null)
                lshfIndex.union(target, new Heapfile(BatchInsert.DB_DATA_HEAP_FILE_NAME));

            FldSpec[] projlist= new FldSpec[numAttributes];
            IntStream.range(0, numAttributes).forEach(i -> projlist[i] = new FldSpec(new RelSpec(RelSpec.outer), i+1));
            unionFileScan = new FileScan(
                    (lshfIndex != null) ? LSHFIndex.UNION_DUMP_HEAP_FILE_NAME : BatchInsert.DB_DATA_HEAP_FILE_NAME,
                    attrTypes, strLengths, numAttributes, numAttributes, projlist, null
            );

//            TODO Pass a good number of buffers
            sort = new Sort(attrTypes, numAttributes, strLengths, unionFileScan, vectorFieldNumber, new TupleOrder(TupleOrder.Ascending), 100, 12, target, 0);
        }

        Tuple currentTuple = sort.get_next();
        if(currentTuple == null)
            return null;
        int currDistance = TupleUtils.CompareTupleWithTuple(new AttrType(AttrType.attrVector100D), targetTuple, 1, currentTuple, vectorFieldNumber);
        if(currDistance > maxDistance)
            return null;

//        DEBUG - Uncomment to see distance from target
//        System.out.println("Distance from Target = " + currDistance);

        currentTuple.setHdr(numAttributes, attrTypes, strLengths);
        Projection.Project(currentTuple, attrTypes, outTuple, projList, numAttributesOut);
        return outTuple;
    }

    @Override
    public void close() {
        try {
            sort.close();
            unionFileScan.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
