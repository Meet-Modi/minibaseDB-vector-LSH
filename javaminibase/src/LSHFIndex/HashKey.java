package LSHFIndex;

import global.Vector100Dtype;

import java.util.Random;

public class HashKey
{
    int L, x;
    private int Layers;
    private int shift_b;
    private int bin_length_w;
    private int function_per_layer_k;
    // private Random random;


    public HashKey(int L, int x)
    {
        this.L = L; // no of layers
        this.x = x; // no of bins
        // this.random = new Random();
    }

    public int[] generateHashValues(Vector100Dtype vector)
    {
        int[] hashValues = new int[L];
        for (int i = 0; i < L; i++)
        {
            hashValues[i] = generateHashValue(vector);
        }
        return hashValues;
    }

    private int generateHashValue(Vector100Dtype vector)
    {
        // Dummy hash function: sum of vector elements modulo x + 1

    }

    public static void main(String[] args)
    {
        int L = 5;
        int x = 10;
        HashKey hashKey = new HashKey(L, x);

        Vector100Dtype vector = new Vector100Dtype();
        Random random = new Random();
        for (int i = 0; i < 100; i++)
        {
            vector.vector[i] = (short) random.nextInt(Short.MAX_VALUE);
        }

        // Generate hash values
        int[] hashValues = hashKey.generateHashValues(vector);
        System.out.println("Generated hash values:");
        for (int hashValue : hashValues)
        {
            System.out.println(hashValue);
        }
    }
}