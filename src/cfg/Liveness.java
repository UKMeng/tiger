package cfg;

import util.Id;
import util.Label;
import util.Todo;
import util.Tuple;

import java.util.HashMap;
import java.util.List;

public class Liveness {

    // record information within a single "map"
    private final HashMap<Object, Tuple.Two<Set<Id>, Set<Id>>>
            useDefMap;

    // for "block", "transfer", and "statement".
    private final HashMap<Object, Tuple.Two<Set<Id>, Set<Id>>>
            liveInOutMap;

    private Set<Id> formalsId;

    public Liveness() {
        useDefMap = new HashMap<>();
        liveInOutMap = new HashMap<>();
        formalsId = new Set<>();
    }

    private Set<Id> getUse(Cfg.Exp.T exp) {
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
    private void doitStm(Cfg.Stm.T t) {
        switch (t) {
            case Cfg.Stm.Assign(
                    Id id,
                    Cfg.Exp.T exp
            ) -> {
                Set<Id> use = getUse(exp);
                Set<Id> def = new Set<>();
                def.add(id);
                useDefMap.put(t, new Tuple.Two<>(use, def));
            }
            case Cfg.Stm.AssignArray(
                    Id id,
                    Cfg.Exp.T index,
                    Cfg.Exp.T exp
            ) -> {
                Set<Id> use = getUse(exp);
                use.union(getUse(index));
                Set<Id> def = new Set<>();
                def.add(id);
                useDefMap.put(t, new Tuple.Two<>(use, def));
            }
        }
    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // transfer
    private void doitTransfer(Cfg.Transfer.T t) {
        switch (t) {
            case Cfg.Transfer.If(Id x, Cfg.Block.T b1, Cfg.Block.T b2) -> {
                Set<Id> use = new Set<>();
                use.add(x);
                useDefMap.put(t, new Tuple.Two<>(use, new Set<>()));
            }
            case Cfg.Transfer.Jmp(Cfg.Block.T target) -> {
                useDefMap.put(t, new Tuple.Two<>(new Set<>(), new Set<>()));
            }
            case Cfg.Transfer.Ret(Id x) -> {
                Set<Id> use = new Set<>();
                use.add(x);
                useDefMap.put(t, new Tuple.Two<>(use, new Set<>()));
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
                stms.forEach(this::doitStm);
                transfer.forEach(this::doitTransfer);
                Set<Id> in = new Set<>();
                Set<Id> out = new Set<>();

                // get in
                for (Cfg.Stm.T stm : stms) {
                    Tuple.Two<Set<Id>, Set<Id>> ud = useDefMap.get(stm);
                    if (ud != null) {
                        in.union(ud.first());
                    }
                }
                for (Cfg.Transfer.T trans: transfer) {
                    Tuple.Two<Set<Id>, Set<Id>> ud = useDefMap.get(trans);
                    if (ud != null) {
                        in.union(ud.first());
                    }
                }
                for (Cfg.Stm.T stm : stms) {
                    Tuple.Two<Set<Id>, Set<Id>> ud = useDefMap.get(stm);
                    if (ud != null) {
                        in.sub(ud.second());
                    }
                }
                for (Cfg.Transfer.T trans: transfer) {
                    Tuple.Two<Set<Id>, Set<Id>> ud = useDefMap.get(trans);
                    if (ud != null) {
                        in.sub(ud.second());
                    }
                }

                in.sub(formalsId);

                // get out
                for (Cfg.Transfer.T trans : transfer) {
                    switch (trans) {
                        case Cfg.Transfer.If(Id x, Cfg.Block.T b1, Cfg.Block.T b2) -> {
                            Tuple.Two<Set<Id>, Set<Id>> ioB1 = liveInOutMap.get(b1);
                            Tuple.Two<Set<Id>, Set<Id>> ioB2 = liveInOutMap.get(b2);
                            if (ioB1 != null) out.union(ioB1.first());
                            if (ioB2 != null) out.union(ioB2.first());
                        }
                        case Cfg.Transfer.Jmp(Cfg.Block.T target) -> {
                            Tuple.Two<Set<Id>, Set<Id>> io = liveInOutMap.get(target);
                            if (io != null) out.union(io.first());
                        }
                        case Cfg.Transfer.Ret(Id x) -> {
                            // do nothing
                        }
                    }
                }


                Tuple.Two<Set<Id>, Set<Id>> oldInOut = liveInOutMap.get(b);
                if (oldInOut == null || !oldInOut.first().isSame(in) || !oldInOut.second().isSame(out)) {
                    stillChanging = true;
                    liveInOutMap.put(b, new Tuple.Two<>(in, out));
                }
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
                formalsId = new Set<>();
                formals.forEach(dec -> formalsId.add(((Cfg.Dec.Singleton) dec).Id()));
                while (stillChanging) {
                    stillChanging = false;
                    blocks.forEach(this::doitBlock);
                }
                stillChanging = true;
            }
        }
    }

    public HashMap<Object, Tuple.Two<Set<Id>, Set<Id>>>
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
                return liveInOutMap;
            }
        }
    }
}
