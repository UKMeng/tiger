package checker;

import ast.Ast;
import ast.Ast.Dec;
import ast.Ast.Type;
import util.Id;
import util.Todo;
import util.Tuple;

import java.util.List;
import static control.Control.Type.dump;
import static control.Control.Type.methodTableDump;

// map each argument and local in a method, to its corresponding type.
// the method table is constructed for each method.
public class MethodTable {
    // map a variable, to its corresponding type and a fresh name.
    private final java.util.HashMap<Id, Tuple.Two<Type.T, Id>> table;
    private final java.util.HashSet<Id> unusedVarSet;

    public MethodTable() {
        this.table = new java.util.HashMap<>();
        this.unusedVarSet = new java.util.HashSet<>();
    }

    // Duplication is not allowed
    public void putFormalLocal(List<Dec.T> formals, List<Dec.T> locals) {
        for (Dec.T dec : formals) {
            Dec.Singleton decc = (Dec.Singleton) dec;
            Ast.AstId aid = decc.aid();
            Id freshId = aid.genFreshId();
            if (this.table.get(aid.id) != null) {
                System.out.println(STR."duplicated parameter: \{aid.id}");
                System.exit(1);
            }
            this.table.put(aid.id, new Tuple.Two<>(decc.type(), freshId));
            this.unusedVarSet.add(freshId);
        }

        for (Dec.T dec : locals) {
            Dec.Singleton decc = (Dec.Singleton) dec;
            Ast.AstId aid = decc.aid();
            Id freshId = aid.genFreshId();
            if (this.table.get(aid.id) != null) {
                System.out.println(STR."duplicated variable: \{aid.id}");
                System.exit(1);
            }
            this.table.put(aid.id, new Tuple.Two<>(decc.type(), freshId));
            this.unusedVarSet.add(freshId);
        }
    }

    public void useVar(Id id) {
        this.unusedVarSet.remove(id);
    }

    public void checkUnusedVar(Ast.AstId methodID) {
        String methodName = methodID.id.toString();
        for (Id id : this.unusedVarSet) {
            System.out.println(STR."Warning: Variable '\{id}' in method '\{methodName}' is never used.");
        }
    }

    // return null for non-existing keys
    public Tuple.Two<Type.T, Id> get(Id id) {
        return this.table.get(id);
    }

    // lab 2, exercise 7:
    public void dump() {
        if (dump || methodTableDump) {
            System.out.println("method table:");
            System.out.println("--------------------------");
            for (Id id : this.table.keySet()) {
                Tuple.Two<Type.T, Id> type = this.table.get(id);
                System.out.println(id + " : " + type);
                System.out.println("--------------------------");
            }

            System.out.println("unused variables:");
            System.out.println("--------------------------");
            for (Id id : this.unusedVarSet) {
                System.out.println(id);
                System.out.println("--------------------------");
            }
        }
    }

    @Override
    public String toString() {
        return this.table.toString();
    }
}
