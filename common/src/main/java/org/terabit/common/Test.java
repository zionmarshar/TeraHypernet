package org.terabit.common;

public class Test {
    public int aMethod() {
        int i = 0;
        i++;
        return i;
    }
    public static void main (String args[]) {
        Test test = new Test();
        test.aMethod();
        int j = test.aMethod();
        System.out.println(j);
        int i = 0xF1 ^ 0x02;
        System.out.println( new Integer(i).toBinaryString(i));

        String s = "abc122";
        String s2 = new String("abc");
        System.out.println(s == s2);
    }
}

