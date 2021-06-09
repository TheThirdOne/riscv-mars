package rars.extras;

import rars.Globals;
import rars.Settings;
import rars.assembler.Directives;
import rars.riscv.*;
import rars.riscv.hardware.Memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

/**
 * Small class for automatically generating documentation.
 *
 * Currently it makes some Markdown tables, but in the future it could do something
 * with javadocs or generate a website with all of the help information
 */
public class Documentation {

    public static void main(String[] args){
        Globals.initialize(false);
        System.out.println(createDirectiveMarkdown());
        System.out.println(createSyscallMarkdown());
        System.out.println(createInstructionMarkdown(BasicInstruction.class));
        System.out.println(createInstructionMarkdown(ExtendedInstruction.class));
        System.out.println(createInstructionMarkdown64Only(BasicInstruction.class));
        System.out.println(createInstructionMarkdown64Only(ExtendedInstruction.class));
    }

    public static String createDirectiveMarkdown(){
        ArrayList<Directives> directives = Directives.getDirectiveList();
        directives.sort(Comparator.comparing(Directives::getName));
        StringBuilder output = new StringBuilder("| Name | Description|\n|------|------------|");
        for (Directives direct: directives) {
            output.append("\n|");
            output.append(direct.getName());
            output.append('|');
            output.append(direct.getDescription());
            output.append('|');
        }
        return output.toString();
    }

    public static String createSyscallMarkdown(){
        ArrayList<AbstractSyscall> list = SyscallLoader.getSyscallList();
        Collections.sort(list);
        StringBuilder output = new StringBuilder("| Name | Call Number (a7) | Description | Inputs | Outputs |\n|------|------------------|-------------|--------|---------|");
        for (AbstractSyscall syscall : list) {
            output.append("\n|");
            output.append(syscall.getName());
            output.append('|');
            output.append(syscall.getNumber());
            output.append('|');
            output.append(syscall.getDescription());
            output.append('|');
            output.append(syscall.getInputs());
            output.append('|');
            output.append(syscall.getOutputs());
            output.append('|');
        }

        return output.toString();
    }

    public static String createInstructionMarkdown(Class<? extends Instruction> instructionClass){
        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.RV64_ENABLED,false);
        InstructionSet.rv64 = false;
        Globals.instructionSet.populate();

        ArrayList<Instruction> instructionList = Globals.instructionSet.getInstructionList();
        instructionList.sort(Comparator.comparing(Instruction::getExampleFormat));

        StringBuilder output = new StringBuilder("| Example Usage | Description |\n|---------------|-------------|");
        for (Instruction instr : instructionList) {
            if (instructionClass.isInstance(instr)) {
                output.append("\n|");
                output.append(instr.getExampleFormat());
                output.append('|');
                output.append(instr.getDescription());
                output.append('|');
            }
        }
        return output.toString();
    }
    public static String createInstructionMarkdown64Only(Class<? extends Instruction> instructionClass){

        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.RV64_ENABLED,false);
        InstructionSet.rv64 = false;
        Globals.instructionSet.populate();

        HashSet<String> set = new HashSet<>();
        for (Instruction i : Globals.instructionSet.getInstructionList()){
            set.add(i.getExampleFormat());
        }

        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.RV64_ENABLED,true);
        InstructionSet.rv64 = true;
        Globals.instructionSet.populate();

        ArrayList<Instruction> instructionList64 = Globals.instructionSet.getInstructionList();
        instructionList64.sort(Comparator.comparing(Instruction::getExampleFormat));
        StringBuilder output = new StringBuilder("| Example Usage | Description |\n|---------------|-------------|");
        for (Instruction instr : instructionList64) {
            if (instructionClass.isInstance(instr) && !set.contains(instr.getExampleFormat())) {
                output.append("\n|");
                output.append(instr.getExampleFormat());
                output.append('|');
                output.append(instr.getDescription());
                output.append('|');
            }
        }
        return output.toString();
    }
}
