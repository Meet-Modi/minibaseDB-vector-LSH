package LSHFIndex;

import global.AttrType;
import global.PageId;
import global.RID;
import global.SystemDefs;
import heap.HFPage;
import heap.InvalidSlotNumberException;
import heap.Tuple;
import diskmgr.Page;
import LSHFIndex.ConstructPageException;
import LSHFIndex.InsertRecException;
import LSHFIndex.DeleteRecException;

import java.io.IOException;

public class LSHFLeafPage extends HFPage {

    /**
     * pin the page with pageno, and get the corresponding LSHFLeafPage
     *
     * @param pageno input parameter. To specify which page number the
     *               LSHFLeafPage will correspond to.
     * @throws ConstructPageException error for LSHFLeafPage constructor
     */
    public LSHFLeafPage(PageId pageno) throws ConstructPageException {
        super();
        try {
            SystemDefs.JavabaseBM.pinPage(pageno, this, false/*Rdisk*/);
        } catch (Exception e) {
            throw new ConstructPageException(e, "construct leaf page failed");
        }
    }

    /**
     * associate the LSHFLeafPage instance with the Page instance
     *
     * @param page input parameter. To specify which page the
     *             LSHFLeafPage will correspond to.
     */
    public LSHFLeafPage(Page page) {
        super(page);
    }

    /**
     * new a page, and associate the LSHFLeafPage instance with the Page instance
     *
     * @throws ConstructPageException error for LSHFLeafPage constructor
     */
    public LSHFLeafPage() throws ConstructPageException {
        super();
        try {
            Page apage = new Page();
            PageId pageId = SystemDefs.JavabaseBM.newPage(apage, 1);
            if (pageId == null)
                throw new ConstructPageException(null, "construct new page failed");
            this.init(pageId, apage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConstructPageException(e, "construct leaf page failed");
        }
    }

    /**
     * Insert a record into the leaf page.
     *
     * @param rid the RID to be inserted. Input parameter.
     * @return its rid where the entry was inserted; null if no space left.
     * @throws InsertRecException error when insert
     */
    public RID insertRecord(RID rid) throws InsertRecException {
        try {
            Tuple tuple = new Tuple();
            tuple.setHdr((short) 2, new AttrType[]{new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger)}, null);
            tuple.setIntFld(1, rid.pageNo.pid);
            tuple.setIntFld(2, rid.slotNo);
            return super.insertRecord(tuple.returnTupleByteArray());
        } catch (Exception e) {
            throw new InsertRecException(e, "insert record failed");
        }
    }

    /**
     * Get the first record in the leaf page.
     *
     * @param rid output parameter. It will be set to the RID of the first record.
     * @return the first RID in the page; null if no records.
     * @throws IOException I/O errors
     */
    public RID getFirst(RID rid) throws IOException, InvalidSlotNumberException {
        RID firstRid = firstRecord();
        if (firstRid == null) {
            return null;
        }
        rid.copyRid(firstRid);
        return getRID(rid);
    }

    /**
     * Get the next record in the leaf page.
     *
     * @param rid input/output parameter. It will be set to the RID of the next record.
     * @return the next RID in the page; null if no more records.
     * @throws IOException I/O errors
     */
    public RID getNext(RID rid) throws IOException, InvalidSlotNumberException {
        RID nextRid = nextRecord(rid);
        if (nextRid == null) {
            return null;
        }
        rid.copyRid(nextRid);
        return getRID(rid);
    }

    /**
     * Get the current record in the leaf page.
     *
     * @param rid input parameter. It specifies the RID of the current record.
     * @return the current RID in the page.
     * @throws IOException I/O errors
     */
    public RID getCurrent(RID rid) throws IOException, InvalidSlotNumberException {
        return getRID(rid);
    }

    /**
     * Delete a record from the leaf page.
     *
     * @param rid it specifies where a record will be deleted
     * @return true if success; false if rid is invalid (no record in the rid).
     * @throws DeleteRecException error when delete
     */
    public boolean deleteRecord(RID rid) throws DeleteRecException {
        try {
            super.deleteRecord(rid);
            compact_slot_dir();
            return true;
        } catch (Exception e) {
            if (e instanceof InvalidSlotNumberException)
                return false;
            else
                throw new DeleteRecException(e, "delete record failed");
        }
    }

    /**
     * Get the number of records in the page.
     *
     * @return the number of records.
     * @throws IOException I/O errors
     */
    public int numberOfRecords() throws IOException {
        return getSlotCnt();
    }

    /**
     * Helper method to get a RID from a tuple.
     *
     * @param rid the RID of the tuple.
     * @return the RID.
     * @throws IOException I/O errors
     */
    private RID getRID(RID rid) throws IOException, InvalidSlotNumberException {
        Tuple tuple = getRecord(rid);
        tuple.setHdr((short) 2, new AttrType[]{new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger)}, null);
        int pageNo = tuple.getIntFld(1);
        int slotNo = tuple.getIntFld(2);
        return new RID(new PageId(pageNo), slotNo);
    }

    public static void main(String[] args) {
        try {
            LSHFLeafPage leafPage = new LSHFLeafPage();

            // Insert some RIDs
            leafPage.insertRecord(new RID(new PageId(1), 1));
            leafPage.insertRecord(new RID(new PageId(2), 2));
            leafPage.insertRecord(new RID(new PageId(3), 3));

            // Retrieve and print all RIDs
            RID rid = new RID();
            RID firstRid = leafPage.getFirst(rid);
            System.out.println("First RID: PageNo: " + firstRid.pageNo.pid + ", SlotNo: " + firstRid.slotNo);

            RID nextRid;
            while ((nextRid = leafPage.getNext(rid)) != null) {
                System.out.println("Next RID: PageNo: " + nextRid.pageNo.pid + ", SlotNo: " + nextRid.slotNo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}