class SumRec {
    public static void main(String[] a) {
        System.out.println(new Doit().doit(100));
    }
}

class Doit {
    public int doit(int n) {
        int sum;
        int test;
        int a;
        int b;
        int c;
        int d;
        int e;
        int g;

        a = (b + c) + g;
        d = (b + c) - e;

        if (n < 1) {
            sum = 0;
            sum = a + d;
            test = b + c;
        }
        else
            sum = n + (this.doit(n - 1));
        return sum + test;
    }
}
