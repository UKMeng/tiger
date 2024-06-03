package cfg;

import util.Id;
import util.Label;
import util.Todo;
import util.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class ConstProp {

    private HashMap<Object, Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>>> inOutMap;
    private HashSet<Id> constId;
    private HashMap<Id, Id> constMap;

    public ConstProp() {
    }

    private Cfg.Exp.T doitExp(Cfg.Exp.T exp, Id stmId) {
        switch (exp) {
            case Cfg.Exp.Int(int num) -> {
                constId.add(stmId);
                return exp;
            }
            case Cfg.Exp.Eid(Id id2, Cfg.Type.T type) -> {
                if (constId.contains(id2) || constMap.containsKey(id2)) {
                    while (!constId.contains(id2)) {
                        id2 = constMap.get(id2);
                    }
                    constMap.put(stmId, id2);
                    return new Cfg.Exp.Eid(id2, type);
                }
                return exp;
            }
            case Cfg.Exp.Bop(String op, List<Id> operands, Cfg.Type.T type) -> {
                List<Id> newOperands = new ArrayList<>();
                for (Id id2 : operands) {
                    if (constMap.containsKey(id2)) {
                        newOperands.add(constMap.get(id2));
                    } else {
                        newOperands.add(id2);
                    }
                }
                return new Cfg.Exp.Bop(op, newOperands, type);
            }
            default -> {
                return exp;
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // statement
    private Cfg.Stm.T doitStm(Cfg.Stm.T t) {
        switch (t) {
            case Cfg.Stm.Assign(Id id, Cfg.Exp.T exp) -> {
                Cfg.Exp.T newExp = doitExp(exp, id);
                return new Cfg.Stm.Assign(id, newExp);
            }
            case Cfg.Stm.AssignArray(Id id, Cfg.Exp.T index, Cfg.Exp.T exp) -> {
                Cfg.Exp.T newIndex = doitExp(index, id);
                Cfg.Exp.T newExp = doitExp(exp, id);
                return new Cfg.Stm.AssignArray(id, newIndex, newExp);
            }
        }
    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // transfer
    private void doitTransfer(Cfg.Transfer.T t) {
        switch(t) {
            case Cfg.Transfer.If(Id id, Cfg.Block.T b1, Cfg.Block.T b2) -> {
                if (constMap.containsKey(id)) {
                    t = new Cfg.Transfer.If(constMap.get(id), b1, b2);
                }
            }
            case Cfg.Transfer.Jmp(Cfg.Block.T target) -> {
                // do nothing
            }
            case Cfg.Transfer.Ret(Id id) -> {
                if (constMap.containsKey(id)) {
                    t = new Cfg.Transfer.Ret(constMap.get(id));
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // block
    private Cfg.Block.T doitBlock(Cfg.Block.T b) {
        switch (b) {
            case Cfg.Block.Singleton(
                    Label label,
                    List<Cfg.Stm.T> stms,
                    List<Cfg.Transfer.T> transfer
            ) -> {
                Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>> inOut = inOutMap.get(b);
                Set<Cfg.Stm.T> liveStmSet = inOut.second();
                List<Cfg.Stm.T> newStms = new ArrayList<>();
                for (Cfg.Stm.T stm: stms) {
                    if (liveStmSet.getSet().contains(stm)) {
                        newStms.add(doitStm(stm));
                    }
                }
                for (Cfg.Transfer.T t: transfer) {
                    doitTransfer(t);
                }
                return new Cfg.Block.Singleton(label, newStms, transfer);
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
                constMap = new HashMap<>();
                constId = new HashSet<>();
                List<Cfg.Block.T> newBlocks = blocks.stream().map(this::doitBlock).toList();

                return new Cfg.Function.Singleton(retType, classId, functionId, formals, locals, newBlocks);
            }
        }
    }

    // TODO: lab3, exercise 12.
    public Cfg.Program.T doitProgram(Cfg.Program.T prog) {
        switch (prog) {
            case Cfg.Program.Singleton(
                    Id mainClassId,
                    Id mainFuncId,
                    List<Cfg.Vtable.T> vtables,
                    List<Cfg.Struct.T> structs,
                    List<Cfg.Function.T> functions
            ) -> {
                this.inOutMap = new ReachDef().doitProgram(prog);
                var newFunctions =
                        functions.stream().map(this::doitFunction).toList();

                prog = new Cfg.Program.Singleton(mainClassId, mainFuncId, vtables, structs, newFunctions);

                prog = new DeadCode().doitProgram(prog);
                return prog;
            }
        }
    }
}
