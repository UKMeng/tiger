package checker;

import ast.Ast;
import ast.Ast.Class;
import ast.Ast.*;
import ast.PrettyPrinter;
import control.Control;
import util.*;

import java.util.List;
import java.util.Objects;

public class Checker {
    // symbol table for all classes
    private final ClassTable classTable;
    // symbol table for each method
    private MethodTable methodTable;
    // the class name being checked
    private Id currentClass;

    public Checker() {
        this.classTable = new ClassTable();
        this.methodTable = new MethodTable();
        this.currentClass = null;
    }

    private void error(String s) {
        System.out.println(STR."Error: type mismatch: \{s}");
    }

    private void error(String s, boolean recovery) {
        System.out.println(STR."Error: type mismatch: \{s}");
        if(!recovery) System.exit(1);
    }

    private void error(String s, Type.T expected, Type.T got) {
        System.out.println(STR."Error: type mismatch: \{s}");
        System.out.print(STR."Expected: ");
        Type.output(expected);
        System.out.print(STR."Got: ");
        Type.output(got);
    }

    private void error(String s, Type.T expected, Type.T got, boolean recovery) {
        System.out.println(STR."Error: type mismatch: \{s}");
        System.out.print(STR."Expected: ");
        Type.output(expected);
        System.out.print(STR."Got: ");
        Type.output(got);
        if(!recovery) System.exit(1);
    }

    // /////////////////////////////////////////////////////
    // ast-id
    private Type.T checkAstId(AstId aid) {
        boolean isClassField = false;
        // first search in current method table
        Tuple.Two<Ast.Type.T, Id> resultId = this.methodTable.get(aid.id);
        // not a local or formal
        if (resultId == null) {
            isClassField = true;
            resultId = this.classTable.getField(this.currentClass, aid.id);
        }
        if (resultId == null) {
            error(STR."id not found: \{aid.id}", false);
        }
        assert resultId != null;
        // set up the fresh
        aid.freshId = resultId.second();
        aid.isClassField = isClassField;

        if (!isClassField) {
            this.methodTable.useVar(resultId.second());
        } else {
            this.classTable.useField(this.currentClass, aid.id);
        }

        return resultId.first();
    }

    // /////////////////////////////////////////////////////
    // expressions
    // type check an expression will return its type.
    private Type.T checkExp(Exp.T e) {
        switch (e) {
            case Exp.Call(
                    Exp.T theObject,
                    AstId methodId,
                    List<Exp.T> args,
                    Tuple.One<Id> calleeTy,
                    Tuple.One<Type.T> retTy
            ) -> {
                var typeOfTheObject = checkExp(theObject);
                Id calleeClassId = null;
                if (Objects.requireNonNull(typeOfTheObject) instanceof Type.ClassType(Id calleeClassId_)) {
                    calleeClassId = calleeClassId_;
                    // put the return type onto the AST
                    calleeTy.set(calleeClassId);
                }
                var resultMethodId = this.classTable.getMethod(calleeClassId, methodId.id);
                if (resultMethodId == null) {
                    error(STR."method not found: \{calleeClassId} . \{methodId}", false);
                }
                var resultArgs = args.stream().map(this::checkExp).toList();
                assert resultMethodId != null;
                methodId.freshId = resultMethodId.second();
                Ast.Type.T retType = resultMethodId.first().retType();
                // put the return type onto the AST
                retTy.set(retType);
                return retType;
            }
            case Exp.NewObject(Id classId) -> {
                var classBinding = this.classTable.getClass_(classId);
                return Type.getClassType(classId);
            }
            case Exp.Num(int n) -> {
                return Type.getInt();
            }
            case Exp.Bop(
                    Exp.T left,
                    String bop,
                    Exp.T right
            ) -> {
                var resultLeft = checkExp(left);
                var resultRight = checkExp(right);

                switch (bop) {
                    case "+", "-", "*" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt())) {
                            error("Bop Left Var Type", Type.getInt(), resultLeft);
                        }
                        if (Type.nonEquals(resultRight, Type.getInt())) {
                            error("Bop Right Var Type", Type.getInt(), resultRight);
                        }
                        return Type.getInt();
                    }
                    case "<" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt())) {
                            error("Bop Left Var Type", Type.getInt(), resultLeft);
                        }
                        if (Type.nonEquals(resultRight, Type.getInt())) {
                            error("Bop Right Var Type", Type.getInt(), resultRight);
                        }
                        return Type.getBool();
                    }
                    default -> throw new Todo();
                }
            }
            case Exp.BopBool(
                    Exp.T left,
                    String bop,
                    Exp.T right
            ) -> {
                var resultLeft = checkExp(left);
                var resultRight = checkExp(right);
                if (Type.nonEquals(resultLeft, Type.getBool())) {
                    error("BopBool Left Var Type", Type.getBool(), resultLeft);
                }
                if (Type.nonEquals(resultRight, Type.getBool())) {
                    error("BopBool Right Var Type", Type.getBool(), resultRight);
                }
                return Type.getBool();
            }
            case Exp.ExpId(AstId aid) -> {
                return checkAstId(aid);
            }
            case Exp.This() -> {
                return Type.getClassType(this.currentClass);
            }
            case Exp.True(), Exp.False() -> {
                return Type.getBool();
            }
            case Exp.Length(Exp.T array) -> {
                var resultArray = checkExp(array);
                if (Type.nonEquals(resultArray, Type.getIntArray())) {
                    error("Length Var Type", Type.getIntArray(), resultArray);
                }
                return Type.getInt();
            }
            case Exp.NewIntArray(Exp.T exp) -> {
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultExp, Type.getInt())) {
                    error("NewIntArray Var Type", Type.getInt(), resultExp);
                }
                return Type.getIntArray();
            }
            case Exp.Uop(
                    String uop,
                    Exp.T exp
            ) -> {
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultExp, Type.getBool())) {
                    error("Uop Var Type", Type.getBool(), resultExp);
                }
                return Type.getBool();
            }
            case Exp.ArraySelect(
                    Exp.T array,
                    Exp.T index
            ) -> {
                var resultArray = checkExp(array);
                var resultIndex = checkExp(index);
                if (Type.nonEquals(resultArray, Type.getIntArray())) {
                    error("ArraySelect Array Type", Type.getIntArray(), resultArray);
                }
                if (Type.nonEquals(resultIndex, Type.getInt())) {
                    error("ArraySelect Index Type", Type.getInt(), resultIndex);
                }
                return Type.getInt();
            }
            default -> throw new Todo();
        }
    }

    // type check a statement
    private void checkStm(Stm.T s) {
        switch (s) {
            case Stm.If(
                    Exp.T cond,
                    Stm.T then_,
                    Stm.T else_
            ) -> {
                var resultCond = checkExp(cond);
                if (Type.nonEquals(resultCond, Type.getBool())) {
                    error("IF Condition Type", Type.getBool(), resultCond);
                }
                checkStm(then_);
                checkStm(else_);
            }
            case Stm.Print(Exp.T exp) -> {
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultExp, Type.getInt())) {
                    error("Print Var Type", Type.getInt(), resultExp);
                }
            }
            case Stm.Assign(
                    AstId id,
                    Exp.T exp
            ) -> {
                // first lookup in the method table
                var resultAstId = checkAstId(id);
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultAstId, resultExp)) {
                    error("Assign Var Type", resultAstId, resultExp);
                }
            }
            case Stm.While(
                    Exp.T cond,
                    Stm.T body
            ) -> {
                var resultCond = checkExp(cond);
                if (Type.nonEquals(resultCond, Type.getBool())) {
                    error("While Condition Type", Type.getBool(), resultCond);
                }
                checkStm(body);
            }
            case Stm.Block(List<Stm.T> stms) -> {
                stms.forEach(this::checkStm);
            }
            case Stm.AssignArray(
                    AstId id,
                    Exp.T index,
                    Exp.T exp
            ) -> {
                var resultId = checkAstId(id);
                var resultIndex = checkExp(index);
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultId, Type.getIntArray())) {
                    error("AssignArray Id Type", Type.getIntArray(), resultId);
                }
                if (Type.nonEquals(resultIndex, Type.getInt())) {
                    error("AssignArray Index Type", Type.getInt(), resultIndex);
                }
                if (Type.nonEquals(resultExp, Type.getInt())) {
                    error("AssignArray Exp Type", Type.getInt(), resultExp);
                }
            }
            default -> throw new Todo();
        }
    }

    // check type
    public void checkType(Type.T t) {
        throw new Todo();
    }

    // dec
    public void checkDec(Dec.T d) {
        throw new Todo();
    }

    // method type
    private List<Type.T> genMethodArgType(List<Dec.T> decs) {
        return decs.stream().map(Dec::getType).toList();
    }

    // method
    private void checkMethod(Method.T mtd) {
        Method.Singleton m = (Method.Singleton) mtd;
        // construct the method table
        this.methodTable = new MethodTable();
        this.methodTable.putFormalLocal(m.formals(), m.locals());
        m.stms().forEach(this::checkStm);
        var resultExp = checkExp(m.retExp());
        if (Type.nonEquals(resultExp, m.retType())) {
            error("Return Type", m.retType(), resultExp);
        }
        this.methodTable.checkUnusedVar(m.methodId());
        this.methodTable.dump();
    }

    // class
    private void checkClass(Class.T c) {
        Class.Singleton cls = (Class.Singleton) c;
        this.currentClass = cls.classId();
        Id extends_ = cls.extends_();
        if (extends_ != null) {
            ClassTable.Binding binding = this.classTable.getClass_(extends_);
            cls.parent().set(binding.self());
        }
        cls.methods().forEach(this::checkMethod);
    }

    // main class
    private void checkMainClass(MainClass.T c) {
        MainClass.Singleton mainClass = (MainClass.Singleton) c;
        this.currentClass = mainClass.classId();
        // "main" method has an argument "arg" of type "String[]", but
        // MiniJava programs do not use it.
        // So we can safely create a fake one with integer type.
        this.methodTable = new MethodTable();
        this.methodTable.putFormalLocal(List.of(new Dec.Singleton(Type.getInt(), mainClass.arg())),
                List.of()); // no local variables
        checkStm(mainClass.stm());
    }

    // ////////////////////////////////////////////////////////
    // step 1: create class table for Main class
    private void buildMainClass(MainClass.T main) {
        // we do not put Main class into the class table.
        // so that no other class can inherit from it.
        // MainClass.Singleton mc = (MainClass.Singleton) main;
        //this.classTable.putClass(mc.classId(), null);
    }

    // create class table for each normal class
    private void buildClass(Class.T cls) {
        Class.Singleton c = (Class.Singleton) cls;
        this.classTable.putClass(c.classId(), c.extends_(), cls);

        // add all instance variables into the class table
        for (Dec.T dec : c.decs()) {
            Dec.Singleton d = (Dec.Singleton) dec;
            this.classTable.putField(c.classId(),
                    d.aid(),
                    d.type());
        }
        // add all methods into the class table
        for (Method.T method : c.methods()) {
            Method.Singleton m = (Method.Singleton) method;
            this.classTable.putMethod(c.classId(),
                    m.methodId(),
                    // for now, do not worry to check
                    // method formals, as we will check
                    // this during method table construction.
                    new ClassTable.MethodType(m.retType(),
                            genMethodArgType(m.formals())));
        }
    }

    private Program.T buildTable0(Program.T p) {
        Program.Singleton prog = (Program.Singleton) p;
        // ////////////////////////////////////////////////
        // a class table maps a class name to its class binding:
        // classTable: className -> Binding{extends_, fields, methods}
        buildMainClass(prog.mainClass());
        prog.classes().forEach(this::buildClass);
        return p;
    }

    private Program.T buildTable(Program.T p) {
        Trace<Program.T, Program.T> trace =
                new Trace<>("checker.Checker.buildTable",
                        this::buildTable0,
                        p,
                        (_) -> {
                            System.out.println("build class table:");
                        },
                        (_) -> {
                            this.classTable.dump();
                        });
        return trace.doit();
    }

    private Program.T checkIt0(Program.T p) {
        Program.Singleton prog = (Program.Singleton) p;
        checkMainClass(prog.mainClass());
        prog.classes().forEach(this::checkClass);
        this.classTable.checkUnusedField();
        return p;
    }

    private Program.T checkIt(Program.T p) {
        Trace<Program.T, Program.T> trace =
                new Trace<>("checker.Checker.checkClass",
                        this::checkIt0,
                        p,
                        (_) -> {
                            System.out.println("check class:");
                        },
                        (_) -> {
                            this.classTable.dump();
                        });
        return trace.doit();
    }

    // to check a program
    private Program.T checkProgram(Program.T p) {
        // pass 1: build the class table
        Pass<Program.T, Program.T> buildTablePass =
                new Pass<>("build class table",
                        this::buildTable,
                        p,
                        Control.Verbose.L1);
        p = buildTablePass.apply();
        // ////////////////////////////////////////////////
        // pass 2: check each class in turn, under the class table
        // built above.
        Pass<Program.T, Program.T> checkPass =
                new Pass<>("check class",
                        this::checkIt,
                        p,
                        Control.Verbose.L1);
        p = checkPass.apply();
        return p;
    }

    public Ast.Program.T check(Program.T ast) {
        PrettyPrinter pp = new PrettyPrinter();

        var traceCheckProgram = new Trace<>(
                "checker.Checker.check",
                this::checkProgram,
                ast,
                pp::ppProgram,
                pp::ppProgram);
        return traceCheckProgram.doit();
    }
}


