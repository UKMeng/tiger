package cfg;

import util.Id;
import util.Label;
import util.Todo;
import util.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeadCode {

    private HashMap<Label, Tuple.Two<Set<Id>, Set<Id>>> inOutMap;

    public DeadCode() {
    }


    private Set<Id> doitExp(Cfg.Exp.T exp) {
        Set<Id> use = new Set<>();
        switch (exp) {
            case Cfg.Exp.Bop(String op, List<Id> operands, Cfg.Type.T type) -> {
                for (Id id : operands) {
                    use.add(id);
                }
            }
            case Cfg.Exp.Call(Id func, List<Id> operands, Cfg.Type.T type) -> {
                use.add(func);
                for (Id id : operands) {
                    use.add(id);
                }
            }
            case Cfg.Exp.Eid(Id id, Cfg.Type.T type) -> {
                use.add(id);
            }
            case Cfg.Exp.GetMethod(Id objId, Id classId, Id methodId) -> {
                use.add(objId);
            }
            case Cfg.Exp.Print(Id id) -> {
                use.add(id);
            }
            case Cfg.Exp.Length(Id id) -> {
                use.add(id);
            }
            case Cfg.Exp.IntArraySelect(Id id, Id index) -> {
                use.add(id);
                use.add(index);
            }
            default -> {
                // do nothing
            }
        }
        return use;
    }

    // /////////////////////////////////////////////////////////
    // statement
    private boolean doitStm(Cfg.Stm.T t, Set<Id> liveSet) {
        Id definedVar = null;
        switch(t) {
            case Cfg.Stm.Assign(Id id, Cfg.Exp.T exp) -> {
                definedVar = id;
                if (!liveSet.getSet().contains(definedVar)) {
                    if (exp instanceof Cfg.Exp.Print) {
                        Set<Id> useSet = doitExp(exp);
                        liveSet.union(useSet);
                        return true;
                    }
                    return false;
                } else {
                    Set<Id> useSet = doitExp(exp);
                    liveSet.union(useSet);
                    return true;
                }
            }
            case Cfg.Stm.AssignArray(Id id, Cfg.Exp.T index, Cfg.Exp.T exp) -> {
                definedVar = id;
                if (!liveSet.getSet().contains(definedVar)) {
                    return false;
                } else {
                    Set<Id> useSet = doitExp(index);
                    useSet.union(doitExp(exp));
                    liveSet.union(useSet);
                    return true;
                }
            }
        }
    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // transfer
    private void doitTransfer(Cfg.Transfer.T t, Set<Id> liveSet) {
        switch (t) {
            case Cfg.Transfer.If(Id x, Cfg.Block.T b1, Cfg.Block.T b2) -> {
                liveSet.add(x);
            }
            case Cfg.Transfer.Jmp(Cfg.Block.T target) -> {
                // do nothing
            }
            case Cfg.Transfer.Ret(Id x) -> {
                liveSet.add(x);
            }
        }
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
                Tuple.Two<Set<Id>, Set<Id>> inOut = this.inOutMap.get(Cfg.Block.getLabel(b));
                Set<Id> liveSet = inOut.second();

                for (int i = transfer.size() - 1; i >= 0; i--) {
                    Cfg.Transfer.T transferStm = transfer.get(i);
                    doitTransfer(transferStm, liveSet);
                }

                List<Cfg.Stm.T> removeStms = new ArrayList<>();
                for (int i = stms.size() - 1; i >= 0; i--) {
                    Cfg.Stm.T stm = stms.get(i);
                    if (!doitStm(stm, liveSet)) {
                        removeStms.add(stm);
                    }
                }

                for (Cfg.Stm.T stm : removeStms) {
                    stms.remove(stm);
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // function
    private Cfg.Function.T doitFunction(Cfg.Function.T func) {
        switch (func) {
            case Cfg.Function.Singleton(
                    Cfg.Type.T retType,
                    Id classId,
                    Id functionId,
                    List<Cfg.Dec.T> formals,
                    List<Cfg.Dec.T> locals,
                    List<Cfg.Block.T> blocks
            ) -> {
                // an algorithm to detect uninitialized variables, by leveraging the results of liveness analysis.
                if (blocks.size() != 0) {
                    Cfg.Block.T block = blocks.get(0);
                    Tuple.Two<Set<Id>, Set<Id>> inOut = this.inOutMap.get(Cfg.Block.getLabel(block));
                    if(inOut != null && inOut.first() != null) {
                        Set<Id> firstIn = inOut.first();
                        if(firstIn.getSet().size() != 0) {
                            for (Id id : firstIn.getSet()) {
                                System.out.println("Error: Uninitialized variable: " + id);
                            }
                        }
                    }
                }


                blocks.forEach(this::doitBlock);
                return func;
            }
        }
    }

    // TODO: lab3, exercise 10.
    public Cfg.Program.T doitProgram(Cfg.Program.T prog) {
        switch (prog) {
            case Cfg.Program.Singleton(
                    Id mainClassId,
                    Id mainFuncId,
                    List<Cfg.Vtable.T> vtables,
                    List<Cfg.Struct.T> structs,
                    List<Cfg.Function.T> functions
            ) -> {
                this.inOutMap = new Liveness().doitProgram(prog);
                var newFunctions =
                        functions.stream().map(this::doitFunction).toList();

                prog = new Cfg.Program.Singleton(mainClassId, mainFuncId, vtables, structs, newFunctions);
                return prog;
            }
        }
    }
}
