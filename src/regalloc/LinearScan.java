package regalloc;

import codegen.X64;
import cfg.Set;
import control.Control;
import util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.function.BiFunction;

import static codegen.X64.Block.getLabel;

// A linear scan register allocator.
public class LinearScan {

    private HashMap<Label, Tuple.Two<Set<Id>, Set<Id>>> liveInOutMap;
    private HashMap<Label, Tuple.Two<Set<Id>, Set<Id>>> liveInterval;
    private Stack<Id> regStack;
    private HashMap<Id, Reg.T> varToRegMap;
    private int stackOffset;

    public LinearScan() {
        liveInOutMap = new HashMap<>();
        liveInterval = new HashMap<>();
        regStack = new Stack<>();
        varToRegMap = new HashMap<>();
        stackOffset = 0;
    }

    public static class Reg {
        public sealed interface T
                permits Register, Stack {

        }

        public record Register(Id id) implements T {

        }

        public record Stack(Id id, int offset) implements  T {

        }
    }

    private Reg.T allocReg(Id varId) {
        if (!varToRegMap.containsKey(varId)) {
            if (!regStack.isEmpty()) {
                Id regId = regStack.peek();
                regStack.pop();
                Reg.Register ret = new Reg.Register(regId);
                varToRegMap.put(varId, ret);
                return ret;
            } else {
                // spill
                Id rbpReg = X64.Register.allRegs.get(6);
                stackOffset -= X64.WordSize.bytesOfWord;
                Reg.Stack ret = new Reg.Stack(rbpReg, stackOffset);
                varToRegMap.put(varId, ret);
                return ret;
            }
        } else {
            return varToRegMap.get(varId);
        }
    }

    private void releaseReg(Id varId) {
        if (varToRegMap.containsKey(varId)) {
            switch (varToRegMap.get(varId)) {
                case Reg.Register(Id id) -> {
                    regStack.push(id);
                }
                case Reg.Stack(Id id, int offset) -> {
                }
            }
            varToRegMap.remove(varId);
        }
    }


    private X64.Block.T allocBlock(X64.Block.T block, TempMap tempMap, boolean firstBlock, Frame frame) {
        Id rbpReg = X64.Register.allRegs.get(6);
        Id rspReg = X64.Register.allRegs.get(7);
        HashMap<Id, X64.Type.T> typeMap = new HashMap<>();
        switch (block) {
            case X64.Block.Singleton(Label label, List<X64.Instr.T> instrs, List<X64.Transfer.T> transfer) -> {
                List<X64.Instr.T> newInstrs = new ArrayList<>();
                Set<Id> varStart = liveInterval.get(label).first();
                Set<Id> varEnd = liveInterval.get(label).second();

                if (firstBlock) {
                    // Prologue
                    // Save base pointer register
                    newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Store,
                            (uarg, darg) -> STR."pushq\t%rbp",
                            List.of(new X64.VirtualReg.Reg(rbpReg, new X64.Type.Int())),
                            List.of()));


                    // Save all Callee-Saved Registers
                    for (Id reg : X64.Register.calleeSavedRegs) {
                        List<X64.VirtualReg.T> uses = List.of(new X64.VirtualReg.Reg(reg, new X64.Type.Int()));
                        List<X64.VirtualReg.T> defs = List.of();
                        newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Store,
                                (uarg, darg) -> STR."pushq\t\{uarg.getFirst()}",
                                uses,
                                defs));
                    }

                    // Move stack pointer register to base pointer register
                    newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Move,
                            (uarg, darg) -> STR."movq\t%rsp, %rbp",
                            List.of(new X64.VirtualReg.Reg(rspReg, new X64.Type.Int())),
                            List.of(new X64.VirtualReg.Reg(rbpReg, new X64.Type.Int()))));

                    // Allocate space for local variables
                    // Suppose `localsSize` is the total size of local variables
                    newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Bop,
                            (uarg, darg) -> STR."subq\t$" + 2 * frame.size() + ", %rsp",
                            List.of(new X64.VirtualReg.Reg(rspReg, new X64.Type.Int())),
                            List.of(new X64.VirtualReg.Reg(rspReg, new X64.Type.Int()))));
                }

//                // alloc var start from this block (or already allocated)
//                for (Id varId : varStart.getSet()) {
//                    if (!varToRegMap.containsKey(varId)) {
//                        Id regId = regStack.peek();
//                        regStack.pop();
//                        varToRegMap.put(varId, regId);
//                    }
//                }


                for (X64.Instr.T instr : instrs) {
                    switch (instr) {
                        case X64.Instr.Singleton(X64.Instr.Kind kind,
                                                 BiFunction<List<X64.VirtualReg.T>, List<X64.VirtualReg.T>, String> ppFormat,
                                                 List<X64.VirtualReg.T> uses,
                                                 List<X64.VirtualReg.T> defs) -> {
                            HashMap<Id, Id> regMap = new HashMap<>();
                            List<X64.VirtualReg.T> newUses = new ArrayList<>();
                            List<X64.VirtualReg.T> newDefs = new ArrayList<>();
                            int regIndex = 0;
                            for (X64.VirtualReg.T var: uses) {
                                switch (var) {
                                    case X64.VirtualReg.Vid(Id id, X64.Type.T ty) -> {
                                        typeMap.put(id, ty);
                                        int offset = tempMap.getOffset(id);
                                        Reg.T reg = allocReg(id);
                                        switch (reg) {
                                            case Reg.Register(Id regId) -> {
                                                if (varStart.isInSet(id)) {
                                                    List<X64.VirtualReg.T> uses_ = List.of(new X64.VirtualReg.Reg(rbpReg, ty));
                                                    List<X64.VirtualReg.T> defs_ = List.of(new X64.VirtualReg.Reg(regId, ty));
                                                    newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Load,
                                                            (uarg, darg) ->
                                                                    STR."movq\t\{offset}(\{uarg.getFirst()}), \{darg.getFirst()}",
                                                            uses_,
                                                            defs_));
                                                }
                                                newUses.add(new X64.VirtualReg.Reg(regId, ty));
                                            }
                                            case Reg.Stack(Id regId, int sOffset) -> {
                                                if (varStart.isInSet(id)) {
                                                    List<X64.VirtualReg.T> uses_ = List.of(new X64.VirtualReg.Reg(rbpReg, ty));
                                                    List<X64.VirtualReg.T> defs_ = List.of(new X64.VirtualReg.Reg(regId, ty));
                                                    newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Load,
                                                            (uarg, darg) ->
                                                                    STR."movq\t\{offset}(\{uarg.getFirst()}), \{sOffset}(\{darg.getFirst()})",
                                                            uses_,
                                                            defs_));
                                                }
                                                newUses.add(new X64.VirtualReg.Reg(regId, ty));
                                            }
                                        }

                                    }
                                    case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
                                        newUses.add(var);
                                    }
                                }
                            }
                            for (X64.VirtualReg.T var: defs) {
                                switch (var) {
                                    case X64.VirtualReg.Vid(Id id, X64.Type.T ty) -> {
                                        typeMap.put(id, ty);
                                        Reg.T reg = allocReg(id);
                                        switch (reg) {
                                            case Reg.Register(Id regId) -> {
                                                newDefs.add(new X64.VirtualReg.Reg(regId, ty));
                                            }
                                            case Reg.Stack(Id regId, int sOffset) -> {
                                                newDefs.add(new X64.VirtualReg.Reg(regId, ty));
                                            }
                                        }

                                    }
                                    case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
                                        newDefs.add(var);
                                    }
                                }
                            }
                            newInstrs.add(new X64.Instr.Singleton(kind, ppFormat, newUses, newDefs));
                        }
                    }
                }

                // release reg when var end in this block
                for (Id varId : varEnd.getSet()) {
                    Reg.T reg = varToRegMap.get(varId);
                    int offset = tempMap.getOffset(varId);
                    switch (reg) {
                        case Reg.Register(Id id) -> {
                            List<X64.VirtualReg.T> uses_ = List.of(new X64.VirtualReg.Reg(id, typeMap.get(varId)));
                            List<X64.VirtualReg.T> defs_ = List.of(new X64.VirtualReg.Reg(rbpReg, typeMap.get(varId)));
                            newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Store,
                                    (uarg, darg) ->
                                            STR."movq\t\{uarg.getFirst()}, \{offset}(\{darg.getFirst()})",
                                    uses_,
                                    defs_));
                        }
                        case Reg.Stack(Id id, int sOffset) -> {
                            List<X64.VirtualReg.T> uses_ = List.of(new X64.VirtualReg.Reg(id, typeMap.get(varId)));
                            List<X64.VirtualReg.T> defs_ = List.of(new X64.VirtualReg.Reg(rbpReg, typeMap.get(varId)));
                            newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Store,
                                    (uarg, darg) ->
                                            STR."movq\t\{sOffset}(\{uarg.getFirst()}), \{offset}(\{darg.getFirst()})",
                                    uses_,
                                    defs_));
                        }
                    }

                    releaseReg(varId);
                }


                for (X64.Transfer.T t : transfer) {
                    switch (t) {
                        case X64.Transfer.Ret() -> {
                            // Epilogue
                            // Move rsp to rbp
                            newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Move,
                                    (uarg, darg) -> STR."movq\t%rbp, %rsp",
                                    List.of(new X64.VirtualReg.Reg(rbpReg, new X64.Type.Int())),
                                    List.of(new X64.VirtualReg.Reg(rspReg, new X64.Type.Int()))));

                            // Restore callee-saved registers
                            for (int i = X64.Register.calleeSavedRegs.size() - 1; i >= 0; i--) {
                                Id reg = X64.Register.calleeSavedRegs.get(i);
                                List<X64.VirtualReg.T> uses = List.of();
                                List<X64.VirtualReg.T> defs = List.of(new X64.VirtualReg.Reg(reg, new X64.Type.Int()));
                                newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Load,
                                        (uarg, darg) -> STR."popq\t\{darg.getFirst()}",
                                        uses,
                                        defs));
                            }
                            // Restore base pointer register
                            newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Load,
                                    (uarg, darg) -> STR."popq\t%rbp",
                                    List.of(),
                                    List.of(new X64.VirtualReg.Reg(rbpReg, new X64.Type.Int()))));
                        }
                        default -> {}
                    }
                }

                return new X64.Block.Singleton(label, newInstrs, transfer);
            }
        }
    }

    private void livenessAnalysis(X64.Function.T function) {
        switch (function) {
            case X64.Function.Singleton(X64.Type.T retType, Id classId, Id methodId, List<X64.Dec.T> formals, List<X64.Dec.T> locals, List<X64.Block.T> blocks) -> {
                for (int i = blocks.size() - 1; i >= 0; i--) {
                    X64.Block.T block = blocks.get(i);
                    switch (block) {
                        case X64.Block.Singleton(Label label, List<X64.Instr.T> instrs, List<X64.Transfer.T> transfer) -> {
                            Set<Id> liveIn = new Set<>();
                            Set<Id> liveOut = new Set<>();
                            Set<Id> blockLiveOut = new Set<>();
                            for (X64.Transfer.T t : transfer) {
                                switch (t) {
                                    case X64.Transfer.Ret() -> {

                                    }
                                    case X64.Transfer.If(String instr, X64.Block.T trueBlock, X64.Block.T falseBlock) -> {
                                        Set<Id> temp1 = liveInOutMap.get(getLabel(trueBlock)).first();
                                        Set<Id> temp2 = liveInOutMap.get(getLabel(falseBlock)).first();
                                        liveOut.union(temp1);
                                        liveOut.union(temp2);
                                    }
                                    case X64.Transfer.Jmp(X64.Block.T target) -> {
                                        Set<Id> temp = liveInOutMap.get(getLabel(target)).first();
                                        liveOut.union(temp);
                                    }
                                }
                            }
                            blockLiveOut = liveOut.clone();
                            for (int j = instrs.size() - 1; j >= 0; j--) {
                                Set<Id> usesSet = new Set<>();
                                Set<Id> defsSet = new Set<>();
                                X64.Instr.T instr = instrs.get(j);
                                if (i != instrs.size() - 1) liveOut = liveIn.clone();
                                switch (instr) {
                                    case X64.Instr.Singleton(X64.Instr.Kind kind,
                                                             BiFunction<List<X64.VirtualReg.T>, List<X64.VirtualReg.T>, String> ppFormat,
                                                             List<X64.VirtualReg.T> uses,
                                                             List<X64.VirtualReg.T> defs) -> {
                                        for (X64.VirtualReg.T use: uses) {
                                            switch (use) {
                                                case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
//                                                    usesSet.add(r);
                                                }
                                                case X64.VirtualReg.Vid(Id id, X64.Type.T ty) -> {
                                                    usesSet.add(id);
                                                }
                                            }
                                        }
                                        for (X64.VirtualReg.T def: defs) {
                                            switch(def) {
                                                case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
//                                                    defsSet.add(r);
                                                }
                                                case X64.VirtualReg.Vid(Id id, X64.Type.T ty) -> {
                                                    defsSet.add(id);
                                                }
                                            }
                                        }
                                        liveIn = usesSet.clone();
                                        Set<Id> temp = liveOut.clone();
                                        temp.sub(defsSet);
                                        liveIn.union(temp);
                                    }
                                }
                            }

                            if (i == 0) liveIn = new Set<>();
                            liveInOutMap.put(label, new Tuple.Two<>(liveIn, blockLiveOut));
                        }
                    }

                }
            }
        }
    }

    private void visit(X64.Block.T node, List<X64.Block.T> newBlocks, HashMap<Label, Boolean> permanentMark, HashMap<Label, Boolean> temporaryMark) {
        if (permanentMark.containsKey(getLabel(node)) && permanentMark.get(getLabel(node))) return;
        if (temporaryMark.containsKey(getLabel(node)) && temporaryMark.get(getLabel(node))) return;
        temporaryMark.put(getLabel(node), true);
        switch (node) {
            case X64.Block.Singleton(Label label, List<X64.Instr.T> instrs, List<X64.Transfer.T> transfer) -> {
                for (X64.Transfer.T t : transfer) {
                    switch (t) {
                        case X64.Transfer.Ret() -> {

                        }
                        case X64.Transfer.If(String instr, X64.Block.T trueBlock, X64.Block.T falseBlock) -> {
                            visit(trueBlock, newBlocks, permanentMark, temporaryMark);
                            visit(falseBlock, newBlocks, permanentMark, temporaryMark);
                        }
                        case X64.Transfer.Jmp(X64.Block.T target) -> {
                            visit(target, newBlocks, permanentMark, temporaryMark);
                        }
                    }
                }
            }
        }
        temporaryMark.put(getLabel(node), false);
        permanentMark.put(getLabel(node), true);
        newBlocks.addFirst(node);
    }

    private X64.Function.T topologicalSort(X64.Function.T function) {
        switch (function) {
            case X64.Function.Singleton(X64.Type.T retType, Id classId, Id methodId, List<X64.Dec.T> formals, List<X64.Dec.T> locals, List<X64.Block.T> blocks) -> {
                List<X64.Block.T> newBlocks = new ArrayList<>();
                HashMap<Label, Boolean> permanentMark = new HashMap<>();
                HashMap<Label, Boolean> temporaryMark = new HashMap<>();

                boolean flag = true;
                while (flag) {
                    flag = false;
                    for (X64.Block.T block : blocks) {
                        Label label = getLabel(block);
                        if (!permanentMark.containsKey(label)) {
                            permanentMark.put(label, false);
                        }
                        if (!permanentMark.get(label)) {
                            flag = true;
                            visit(block, newBlocks, permanentMark, temporaryMark);
                        }
                    }
                }

                return new X64.Function.Singleton(retType, classId, methodId, formals, locals, newBlocks);
            }
        }
    }

    private Set<Id> getBlockVarId(X64.Block.T block) {
        Set<Id> ret = new Set<>();
        switch (block) {
            case X64.Block.Singleton(Label label, List<X64.Instr.T> instrs, List<X64.Transfer.T> transfer) -> {
                for (X64.Instr.T instr : instrs) {
                    switch (instr) {
                        case X64.Instr.Singleton(X64.Instr.Kind kind,
                                                 BiFunction<List<X64.VirtualReg.T>, List<X64.VirtualReg.T>, String> ppFormat,
                                                 List<X64.VirtualReg.T> uses,
                                                 List<X64.VirtualReg.T> defs) -> {
                            for (X64.VirtualReg.T use: uses) {
                                switch (use) {
                                    case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
                                    }
                                    case X64.VirtualReg.Vid(Id id, X64.Type.T ty) -> {
                                        ret.add(id);
                                    }
                                }
                            }
                            for (X64.VirtualReg.T def: defs) {
                                switch(def) {
                                    case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
                                    }
                                    case X64.VirtualReg.Vid(Id id, X64.Type.T ty) -> {
                                        ret.add(id);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return ret;
    }

    private void getLiveInterval(X64.Function.T function) {
        switch (function) {
            case X64.Function.Singleton(X64.Type.T retType, Id classId, Id methodId, List<X64.Dec.T> formals, List<X64.Dec.T> locals, List<X64.Block.T> blocks) -> {
                // debug: print liveInOutMap
//                for (X64.Block.T block : blocks) {
//                    Label label = getLabel(block);
//                    System.out.println(STR."\{label.toString()}");
//                    Set<Id> liveIn = liveInOutMap.get(label).first();
//                    Set<Id> liveOut = liveInOutMap.get(label).second();
//                    System.out.println("Live In");
//                    for (Id id: liveIn.getSet()) {
//                        System.out.println(STR."\{id.toString()}");
//                    }
//                    System.out.println("Live Out");
//                    for (Id id: liveOut.getSet()) {
//                        System.out.println(STR."\{id.toString()}");
//                    }
//                    System.out.println();
//                }
                for (X64.Block.T block : blocks) {
                    Label label = getLabel(block);
                    Set<Id> liveIn = liveInOutMap.get(label).first();
                    Set<Id> liveOut = liveInOutMap.get(label).second();
                    Set<Id> temp = liveIn.clone();
                    temp.intersection(liveOut);
                    Set<Id> start = liveOut.clone();
                    Set<Id> end = liveIn.clone();
                    start.sub(temp);
                    end.sub(temp);

                    Set<Id> blockVarId = getBlockVarId(block);
                    blockVarId.sub(liveIn);
                    blockVarId.sub(liveOut);
                    start.union(blockVarId);
                    end.union(blockVarId);

                    liveInterval.put(label, new Tuple.Two<>(start, end));
                }
            }
        }
    }

    private X64.Function.T allocFunction(X64.Function.T function) {
        TempMap tempMap = new TempMap();

        function = topologicalSort(function);

        liveInOutMap.clear();
        livenessAnalysis(function);

        liveInterval.clear();
        getLiveInterval(function);

        regStack.clear();
        varToRegMap.clear();
        for (Id reg : X64.Register.calleeSavedRegs) regStack.push(reg);

        stackOffset = 0;
        switch (function) {
            case X64.Function.Singleton(X64.Type.T retType, Id classId, Id methodId, List<X64.Dec.T> formals, List<X64.Dec.T> locals, List<X64.Block.T> blocks) -> {
                Frame frame = new Frame(STR."\{classId.toString()} + . + \{methodId.toString()}");
                for (X64.Dec.T formal : formals) {
                    switch (formal) {
                        case X64.Dec.Singleton(X64.Type.T ty, Id id) -> {
                            tempMap.put(id, new TempMap.Position.InStack(stackOffset));
                            stackOffset -= X64.WordSize.bytesOfWord;
                        }
                    }
                }
                for (X64.Dec.T local : locals) {
                    switch (local) {
                        case X64.Dec.Singleton(X64.Type.T ty, Id id) -> {
                            tempMap.put(id, new TempMap.Position.InStack(stackOffset));
                            stackOffset -= X64.WordSize.bytesOfWord;
                            frame.alloc();
                        }
                    }
                }


                List<X64.Block.T> newBlocks = new ArrayList<>();
                boolean firstBlock = true;
                for (X64.Block.T block : blocks) {
                    newBlocks.add(allocBlock(block, tempMap, firstBlock, frame));
                    if (firstBlock) {
                        firstBlock = false;
                    }
                }
                return new X64.Function.Singleton(retType, classId, methodId, formals, locals, newBlocks);
            }
        }
    }
    
    private X64.Program.T allocProgram0(X64.Program.T x64) {
        switch (x64) {
            case X64.Program.Singleton(Id entryClassName, Id entryMethodName, List<X64.Vtable.T> vtables, List<X64.Struct.T> structs, List<X64.Function.T> functions) -> {
                List<X64.Function.T> newFunctions = new ArrayList<>();
                for (X64.Function.T function : functions) {
                    newFunctions.add(allocFunction(function));
                }
                return new X64.Program.Singleton(entryClassName, entryMethodName, vtables, structs, newFunctions);
            }
        }
    }

    public X64.Program.T allocProgram(X64.Program.T x64) {
        Trace<X64.Program.T, X64.Program.T> trace =
                new Trace<>("regalloc.LinearScan.allocProgram",
                        this::allocProgram0,
                        x64,
                        X64.Program::pp,
                        X64.Program::pp);
        X64.Program.T result = trace.doit();
        // this should not be controlled by trace
        if (Control.X64.assemFile != null) {
            new PpAssem().ppProgram(result);
        }
        return result;
    }
}