package slp;

import slp.Slp.Exp;
import slp.Slp.Stm;
import util.Todo;

import java.util.List;

public class MaxArgument {
    // ///////////////////////////////////////////
    // expression
    private int maxExp(Exp.T exp) {
        int ret = 0;
        if (exp instanceof Exp.Eseq) {
            Exp.Eseq eseq = (Exp.Eseq) exp;
            ret = maxStm(eseq.stm());
        }
        return ret;
    }

    // ///////////////////////////////////////////
    // statement
    public int maxStm(Stm.T stm) {
        int ret = 0;
        switch (stm) {
            case Stm.Compound(
                    Stm.T s1,
                    Stm.T s2
            ) -> {
                ret = Math.max(ret, maxStm(s1));
                ret = Math.max(ret, maxStm(s2));
            }
            case Stm.Assign(
                    String x,
                    Exp.T e
            ) -> {
                ret = Math.max(ret, maxExp(e));
            }
            case Stm.Print(List<Exp.T> exps) -> {
                ret = Math.max(ret, exps.size());
            }
        }
        return ret;
    }
}
