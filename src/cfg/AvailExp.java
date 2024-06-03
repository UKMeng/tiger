package cfg;

import util.Id;
import util.Label;
import util.Todo;
import util.Tuple;

import java.util.HashMap;
import java.util.List;

public class AvailExp {

    // record information within a single "map"
    private HashMap<Object, Tuple.Two<Set<Cfg.Exp.T>, Set<Cfg.Exp.T>>>
            genKillMap;

    // for "block", "transfer", and "statement".
    private final HashMap<Object, Tuple.Two<Set<Cfg.Exp.T>, Set<Cfg.Exp.T>>>
            inOutMap;

    public AvailExp() {
        genKillMap = new HashMap<>();
        inOutMap = new HashMap<>();
    }

    private boolean expContains(Cfg.Exp.T exp, Id id) {
        switch (exp) {
            case Cfg.Exp.Bop(String op, List<Id> operands, Cfg.Type.T type) -> {
                for (Id operand: operands) {
                    if (operand.equals(id)) {
                        return true;
                    }
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // statement
    private void doitStm(Cfg.Stm.T t) {
        if (genKillMap.containsKey(t)) {
            return;
        }
        Set<Cfg.Exp.T> gen = new Set<>();
        Set<Cfg.Exp.T> kill = new Set<>();
        switch(t) {
            case Cfg.Stm.Assign(Id id, Cfg.Exp.T exp) -> {
                if (exp instanceof Cfg.Exp.Bop binOp) {
                    gen.add(exp);
                }
                for (Object obj : genKillMap.keySet()) {
                    Cfg.Stm.T stm = (Cfg.Stm.T) obj;
                    Cfg.Exp.T exp_ = Cfg.Stm.GetExp(stm);
                    if (expContains(exp_, id)) {
                        kill.add(exp_);
                    }
                }
            }
            case Cfg.Stm.AssignArray(Id id, Cfg.Exp.T index, Cfg.Exp.T exp) -> {
                if (exp instanceof Cfg.Exp.Bop binOp) {
                    gen.add(exp);
                }
                for (Object obj : genKillMap.keySet()) {
                    Cfg.Stm.T stm = (Cfg.Stm.T) obj;
                    Cfg.Exp.T exp_ = Cfg.Stm.GetExp(stm);
                    if (expContains(exp_, id)) {
                        kill.add(exp_);
                    }
                }
            }
        }

        genKillMap.put(t, new Tuple.Two<>(gen, kill));
    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // transfer
    private void doitTransfer(Cfg.Transfer.T t) {
        throw new Todo();
    }

    // /////////////////////////////////////////////////////////
    // block
    private void doitBlock(Cfg.Block.T b) {
        switch (b) {
            case Cfg.Block.Singleton(
                    Label label,
                    List<Cfg.Stm.T> stms,
                    List<Cfg.Transfer.T> transfer
            ) -> {
                stms.forEach(this::doitStm);

                Set<Cfg.Exp.T> gen = new Set<>();
                Set<Cfg.Exp.T> kill = new Set<>();
                Set<Cfg.Exp.T> in = new Set<>();
                Set<Cfg.Exp.T> out = new Set<>();

                for (Cfg.Stm.T stm: stms) {
                    Tuple.Two<Set<Cfg.Exp.T>, Set<Cfg.Exp.T>> genKill = genKillMap.get(stm);
                    gen.union(genKill.first());
                    kill.union(genKill.second());
                }

                gen.sub(kill);
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // function
    // TODO: lab3, exercise 9.
    private boolean stillChanging = true;

    private void doitFunction(Cfg.Function.T func) {
        switch (func) {
            case Cfg.Function.Singleton(
                    Cfg.Type.T retType,
                    Id classId,
                    Id functionId,
                    List<Cfg.Dec.T> formals,
                    List<Cfg.Dec.T> locals,
                    List<Cfg.Block.T> blocks
            ) -> {
                genKillMap = new HashMap<>();
                while (stillChanging) {
                    stillChanging = false;
                    blocks.forEach(this::doitBlock);
                }
                stillChanging = true;
            }
        }
    }

    public HashMap<Object, Tuple.Two<Set<Cfg.Exp.T>, Set<Cfg.Exp.T>>>
    doitProgram(Cfg.Program.T prog) {
        switch (prog) {
            case Cfg.Program.Singleton(
                    Id mainClassId,
                    Id mainFuncId,
                    List<Cfg.Vtable.T> vtables,
                    List<Cfg.Struct.T> structs,
                    List<Cfg.Function.T> functions
            ) -> {
                functions.forEach(this::doitFunction);
                return inOutMap;
            }
        }
    }
}
