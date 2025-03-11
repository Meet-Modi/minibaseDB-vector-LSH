package tests;

import scripts.BatchInsert;
import scripts.Query;

import static global.GlobalConst.NUMBUF;

public class QueryScriptTest {

    final static String QUERY_SPECIFICATION_FILE_PATH = "./javaminibase/src/tests/scriptTestDataFiles/queryDataFiles/nquery1.txt";
    final static String NUM_BUFFERS = "4000";


    public static void main(String[] args) throws Exception {
        Query.main(new String[]{BatchInsertScriptTest.DB_NAME, QUERY_SPECIFICATION_FILE_PATH, "Y", NUM_BUFFERS});

    }

}
