class Node1{
    int type;
}
public class Tt {
    public static void main(String[] args) {
        Node1 n1 = new Node1();
        Node1 n2;
        int c = (n2 = n1).type;
    }
}
