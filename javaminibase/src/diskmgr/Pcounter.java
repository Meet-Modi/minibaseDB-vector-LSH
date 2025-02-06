package diskmgr;

public class Pcounter
{
    public static int rcounter;
    public static int wcounter;

    public static void initialize()
    {
        rcounter = 0;
        wcounter = 0;

    }

    public static void readIncrement()
    {
        rcounter++;
    }

    public static void writeIncrement()
    {
        wcounter++;
    }

    public static void printPcounter()
    {
        System.out.println("Pages read (rcounter) : " + Pcounter.rcounter);
        System.out.println("Pages written (wcounter): " + Pcounter.wcounter);
    }

}
