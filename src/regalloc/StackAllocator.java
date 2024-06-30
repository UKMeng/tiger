package regalloc;

import codegen.X64;
import control.Control;
import util.Id;
import util.Label;
import util.Todo;
import util.Trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

// A register allocator to allocate each variable to a physical register,
// using a stack-based allocation approach.
public class StackAllocator {

    private X64.Block.T allocBlock(X64.Block.T block, TempMap tempMap) {
        switch (block) {
            case X64.Block.Singleton(Label label, List<X64.Instr.T> instrs, List<X64.Transfer.T> transfer) -> {
                List<X64.Instr.T> newInstrs = new ArrayList<>();
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
                                        int offset = tempMap.getOffset(id);
                                        Id reg= X64.Register.calleeSavedRegs.get(regIndex);
                                        regIndex++;
                                        regMap.put(id, reg);

                                        Id rbpReg = X64.Register.allRegs.get(6);

                                        List<X64.VirtualReg.T> uses_ = List.of(new X64.VirtualReg.Reg(rbpReg, ty));
                                        List<X64.VirtualReg.T> defs_ = List.of(new X64.VirtualReg.Reg(reg, ty));
                                        newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Load,
                                                (uarg, darg) ->
                                                        STR."movq\t\{offset}(\{uarg.getFirst()}), \{darg.getFirst()})",
                                                uses_,
                                                defs_));
                                        newUses.add(new X64.VirtualReg.Reg(reg, ty));
                                    }
                                    case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
                                        newUses.add(var);
                                    }
                                }
                            }
                            for (X64.VirtualReg.T var: defs) {
                                switch (var) {
                                    case X64.VirtualReg.Vid(Id id, X64.Type.T ty) -> {
                                        Id reg;
                                        if(regMap.containsKey(id)) {
                                            reg = regMap.get(id);
                                        } else {
                                            reg = X64.Register.calleeSavedRegs.get(regIndex);
                                            regIndex++;
                                            regMap.put(id, reg);
                                        }
                                        newDefs.add(new X64.VirtualReg.Reg(reg, ty));
                                    }
                                    case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
                                        newDefs.add(var);
                                    }
                                }
                            }
                            newInstrs.add(new X64.Instr.Singleton(kind, ppFormat, newUses, newDefs));
                            for (X64.VirtualReg.T var: defs) {
                                switch (var) {
                                    case X64.VirtualReg.Vid(Id id, X64.Type.T ty) -> {
                                        Id reg = regMap.get(id);
                                        int offset = tempMap.getOffset(id);
                                        Id rbpReg = X64.Register.allRegs.get(6);
                                        List<X64.VirtualReg.T> uses_ = List.of(new X64.VirtualReg.Reg(reg, ty));
                                        List<X64.VirtualReg.T> defs_ = List.of(new X64.VirtualReg.Reg(rbpReg, ty));
                                        newInstrs.add(new X64.Instr.Singleton(X64.Instr.Kind.Store,
                                                (uarg, darg) ->
                                                        STR."movq\t\{uarg.getFirst()}, \{offset}(\{darg.getFirst()})",
                                                uses_,
                                                defs_));
                                    }
                                    case X64.VirtualReg.Reg(Id r, X64.Type.T ty) -> {
                                    }
                                }
                            }
                        }
                    }
                }
                return new X64.Block.Singleton(label, newInstrs, transfer);
            }
        }
    }

    private X64.Function.T allocFunction(X64.Function.T function) {
        TempMap tempMap = new TempMap();
        int offset = 0;
        switch (function) {
            case X64.Function.Singleton(X64.Type.T retType, Id classId, Id methodId, List<X64.Dec.T> formals, List<X64.Dec.T> locals, List<X64.Block.T> blocks) -> {
                for (X64.Dec.T formal : formals) {
                    switch (formal) {
                        case X64.Dec.Singleton(X64.Type.T ty, Id id) -> {
                            tempMap.put(id, new TempMap.Position.InStack(offset));
                            offset -= X64.WordSize.bytesOfWord;
                        }
                    }
                }
                for (X64.Dec.T local : locals) {
                    switch (local) {
                        case X64.Dec.Singleton(X64.Type.T ty, Id id) -> {
                            tempMap.put(id, new TempMap.Position.InStack(offset));
                            offset -= X64.WordSize.bytesOfWord;
                        }
                    }
                }


                List<X64.Block.T> newBlocks = new ArrayList<>();
                for (X64.Block.T block : blocks) {
                    newBlocks.add(allocBlock(block, tempMap));
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
                new Trace<>("regalloc.StackAllocator.allocProgram",
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