package scripts;

public class ScriptMetrics {
    private static long timeStarted = 0;
    private static long timeEnded = 0;
    private static long timeReinitializeLshIndexStart = 0;
    private static long timeReinitializeLshIndexEnd = 0;

//    LSH Union method specific times
    private static long timeUnionDeduplicationStart = 0;
    private static long timeUnionDeduplicationEnd = 0;
    private static long timeUnionDataCopyStart = 0;
    private static long timeUnionDataCopyEnd = 0;

    private static long timeDataSortStart = 0;
    private static long timeDataSortEnd = 0;

    private static int numberOfTuplesToSort = 0;
    private static int numberOfTuplesReturned = 0;

    private final static float MILLIS_IN_MINUTE = (float) 60_000.0;

    public static void setTimeStarted() {timeStarted = System.currentTimeMillis();}
    public static void setTimeEnded() {timeEnded = System.currentTimeMillis();}
    public static void setTimeReinitializeLshIndexStart() {timeReinitializeLshIndexStart = System.currentTimeMillis();}
    public static void setTimeReinitializeLshIndexEnd() {timeReinitializeLshIndexEnd = System.currentTimeMillis();}
    public static void setTimeUnionDeduplicationStart() {timeUnionDeduplicationStart = System.currentTimeMillis();}
    public static void setTimeUnionDeduplicationEnd() {timeUnionDeduplicationEnd = System.currentTimeMillis();}
    public static void setTimeUnionDataCopyStart() {timeUnionDataCopyStart = System.currentTimeMillis();}
    public static void setTimeUnionDataCopyEnd() {timeUnionDataCopyEnd = System.currentTimeMillis();}
    public static void setTimeDataSortStart() {timeDataSortStart = System.currentTimeMillis();}
    public static void setTimeDataSortEnd() {timeDataSortEnd = System.currentTimeMillis();}
    public static void setNumberOfTuplesToSort(int count) {numberOfTuplesToSort = count;}
    public static void incrementNumberOfTuplesReturned() {numberOfTuplesReturned++;}

    public static void printMetricsReport() {
        System.out.println("\n----Metrics Report----");
        System.out.println("Total Execution Time = " + ((timeEnded - timeStarted) / MILLIS_IN_MINUTE) + " min");

        if(timeReinitializeLshIndexStart != 0){
//            LSH Index was used
            System.out.println("\nLSH Index Reinitialization Time = " +
                    ((timeReinitializeLshIndexEnd - timeReinitializeLshIndexStart) / MILLIS_IN_MINUTE) + "min");
            System.out.println("LSH Index Union Deduplication Time = " +
                    ((timeUnionDeduplicationEnd - timeUnionDeduplicationStart) / MILLIS_IN_MINUTE) + "min");
            System.out.println("LSH Index Union Data Copy Time = " +
                    ((timeUnionDataCopyEnd - timeUnionDataCopyStart) / MILLIS_IN_MINUTE) + "min");
        }
        System.out.println("\nData Sort Time = " + ((timeDataSortEnd - timeDataSortStart) / MILLIS_IN_MINUTE) + " min");
        System.out.println("Number of Tuples to Sort = " + numberOfTuplesToSort);

        System.out.println("\nNumber of Tuples Returned = " + numberOfTuplesReturned);

        System.out.println("\n\n\n\n\n\n\n\n");
    }


}
