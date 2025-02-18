package iterator;

import java.lang.*;
import java.io.*;

import global.*;

/**
 * This clas will hold single select condition
 * It is an element of linked list which is logically
 * connected by OR operators.
 */

public class CondExpr
{

    /**
     * Operator like "<"
     */
    public AttrOperator op;

    /**
     * Types of operands, Null AttrType means that operand is not a
     * literal but an attribute name
     */
    public AttrType type1;
    public AttrType type2;

    /**
     * the left operand and right operand
     */
    public Operand operand1;
    public Operand operand2;

    /**
     * distance between two operands for vectors
      */
    public int distance;
    /**
     * Pointer to the next element in linked list
     */
    public CondExpr next;

    /**
     * constructor
     */
    public CondExpr()
    {
        operand1 = new Operand();
        operand2 = new Operand();
        operand1.integer = 0;
        operand2.integer = 0;
        distance = 0;
        next = null;
    }
    public CondExpr(int dist)
    {
        operand1 = new Operand();
        operand2 = new Operand();
        operand1.integer = 0;
        operand2.integer = 0;
        distance = dist;
        next = null;
    }

    // used to set the distance parameter of CondExpr if both operands are 100DVectors
    public void setDistance(int distance)
    {
        this.distance = distance;
    }

    public int getDistance()
    {
        return distance;
    }

}

