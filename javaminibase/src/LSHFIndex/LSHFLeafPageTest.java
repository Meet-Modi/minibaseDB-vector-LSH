package LSHFIndex;

import global.PageId;
import global.RID;
import global.SystemDefs;
// import diskmgr.PCounter;

public class LSHFLeafPageTest {

    public static void main(String[] args) {
        try {
            // Initialize the system
            String dbpath = "/tmp/minibase.lshfleafpage.test";
            String logpath = "/tmp/minibase.lshfleafpage.log";
            SystemDefs sysdef = new SystemDefs(dbpath, 1000, 100, "Clock");

            // Create a new LSHFLeafPage
            LSHFLeafPage leafPage = new LSHFLeafPage();

            // Insert some RIDs
            leafPage.insertRecord(new RID(new PageId(1), 1));
            leafPage.insertRecord(new RID(new PageId(2), 2));
            leafPage.insertRecord(new RID(new PageId(3), 3));

            // Retrieve and print all RIDs
            RID rid = new RID();
            RID firstRid = leafPage.getFirst(rid);
            if (firstRid != null) {
                System.out.println("First RID: PageNo: " + firstRid.pageNo.pid + ", SlotNo: " + firstRid.slotNo);

                RID nextRid;
                while ((nextRid = leafPage.getNext(rid)) != null) {
                    System.out.println("Next RID: PageNo: " + nextRid.pageNo.pid + ", SlotNo: " + nextRid.slotNo);
                }
            } else {
                System.out.println("No records found.");
            }

            // Delete a record
            boolean deleteSuccess = leafPage.deleteRecord(new RID(new PageId(2), 2));
            System.out.println("Delete record with PageNo: 2, SlotNo: 2: " + (deleteSuccess ? "Success" : "Failed"));

            // Retrieve and print all RIDs after deletion
            firstRid = leafPage.getFirst(rid);
            if (firstRid != null) {
                System.out.println("First RID after deletion: PageNo: " + firstRid.pageNo.pid + ", SlotNo: " + firstRid.slotNo);

                RID nextRid;
                while ((nextRid = leafPage.getNext(rid)) != null) {
                    System.out.println("Next RID after deletion: PageNo: " + nextRid.pageNo.pid + ", SlotNo: " + nextRid.slotNo);
                }
            } else {
                System.out.println("No records found after deletion.");
            }

            // Print the number of records
            int numRecords = leafPage.numberOfRecords();
            System.out.println("Number of records: " + numRecords);

            // Clean up
            SystemDefs.JavabaseBM.unpinPage(leafPage.getCurPage(), true);
            SystemDefs.JavabaseBM.freePage(leafPage.getCurPage());
            SystemDefs.JavabaseDB.closeDB();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}