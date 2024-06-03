package cfg;

import ast.Ast;
import control.Control;
import util.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

public class Translate {
    // the generated results:
    private final Vector<Cfg.Vtable.T> vtables;
    private final Vector<Cfg.Struct.T> structs;
    private final Vector<Cfg.Function.T> functions;
    // for bookkeeping purpose:
    private Id currentClassId = null;
    private Id currentThis = null;
    private Cfg.Function.T currentFunction = null;
    private Cfg.Block.T currentBlock = null;
    private Cfg.Block.T currentThennBlock = null;
    private Cfg.Block.T currentElseeBlock = null;
    private Cfg.Block.T currentJumpBlock = null;
    private LinkedList<Cfg.Dec.T> newDecs = new LinkedList<>();
    // for main function
    private Id mainClassId = null;
    private Id mainFunctionId = null;

    public Translate() {
        this.vtables = new Vector<>();
        this.structs = new Vector<>();
        this.functions = new Vector<>();
    }

    /////////////////////////////
    // translate a type
    private Cfg.Type.T transType(Ast.Type.T ty) {
        switch (ty) {
            case Ast.Type.ClassType(Id id) -> {
                return new Cfg.Type.ClassType(id);
            }
            case Ast.Type.Boolean() -> {
                return new Cfg.Type.Int();
            }
            case Ast.Type.IntArray() -> {
                return new Cfg.Type.IntArray();
            }
            case Ast.Type.Int() -> {
                return new Cfg.Type.Int();
            }
        }
    }

    private Cfg.Dec.T transDec(Ast.Dec.T dec) {
        switch (dec) {
            case Ast.Dec.Singleton(Ast.Type.T type, Ast.AstId aid) -> {
                return new Cfg.Dec.Singleton(transType(type), aid.freshId);
            }
        }
    }

    private Cfg.Vtable.Entry transVtables(Ast.Method.T method) {
        switch (method) {
            case Ast.Method.Singleton(
                    Ast.Type.T retType,
                    Ast.AstId methodId,
                    List<Ast.Dec.T> formals,
                    List<Ast.Dec.T> locals,
                    List<Ast.Stm.T> stms,
                    Ast.Exp.T retExp
            ) -> {
                List<Cfg.Dec.T> cfgDecls = transDecList(formals);
                cfgDecls.addAll(transDecList(locals));
                return new Cfg.Vtable.Entry(transType(retType), this.currentClassId, methodId.id, cfgDecls);
            }
        }
    }

    private List<Cfg.Dec.T> transDecList(List<Ast.Dec.T> decs) {
        return decs.stream().map(this::transDec).collect(Collectors.toList());
    }

    private void emit(Cfg.Stm.T s) {
        Cfg.Block.add(this.currentBlock, s);
    }

    private void emitTransfer(Cfg.Transfer.T s) {
        Cfg.Block.addTransfer(this.currentBlock, s);
    }

    private void emitDec(Cfg.Dec.T dec) {
        this.newDecs.add(dec);
    }

    /////////////////////////////
    // translate an expression
    // TODO: lab3, exercise 8.
    private Cfg.Exp.T transExp(Ast.Exp.T exp) {
        switch(exp) {
            case Ast.Exp.Num(int i) -> {
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Int(i)));
                return new Cfg.Exp.Eid(id, new Cfg.Type.Int());
            }
            case Ast.Exp.Bop(Ast.Exp.T left, String op, Ast.Exp.T right) -> {
                Cfg.Exp.T leftExp = transExp(left);
                Cfg.Exp.T rightExp = transExp(right);
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Bop(op, List.of(Cfg.Exp.GetId(leftExp), Cfg.Exp.GetId(rightExp)), new Cfg.Type.Int())));
                return new Cfg.Exp.Eid(id, new Cfg.Type.Int());
            }
            case Ast.Exp.ExpId(Ast.AstId id) -> {
                return new Cfg.Exp.Eid(id.freshId, new Cfg.Type.Int());
            }
            case Ast.Exp.Call(Ast.Exp.T objExp, Ast.AstId methodId, List<Ast.Exp.T> args, Tuple.One<Id> theObejectType, Tuple.One<Ast.Type.T> retType) -> {
                Cfg.Exp.T objId = transExp(objExp);

                Id codeptr = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.CodePtr(), codeptr));
                emit(new Cfg.Stm.Assign(codeptr, new Cfg.Exp.GetMethod(Cfg.Exp.GetId(objId), theObejectType.get(), methodId.id)));

                List<Id> argsId = new ArrayList<>();
                argsId.add(Cfg.Exp.GetId(objId));
                for (Ast.Exp.T arg : args) {
                    Cfg.Exp.T argExp = transExp(arg);
                    argsId.add(Cfg.Exp.GetId(argExp));
                }

                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(transType(retType.get()), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Call(codeptr, argsId, transType(retType.get()))));
                return new Cfg.Exp.Eid(id, transType(retType.get()));
            }
            case Ast.Exp.This() -> {
                return new Cfg.Exp.Eid(this.currentThis, new Cfg.Type.ClassType(this.currentClassId));
            }
            case Ast.Exp.NewObject(Id id) -> {
                Id objId = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.ClassType(id), objId));
                emit(new Cfg.Stm.Assign(objId, new Cfg.Exp.New(id)));
                return new Cfg.Exp.Eid(objId, new Cfg.Type.ClassType(id));
            }
            case Ast.Exp.Uop(String op, Ast.Exp.T exp_) -> {
                Id expId = Cfg.Exp.GetId(transExp(exp_));
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Bop(op, List.of(expId), new Cfg.Type.Int())));
                return new Cfg.Exp.Eid(id, new Cfg.Type.Int());
            }
            case Ast.Exp.ArraySelect(Ast.Exp.T array, Ast.Exp.T index) -> {
                Id arrayId = Cfg.Exp.GetId(transExp(array));
                Id indexId = Cfg.Exp.GetId(transExp(index));
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.IntArraySelect(arrayId, indexId)));
                return new Cfg.Exp.Eid(id, new Cfg.Type.Int());
            }
            case Ast.Exp.NewIntArray(Ast.Exp.T size) -> {
                Id sizeId = Cfg.Exp.GetId(transExp(size));
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.IntArray(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.NewIntArray(sizeId)));
                return new Cfg.Exp.Eid(id, new Cfg.Type.IntArray());
            }
            case Ast.Exp.BopBool(Ast.Exp.T left, String op, Ast.Exp.T right) -> {
                Cfg.Exp.T leftExp = transExp(left);
                Cfg.Exp.T rightExp = transExp(right);
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Bop(op, List.of(Cfg.Exp.GetId(leftExp), Cfg.Exp.GetId(rightExp)), new Cfg.Type.Int())));
                return new Cfg.Exp.Eid(id, new Cfg.Type.Int());
            }
            case Ast.Exp.True() -> {
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Int(1)));
                return new Cfg.Exp.Eid(id, new Cfg.Type.Int());
            }
            case Ast.Exp.False() -> {
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Int(0)));
                return new Cfg.Exp.Eid(id, new Cfg.Type.Int());
            }
            case Ast.Exp.Length(Ast.Exp.T array) -> {
                Cfg.Exp.T arrayId = transExp(array);
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Length(Cfg.Exp.GetId(arrayId))));
                return new Cfg.Exp.Eid(id, new Cfg.Type.Int());
            }
            default -> {
                throw new Todo();
            }
        }
    }

    /////////////////////////////
    // translate a statement
    // this function does not return its result,
    // but saved the result into "currentBlock"
    // TODO: lab3, exercise 8.
    private void transStm(Ast.Stm.T stm) {
        switch(stm) {
            case Ast.Stm.Assign(Ast.AstId id, Ast.Exp.T exp) -> {
                Cfg.Exp.T expId = transExp(exp);
                Cfg.Stm.T cfgStm = new Cfg.Stm.Assign(id.freshId, expId);
                emit(cfgStm);
            }
            case Ast.Stm.AssignArray(Ast.AstId id, Ast.Exp.T index, Ast.Exp.T exp) -> {
                Cfg.Exp.T indexId = transExp(index);
                Cfg.Exp.T exp_ = transExp(exp);
                emit(new Cfg.Stm.AssignArray(id.freshId, indexId, exp_));
            }
            case Ast.Stm.Block(List<Ast.Stm.T> stms_) -> {
                for (Ast.Stm.T s : stms_) {
                    transStm(s);
                }
                //emitTransfer(new Cfg.Transfer.Jmp(this.currentJumpBlock));
            }
            case Ast.Stm.If(Ast.Exp.T cond, Ast.Stm.T thenn, Ast.Stm.T elsee) -> {
                Cfg.Exp.T condId = transExp(cond);
                emitTransfer(new Cfg.Transfer.If(Cfg.Exp.GetId(condId), this.currentThennBlock, this.currentElseeBlock));
            }
            case Ast.Stm.Print(Ast.Exp.T exp) -> {
                Cfg.Exp.T printObj = transExp(exp);
                Id id = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), id));
                emit(new Cfg.Stm.Assign(id, new Cfg.Exp.Print(Cfg.Exp.GetId(printObj))));
            }
            case Ast.Stm.While(Ast.Exp.T cond, Ast.Stm.T body) -> {
                Cfg.Exp.T condId = transExp(cond);
                emitTransfer(new Cfg.Transfer.If(Cfg.Exp.GetId(condId), this.currentThennBlock, this.currentElseeBlock));
            }
        }
    }

    // translate a method
    // TODO: lab3, exercise 8.
    private Cfg.Function.T transMethod(Ast.Method.T method) {
        switch (method) {
            case Ast.Method.Singleton(
                    Ast.Type.T retType,
                    Ast.AstId methodId,
                    List<Ast.Dec.T> formals,
                    List<Ast.Dec.T> locals,
                    List<Ast.Stm.T> stms,
                    Ast.Exp.T retExp
            ) -> {
                List<Cfg.Block.T> blocks = new ArrayList<>();
                this.currentBlock = new Cfg.Block.Singleton(new Label(), new LinkedList<>(), new LinkedList<>());
                this.newDecs = new LinkedList<>();
                // TODO: Trans Stm
                for (Ast.Stm.T stm : stms) {
                    switch(stm) {
                        case Ast.Stm.If(Ast.Exp.T cond, Ast.Stm.T thenn, Ast.Stm.T elsee) -> {
                            Label thennLabel = new Label();
                            Label elseeLabel = new Label();
                            Label joinLabel = new Label();

                            this.currentThennBlock = new Cfg.Block.Singleton(thennLabel, new LinkedList<>(), new LinkedList<>());
                            this.currentElseeBlock = new Cfg.Block.Singleton(elseeLabel, new LinkedList<>(), new LinkedList<>());
                            this.currentJumpBlock = new Cfg.Block.Singleton(joinLabel, new LinkedList<>(), new LinkedList<>());
                            transStm(stm); // add cond and transfer to currentBlock
                            blocks.add(this.currentBlock);
                            this.currentBlock = this.currentThennBlock;
                            transStm(thenn);
                            emitTransfer(new Cfg.Transfer.Jmp(this.currentJumpBlock));
                            blocks.add(this.currentBlock);
                            this.currentBlock = this.currentElseeBlock;
                            transStm(elsee);
                            emitTransfer(new Cfg.Transfer.Jmp(this.currentJumpBlock));
                            blocks.add(this.currentBlock);
                            this.currentBlock = this.currentJumpBlock;
                        }
                        case Ast.Stm.While(Ast.Exp.T cond, Ast.Stm.T body) -> {
                            Label condLabel = new Label();
                            Label bodyLabel = new Label();
                            Label jumpLabel = new Label();

                            this.currentThennBlock = new Cfg.Block.Singleton(bodyLabel, new LinkedList<>(), new LinkedList<>());
                            this.currentElseeBlock = new Cfg.Block.Singleton(jumpLabel, new LinkedList<>(), new LinkedList<>());

                            Cfg.Block.Singleton condBlock = new Cfg.Block.Singleton(condLabel, new LinkedList<>(), new LinkedList<>());
                            emitTransfer(new Cfg.Transfer.Jmp(condBlock));
                            blocks.add(this.currentBlock);
                            this.currentBlock = condBlock;
                            transStm(stm); // if(cond, body, jump);

                            blocks.add(this.currentBlock);

                            this.currentBlock = this.currentThennBlock;
                            transStm(body);
                            emitTransfer(new Cfg.Transfer.Jmp(condBlock));
                            blocks.add(this.currentBlock);

                            this.currentBlock = this.currentElseeBlock;
                        }
                        default -> {
                            transStm(stm);
                        }
                    }
                }

                Cfg.Exp.T retExpId = transExp(retExp);
                emitTransfer(new Cfg.Transfer.Ret(Cfg.Exp.GetId(retExpId)));
                blocks.add(this.currentBlock);

                List<Cfg.Dec.T> formalsDecls = new ArrayList<>();
                this.currentThis = Id.newName("this");
                formalsDecls.add(new Cfg.Dec.Singleton(new Cfg.Type.ClassType(this.currentClassId), this.currentThis));
                formalsDecls.addAll(transDecList(formals));

                List<Cfg.Dec.T> localDecls = transDecList(locals);
                localDecls.addAll(this.newDecs);

                this.currentFunction = new Cfg.Function.Singleton(transType(retType),
                        this.currentClassId, methodId.id, formalsDecls,
                        localDecls, blocks);
            }
        }
        return this.currentFunction;
    }

    // the prefixing algorithm
    // TODO: lab3, exercise 6.
    private Tuple.Two<Vector<Cfg.Dec.T>,
            Vector<Cfg.Vtable.Entry>> prefixOneClass(Ast.Class.T cls,
                                                     Tuple.Two<Vector<Cfg.Dec.T>,
                                                             Vector<Cfg.Vtable.Entry>> decsAndFunctions) {

        Vector<Cfg.Dec.T> decs = decsAndFunctions.first();
        Vector<Cfg.Dec.T> newDecs = new Vector<>();

        Vector<Cfg.Vtable.Entry> vtableEntries = new Vector<>();

        Vector<Cfg.Vtable.Entry> extendsFunctions = decsAndFunctions.second();

        switch (cls) {
            case Ast.Class.Singleton(Id classId, Id extends_, List<Ast.Dec.T> decs_, List<Ast.Method.T> methods, Tuple.One<Ast.Class.T> parent) -> {
                // Translate fields
                this.currentClassId = classId;

                for (Ast.Dec.T dec : decs_) {
                    Cfg.Dec.T cfgDec = transDec(dec);
                    newDecs.add(cfgDec);
                }

                // Translate methods
                for (Ast.Method.T method: methods) {
                    Cfg.Vtable.Entry entry = transVtables(method);
                    this.functions.add(transMethod(method));
                    vtableEntries.add(entry);
                }

                if (extends_ != null) {
                    for (Cfg.Vtable.Entry entry : extendsFunctions) {
                        if (entry.classId() == extends_) {
                            vtableEntries.add(entry);
                        }
                    }
                }

                //Vector<Cfg.Vtable.Entry> vtableEntriesCopy = new Vector<>(vtableEntries);
                this.vtables.add(new Cfg.Vtable.Singleton(this.currentClassId, vtableEntries));
                this.structs.add(new Cfg.Struct.Singleton(this.currentClassId, newDecs));
                decs.addAll(newDecs);
            }
        }


        return new Tuple.Two<>(decs, vtableEntries);
    }

    // build an inherit tree
    // TODO: lab3, exercise 5.
    private Tree<Ast.Class.T> buildInheritTree0(Ast.Program.T ast) {
        switch(ast) {
            case Ast.Program.Singleton(Ast.MainClass.T mainClass, List<Ast.Class.T> classes) -> {
                Tree<Ast.Class.T> tree = new Tree<>("inheritTree");

                Vector<Ast.Stm.T> mainStm = new Vector<>();
                mainStm.add(Ast.MainClass.getStm(mainClass));

                this.mainClassId = Ast.MainClass.getClassId(mainClass);
                this.mainFunctionId = Id.newName("main");
                Ast.Method.Singleton mainMethod = new Ast.Method.Singleton(
                        Ast.Type.getInt(),
                        new Ast.AstId(Id.newName("main")),
                        new Vector<>(),
                        new Vector<>(),
                        mainStm,
                        new Ast.Exp.Num(0)
                );
                // TODO: Root Object?
                Vector<Ast.Method.T> mainMethods = new Vector<>();
                mainMethods.add(mainMethod);

                tree.addRoot(new Ast.Class.Singleton(Ast.MainClass.getClassId(mainClass), null, new Vector<>(), mainMethods, null));
                for (Ast.Class.T c : classes) {
                    tree.addNode(c);
                    if (Ast.Class.getExtends(c) != null) {
                        Ast.Class.T parent = null;
                        for (Ast.Class.T c2 : classes) {
                            if (Ast.Class.getClassId(c2).equals(Ast.Class.getExtends(c))) {
                                parent = c2;
                                break;
                            }
                        }
                        if (parent == null) {
                            throw new util.Error("Parent class not found");
                        }
                        tree.addEdge(parent, c);
                    } else {
                        tree.addEdge(tree.root, tree.lookupNode(c));
                    }
                }
                return tree;
            }
        }
    }

    private Tree<Ast.Class.T> buildInheritTree(Ast.Program.T ast) {
        Trace<Ast.Program.T, Tree<Ast.Class.T>> trace =
                new Trace<>("cfg.Translate.buildInheritTree",
                        this::buildInheritTree0,
                        ast,
                        new ast.PrettyPrinter()::ppProgram,
                        (tree) -> {
                            // TODO: lab3, exercise 5.
                            // visualize the tree
                            if (Control.Dot.beingDotted("inheritTree")) {
                                tree.dot(c -> Ast.Class.getClassId((Ast.Class.T) c).toString());
                            }
                        });
        return trace.doit();
    }

    private Cfg.Program.T translate0(Ast.Program.T ast) {
        // if we are using the builtin AST, then do not generate
        // the CFG, but load the CFG directly from disk
        // and return it.
        if (Control.bultinAst != null) {
            Cfg.Program.T result;
            String serialFileName;
            try {
                File dir = new File("");
                serialFileName = dir.getCanonicalPath() + "/src/cfg/SumRec.java.cfg.ser";

                FileInputStream fileIn = new FileInputStream(serialFileName);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                result = (Cfg.Program.T) in.readObject();
                in.close();
                fileIn.close();
            } catch (Exception e) {
                throw new util.Error(e);
            }
            return result;
        }

        // Step #1: build the inheritance tree
        Tree<Ast.Class.T> tree = buildInheritTree(ast);
        // Step #2: perform prefixing via a level-order traversal
        // we also translate each method during this traversal.
        tree.levelOrder(tree.root,
                this::prefixOneClass,
                new Tuple.Two<>(new Vector<>(),
                        new Vector<>()));

        return new Cfg.Program.Singleton(this.mainClassId,
                this.mainFunctionId,
                this.vtables,
                this.structs,
                this.functions);
    }

    // given an abstract syntax tree, lower it down
    // to a corresponding control-flow graph.
    public Cfg.Program.T translate(Ast.Program.T ast) {
        Trace<Ast.Program.T, Cfg.Program.T> trace =
                new Trace<>("cfg.Translate.translate",
                        this::translate0,
                        ast,
                        new ast.PrettyPrinter()::ppProgram,
                        (x) -> {
                            x = new DeadCode().doitProgram(x); // DeadCode elimination
                            x = new ConstProp().doitProgram(x);
                            Cfg.Program.pp(x);
                            if (Control.Dot.beingDotted("cfg")) {
                                Cfg.Program.dot(x);
                            }
                        });
        return trace.doit();
    }
}
