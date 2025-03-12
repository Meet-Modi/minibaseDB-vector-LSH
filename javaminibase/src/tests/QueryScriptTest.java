package tests;

import scripts.BatchInsert;
import scripts.Query;

import static global.GlobalConst.NUMBUF;

public class QueryScriptTest {

    final static String QUERY_SPECIFICATION_FILE_PATH = "./javaminibase/src/tests/scriptTestDataFiles/queryDataFiles/rquery1.txt";
    final static String NUM_BUFFERS = "15";

//    TODO Write tests to check if union file empty when noIndex, only k nearest neighbors being returned, only within range returned
//      TODO Print disk I/O data

    public static void main(String[] args) throws Exception {
        Query.main(new String[]{BatchInsertScriptTest.DB_NAME, QUERY_SPECIFICATION_FILE_PATH, "Y", NUM_BUFFERS});

    }

}
