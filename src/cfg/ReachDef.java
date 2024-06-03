package cfg;

import util.Id;
import util.Label;
import util.Todo;
import util.Tuple;

import java.util.HashMap;
import java.util.List;

public class ReachDef {

    // record information within a single "map"
    private final HashMap<Object, Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>>>
            genKillMap;

    // for "block", "transfer", and "statement".
    private final HashMap<Object, Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>>>
            inOutMap;

    public ReachDef() {
        genKillMap = new HashMap<>();
        inOutMap = new HashMap<>();
    }


    // /////////////////////////////////////////////////////////
    // statement
    private void doitStm(Cfg.Stm.T t) {
        Set<Cfg.Stm.T> gen = new Set<>();
        Set<Cfg.Stm.T> kill = new Set<>();
        switch(t) {
            case Cfg.Stm.Assign(Id id, Cfg.Exp.T exp) -> {
                gen.add(t);
                for (Object obj : genKillMap.keySet()) {
                     Cfg.Stm.T stm = (Cfg.Stm.T) obj;
                    if (stm instanceof Cfg.Stm.Assign assign) {
                        if (assign.Id().equals(id)) {
                            kill.add(stm);
                        }
                    }
                }
            }
            case Cfg.Stm.AssignArray(Id id, Cfg.Exp.T index, Cfg.Exp.T exp) -> {
                gen.add(t);
                for (Object obj : genKillMap.keySet()) {
                    Cfg.Stm.T stm = (Cfg.Stm.T) obj;
                    if (stm instanceof Cfg.Stm.AssignArray assignArray) {
                        if (assignArray.Id().equals(id)) {
                            kill.add(stm);
                        }
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
        //genKillMap.put(t, new Tuple.Two<>(new Set<>(), new Set<>()));
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
                Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>> oldInOut = inOutMap.get(b);
                Set<Cfg.Stm.T> oldIn = oldInOut == null ? new Set<>() : oldInOut.first();
                Set<Cfg.Stm.T> in = oldIn.clone();

                stms.forEach(this::doitStm);
                transfer.forEach(this::doitTransfer);
                Set<Cfg.Stm.T> out = new Set<>();
                Set<Cfg.Stm.T> gen = new Set<>();
                Set<Cfg.Stm.T> kill = new Set<>();
                for (Cfg.Stm.T stm : stms) {
                    Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>> genKill = genKillMap.get(stm);
                    if (genKill != null) {
                        gen.union(genKill.first());
                        kill.union(genKill.second());
                    }
                }

                in.sub(kill);
                gen.union(in);
                out = gen.clone(); // out = gen U (in - kill)

                for (Cfg.Transfer.T t : transfer) {
                    switch(t) {
                        case Cfg.Transfer.If(Id id, Cfg.Block.T b1, Cfg.Block.T b2) -> {
                            Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>> inoutB1 = inOutMap.get(b1);
                            Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>> inoutB2 = inOutMap.get(b2);
                            if (inoutB1 == null) {
                                inoutB1 = new Tuple.Two<>(out, new Set<>());
                                inOutMap.put(b1, inoutB1);
                            } else {
                                inoutB1.first().union(out);
                            }
                            if (inoutB2 == null) {
                                inoutB2 = new Tuple.Two<>(out, new Set<>());
                                inOutMap.put(b2, inoutB2);
                            } else {
                                inoutB2.first().union(out);
                            }
                        }
                        case Cfg.Transfer.Jmp(Cfg.Block.T target) -> {
                            Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>> inout = inOutMap.get(target);
                            if (inout == null) {
                                inout = new Tuple.Two<>(out, new Set<>());
                                inOutMap.put(target, inout);
                            } else {
                                inout.first().union(out);
                            }
                        }
                        case Cfg.Transfer.Ret(Id id) -> {
                            // do nothing
                        }
                    }
                }

                if (oldInOut == null || !oldInOut.second().isSame(out)) {
                    inOutMap.put(b, new Tuple.Two<>(oldIn, out));
                    stillChanging = true;
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // function
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
                while (stillChanging) {
                    stillChanging = false;
                    blocks.forEach(this::doitBlock);
                }
                stillChanging = true;
            }
        }
    }

    // TODO: lab3, exercise 11.
    public HashMap<Object, Tuple.Two<Set<Cfg.Stm.T>, Set<Cfg.Stm.T>>>
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
