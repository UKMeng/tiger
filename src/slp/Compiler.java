package slp;

import slp.Slp.Exp;
import slp.Slp.Exp.Eseq;
import slp.Slp.Exp.Id;
import slp.Slp.Exp.Num;
import slp.Slp.Exp.Op;
import slp.Slp.Stm;
import util.Todo;

import java.io.FileWriter;
import java.util.HashSet;
import java.util.List;

// a simple compiler for SLP, to x64.
public class Compiler {
    // ////////////////////////////////////////
    // whether to keep the generated assembly file.
    boolean keepAsm = false;
    HashSet<String> ids;
    StringBuffer buf;

    private void emit(String s) {
        buf.append(s);
    }

    private void compileExp(Exp.T exp) {
        switch (exp) {
            case Id(String x) -> emit(STR."\tmovq\t\{x}, %rax\n");
            case Num(int n) -> emit(STR."\tmovq\t$\{n}, %rax\n");
            case Op(
                    Exp.T left,
                    String op,
                    Exp.T right
            ) -> {
                compileExp(left);
                emit("\tpushq\t%rax\n");
                compileExp(right);
                emit("\tpopq\t%rdx\n");
                switch (op) {
                    case "+" -> emit("\taddq\t%rdx, %rax\n");
                    case "*" -> emit("\timulq\t%rdx\n");
                    case "-" -> emit("\tsubq\t%rdx, %rax\n");
                    case "/" -> {
                        emit("\tmovq\t%rax, %rbx\n");
                        emit("\tmovq\t%rdx, %rax\n");
                        emit("\tcqo\n");
                        emit("\tidivq\t%rbx\n");
                    }
                    default -> throw new Todo(op);
                }
            }
            case Eseq(
                    Stm.T s,
                    Exp.T e
            ) -> {
                compileStm0(s);
                compileExp(e);
            }
        }
    }

    // to compile a statement
    private void compileStm0(Stm.T s) {
        switch (s) {
            case Stm.Compound(
                    Stm.T s1,
                    Stm.T s2
            ) -> {
                compileStm0(s1);
                compileStm0(s2);
            }
            case Stm.Assign(
                    String x,
                    Exp.T e
            ) -> {
                ids.add(x);
                compileExp(e);
                emit(STR."\tmovq\t%rax, \{x}\n");
            }
            case Stm.Print(List<Exp.T> exps) -> {
                exps.forEach(e -> {
                    compileExp(e);
                    emit("""
                                movq\t%rax, %rsi
                                movq\t$slp_format, %rdi
                                callq\tprintf
                            """);
                });
                emit("""
                            movq\t$new_line, %rdi
                            callq\tprintf
                        """);
            }
        }
    }

    // ////////////////////////////////////////
    public void compileStm(Stm.T prog) throws Exception {
        // we always reset these two variables, so that this
        // method is re-entrant.
        this.ids = new HashSet<>();
        this.buf = new StringBuffer();

        // do the real work
        compileStm0(prog);

        FileWriter fileWriter = new FileWriter("slp_gen.s");
        fileWriter.write(
                """
                        // Automatically generated by the Tiger compiler, do NOT edit.
                        // the data section:
                            .data
                        slp_format:
                            .string "%d "
                        new_line:
                            .string "\\n"
                        """);
        for (String s : this.ids) {
            fileWriter.write(STR."\{s}:");
            fileWriter.write("\t.long 0\n");
        }
        fileWriter.write(
                """
                            .text
                            .globl main
                        main:
                            pushq\t%rbp
                            movq\t%rsp, %rbp
                        """);
        fileWriter.write(buf.toString());
        fileWriter.write("\tleave\n\tret\n\n");
        fileWriter.close();

        String[] cmdStr = {"gcc", "-no-pie", "slp_gen.s"};
        Process child = Runtime.getRuntime().exec(cmdStr, null, null);
        child.waitFor();
        if (!keepAsm) {
            String[] cmdStr2 = {"rm", "-rf", "slp_gen.s"};
            Runtime.getRuntime().exec(cmdStr2, null, null);
        }
    }
}
