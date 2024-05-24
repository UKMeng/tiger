class Sum { 
	public static void main(String[] a) {
        System.out.println(new Doit().doit(101));
    }
}

class Doit {
    int unused_field;
    public int doit(int n)  {
        int sum;
        int i;
        boolean unused_var;
        int[] array;

        i = 0;
        sum = 0;
        unused_var = true;
        array = new int[unused_var];
        array[unused_var] = true;
        sum = array[unused_var];
        sum = sum + unused_var;
        if (sum) {
            sum = sum + 1;
        } else {
            sum = sum + 2;
        }
        i = sum.length;
        while (i < n) {
            sum = sum + i;
            i = i + 1;
        }
        return sum;
    }
}
