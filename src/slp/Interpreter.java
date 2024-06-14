package slp;

import slp.Slp.Exp;
import slp.Slp.Stm;
import util.Todo;

import java.util.HashMap;

import java.util.List;

// an interpreter for the SLP language.
public class Interpreter {
    // an abstract memory mapping each variable to its value
    HashMap<String, Integer> memory = new HashMap<>();

    // ///////////////////////////////////////////
    // interpret an expression
    private int interpExp(Exp.T exp) {
        switch (exp) {
            case Exp.Num(int n) -> {
                return n;
            }
            case Exp.Id(String x) -> {
                return memory.get(x);
            }
            case Exp.Op(
                Exp.T left,
                String bop,
                Exp.T right
            ) -> {
                if (bop == "+") {
                    return interpExp(left) + interpExp(right);
                } else if (bop == "-") {
                    return interpExp(left) - interpExp(right);
                } else if (bop == "*") {
                    return interpExp(left) * interpExp(right);
                } else if (bop == "/") {
                    int l = interpExp(left);
                    int r = interpExp(right);
                    if (r == 0) {
                        throw new ArithmeticException("Division by zero");
                    }
                    return l / r;
                }
            }
            case Exp.Eseq(Stm.T stm, Exp.T e) -> {
                interpStm(stm);
                return interpExp(e);
            }
        }
        throw new Todo(exp); // if the expression is not matched
    }

    // ///////////////////////////////////////////
    // interpret a statement
    public void interpStm(Stm.T stm) {
        switch (stm) {
            case Stm.Compound(
                    Stm.T s1,
                    Stm.T s2
            ) -> {
                interpStm(s1);
                interpStm(s2);
            }
            case Stm.Assign(
                    String x,
                    Exp.T e
            ) -> {
                memory.put(x, interpExp(e));
            }
            case Stm.Print(List<Exp.T> exps) -> {
                int size = exps.size();
                for (int i = 0; i < size; i++) {
                    System.out.print(interpExp(exps.get(i)));
                    if (i != size - 1) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            }
        }

    }
}
