// Export the original game's named methods for local porting analysis.
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.data.*;
import ghidra.program.model.symbol.SourceType;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ExportGame extends GhidraScript {
    private VariableStorage argumentStorage(int firstRegister, DataType type) throws Exception {
        if (type.getLength() == 8)
            return new VariableStorage(currentProgram,
                currentProgram.getRegister("r" + (firstRegister + 1)),
                currentProgram.getRegister("r" + firstRegister));
        return new VariableStorage(currentProgram, currentProgram.getRegister("r" + firstRegister));
    }
    public void run() throws Exception {
        String[] args = getScriptArgs();
        Map<Long, String> methods = new TreeMap<>();
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            String[] parts = line.split("\t", 2);
            methods.put(Long.parseLong(parts[0], 16), parts[1]);
        }
        // These ARMv6 soft-float helper imports otherwise lose argument/return
        // types, obscuring gameplay constants and arithmetic in the decompiler.
        for (Function function : currentProgram.getFunctionManager().getFunctions(true)) {
            String name = function.getName().replaceFirst("^_+", "");
            DataType result = null;
            DataType argument = null;
            int arity = 1;
            if (name.matches("(add|sub|mul|div)(sf|df)3vfp")) {
                result = argument = name.contains("sf") ? FloatDataType.dataType : DoubleDataType.dataType;
                arity = 2;
            } else if (name.matches("(eq|ne|gt|ge|lt|le)(sf|df)2vfp")) {
                result = IntegerDataType.dataType;
                argument = name.contains("sf") ? FloatDataType.dataType : DoubleDataType.dataType;
                arity = 2;
            } else if (name.equals("extendsfdf2vfp")) {
                result = DoubleDataType.dataType; argument = FloatDataType.dataType;
            } else if (name.equals("truncdfsf2vfp")) {
                result = FloatDataType.dataType; argument = DoubleDataType.dataType;
            } else if (name.matches("float(un)?si(sf|df)vfp")) {
                result = name.contains("sf") ? FloatDataType.dataType : DoubleDataType.dataType;
                argument = name.contains("unsi") ? UnsignedIntegerDataType.dataType : IntegerDataType.dataType;
            } else if (name.matches("fix(sf|df)sivfp")) {
                result = IntegerDataType.dataType;
                argument = name.contains("sf") ? FloatDataType.dataType : DoubleDataType.dataType;
            }
            if (name.equals("sin") || name.equals("cos")) {
                result = argument = DoubleDataType.dataType;
            }
            if (name.equals("unordsf2vfp")) {
                result = IntegerDataType.dataType; argument = FloatDataType.dataType; arity = 2;
            }
            if (result != null) {
                Parameter[] params = new Parameter[arity];
                for (int i = 0; i < arity; i++) params[i] = new ParameterImpl("value" + i,
                    argument, argumentStorage(i * argument.getLength() / 4, argument), currentProgram);
                function.updateFunction(null,
                    new ReturnParameterImpl(result, argumentStorage(0, result), currentProgram),
                    Function.FunctionUpdateType.CUSTOM_STORAGE, true, SourceType.USER_DEFINED, params);
            }
        }
        // Mach-O maxprot may make constant pools appear mutable; __TEXT is
        // actually mapped read/execute (initprot=5). Keep data segments writable.
        for (ghidra.program.model.mem.MemoryBlock block : currentProgram.getMemory().getBlocks()) {
            if (block.getStart().getOffset() >= 0x1000 && block.getEnd().getOffset() < 0x15000) {
                println("Read-only block " + block.getName() + " " + block.getStart() + ".." + block.getEnd());
                block.setWrite(false);
            } else if (block.getStart().getOffset() >= 0x15000 && block.getEnd().getOffset() < 0x19000) {
                block.setWrite(true);
            }
        }
        DecompInterface decompiler = new DecompInterface();
        DecompileOptions options = new DecompileOptions();
        options.setRespectReadOnly(true);
        decompiler.setOptions(options);
        decompiler.openProgram(currentProgram);
        Map<String, PrintWriter> outputs = new TreeMap<>();
        int count = 0;
        for (Map.Entry<Long, String> entry : methods.entrySet()) {
            String name = entry.getValue();
            String owner = name.substring(2, name.indexOf(' ')).replaceAll("[^A-Za-z0-9_]", "_");
            Function function = getFunctionAt(toAddr(entry.getKey()));
            if (function == null) { println("Missing function: " + name); continue; }
            PrintWriter output = outputs.get(owner);
            if (output == null) {
                output = new PrintWriter(new File(args[1], owner + ".c"));
                outputs.put(owner, output);
            }
            output.println("// " + name + " @ " + function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 60, monitor);
            if (result.decompileCompleted()) output.println(result.getDecompiledFunction().getC());
            else output.println("// Failed: " + result.getErrorMessage());
            count++;
        }
        for (PrintWriter output : outputs.values()) output.close();
        decompiler.dispose();
        println("Exported " + count + " named methods");
    }
}
