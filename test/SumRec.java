class SumRec {
    public static void main(String[] a) {
        System.out.println(new Doit().doit(100));
    }
}

class Doit {
    public int doit(int n) {
        int sum;
        int test;
        int y;

        test = 0;
        test = 1;
        y = test;
        if (n < 1) {
            sum = 0;
        }
        else
            sum = n + (this.doit(n - 1));
        return sum;
    }
}
