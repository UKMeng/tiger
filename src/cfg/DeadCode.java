package cfg;

import util.Id;
import util.Label;
import util.Todo;
import util.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeadCode {

    public DeadCode() {
    }


    // /////////////////////////////////////////////////////////
    // statement
    private void doitStm(Cfg.Stm.T t) {
        throw new Todo();
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
            ) -> throw new Todo();
        }
    }

    // /////////////////////////////////////////////////////////
    // function
    private Cfg.Function.T doitFunction(Cfg.Function.T func) {
//        switch (func) {
//            case Cfg.Function.Singleton(
//                    Cfg.Type.T retType,
//                    Id classId,
//                    Id functionId,
//                    List<Cfg.Dec.T> formals,
//                    List<Cfg.Dec.T> locals,
//                    List<Cfg.Block.T> blocks
//            ) -> throw new Todo();
//        }
        return func;
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
                HashMap<Object, Tuple.Two<Set<Id>, Set<Id>>>
                        inOutMap = new Liveness().doitProgram(prog);
                var newFunctions =
                        functions.stream().map(this::doitFunction).toList();
                // TODO: your code here:
                //
                List<Id> uninitalizedVars = new ArrayList<>();

                for (Cfg.Function.T function : newFunctions) {
                    Set<Id> inSet = inOutMap.get(function).first();
                    Set<Id> outSet = inOutMap.get(function).second();

                    for (Id var: outSet.getSet()) {
                        if (!inSet.getSet().contains(var)) {
                            uninitalizedVars.add(var);
                        }
                    }
                }

                if (uninitalizedVars.size() > 0) {
                    System.out.println("Error: Uninitialized variables: " + uninitalizedVars);
                }


                return prog;
            }
        }
    }
}
