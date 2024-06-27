package cfg;

import util.Id;
import util.Label;
import util.Todo;
import util.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Cse {

    private HashMap<Object, Tuple.Two<Set<Cfg.Exp.T>, Set<Cfg.Exp.T>>> inOutMap;

    private HashMap<Cfg.Exp.T, Id> expIdMap;
    private HashMap<Cfg.Exp.T, Id> localExpIdMap;

    public Cse() {
    }


    private void useId(Id id) {
        List<Cfg.Exp.T> removeExp = new ArrayList<>();
        for (Cfg.Exp.T exp: this.localExpIdMap.keySet()) {
            switch (exp) {
                case Cfg.Exp.Bop(String op, List<Id> operands, Cfg.Type.T type) -> {
                    for (Id operand: operands) {
                        if (operand.equals(id)) {
                            removeExp.add(exp);
                        }
                    }
                }
                default -> {
                }
            }
        }
        removeExp.forEach(exp -> this.localExpIdMap.remove(exp));
    }

    // /////////////////////////////////////////////////////////
    // statement
    private Cfg.Stm.T doitStm(Cfg.Stm.T t) {
        switch (t) {
            case Cfg.Stm.Assign(Id id, Cfg.Exp.T exp) -> {
                switch(exp) {
                    case Cfg.Exp.Bop(String op, List<Id> operands, Cfg.Type.T type) -> {
                        if (this.localExpIdMap.containsKey(exp)) {
                            return new Cfg.Stm.Assign(id, new Cfg.Exp.Eid(this.localExpIdMap.get(exp), type));
                        }
                        if (this.expIdMap.containsKey(exp)) {
                            return new Cfg.Stm.Assign(id, new Cfg.Exp.Eid(this.expIdMap.get(exp), type));
                        }
                        this.localExpIdMap.put(exp, id);
                        useId(id);
                        return t;
                    }
                    default -> {
                        useId(id);
                        return t;
                    }
                }
            }
            case Cfg.Stm.AssignArray(Id id, Cfg.Exp.T index, Cfg.Exp.T exp) -> {
                switch(exp) {
                    case Cfg.Exp.Bop(String op, List<Id> operands, Cfg.Type.T type) -> {
                        if (this.localExpIdMap.containsKey(exp)) {
                            return new Cfg.Stm.Assign(id, new Cfg.Exp.Eid(this.localExpIdMap.get(exp), type));
                        }
                        if (this.expIdMap.containsKey(exp)) {
                            return new Cfg.Stm.Assign(id, new Cfg.Exp.Eid(this.expIdMap.get(exp), type));
                        }
                        this.localExpIdMap.put(exp, id);
                        useId(id);
                        return t;
                    }
                    default -> {
                        return t;
                    }
                }
            }
        }
    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // transfer
    private void doitTransfer(Cfg.Transfer.T t) {
        throw new Todo();
    }

    // /////////////////////////////////////////////////////////
    // block
    private Cfg.Block.T doitBlock(Cfg.Block.T b) {
        this.localExpIdMap = new HashMap<>();
        switch (b) {
            case Cfg.Block.Singleton(
                    Label label,
                    List<Cfg.Stm.T> stms,
                    List<Cfg.Transfer.T> transfer
            ) -> {
                Tuple.Two<Set<Cfg.Exp.T>, Set<Cfg.Exp.T>> inOut = inOutMap.get(b);
                Set<Cfg.Exp.T> in = inOut.first();
                Set<Cfg.Exp.T> out = inOut.second();

                List<Cfg.Stm.T> newStms = new ArrayList<>();
                for (Cfg.Stm.T stm : stms) {
                    newStms.add(doitStm(stm));
                }

                for (Cfg.Exp.T exp: out.getSet()) {
                    if (this.localExpIdMap.containsKey(exp)) {
                        this.expIdMap.put(exp, this.localExpIdMap.get(exp));
                    }
                }

                return new Cfg.Block.Singleton(label, newStms, transfer);
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // function
    // TODO: lab3, exercise 10.
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
                expIdMap = new HashMap<>();

                List<Cfg.Block.T> newBlocks = blocks.stream().map(this::doitBlock).toList();
                return new Cfg.Function.Singleton(retType, classId, functionId, formals, locals, newBlocks);
            }
        }
    }

    public Cfg.Program.T doitProgram(Cfg.Program.T prog) {
        switch (prog) {
            case Cfg.Program.Singleton(
                    Id mainClassId,
                    Id mainFuncId,
                    List<Cfg.Vtable.T> vtables,
                    List<Cfg.Struct.T> structs,
                    List<Cfg.Function.T> functions
            ) -> {
                this.inOutMap = new AvailExp().doitProgram(prog);
                var newFunctions =
                        functions.stream().map(this::doitFunction).toList();

                prog = new Cfg.Program.Singleton(mainClassId, mainFuncId, vtables, structs, newFunctions);

//                prog = new DeadCode().doitProgram(prog);
                return prog;
            }
        }
    }
}
