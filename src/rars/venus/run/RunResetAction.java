package rars.venus.run;

import rars.AssemblyException;
import rars.Globals;
import rars.riscv.hardware.*;
import rars.util.SystemIO;
import rars.venus.ExecutePane;
import rars.venus.FileStatus;
import rars.venus.GuiAction;
import rars.venus.VenusUI;

import javax.swing.*;
import java.awt.event.ActionEvent;

	/*
Copyright (c) 2003-2009,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */

/**
 * Action  for the Run -> Reset menu item
 */
public class RunResetAction extends GuiAction {
    private VenusUI mainUI;

    public RunResetAction(String name, Icon icon, String descrip,
                          Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel);
        mainUI = gui;
    }

    /**
     * reset GUI components and MIPS resources
     */
    public void actionPerformed(ActionEvent e) {
        RunGoAction.resetMaxSteps();
        String name = this.getValue(Action.NAME).toString();
        ExecutePane executePane = mainUI.getMainPane().getExecutePane();
        // The difficult part here is resetting the data segment.  Two approaches are:
        // 1. After each assembly, get a deep copy of the Globals.memory array
        //    containing data segment.  Then replace it upon reset.
        // 2. Simply re-assemble the program upon reset, and the assembler will
        //    build a new data segment.  Reset can only be done after a successful
        //    assembly, so there is "no" chance of assembler error.
        // I am choosing the second approach although it will slow down the reset
        // operation.  The first approach requires additional Memory class methods.
        try {
            Globals.program.assemble(RunAssembleAction.getProgramsToAssemble(),
                    RunAssembleAction.getExtendedAssemblerEnabled(),
                    RunAssembleAction.getWarningsAreErrors());
        } catch (AssemblyException pe) {
            // Should not be possible
            mainUI.getMessagesPane().postMessage(
                    //pe.errors().generateErrorReport());
                    "Unable to reset.  Please close file then re-open and re-assemble.\n");
            return;
        }

        RegisterFile.resetRegisters();
        FloatingPointRegisterFile.resetRegisters();
        ControlAndStatusRegisterFile.resetRegisters();
        InterruptController.reset();

        executePane.getRegistersWindow().clearHighlighting();
        executePane.getRegistersWindow().updateRegisters();
        executePane.getFloatingPointWindow().clearHighlighting();
        executePane.getFloatingPointWindow().updateRegisters();
        executePane.getControlAndStatusWindow().clearHighlighting();
        executePane.getControlAndStatusWindow().updateRegisters();
        executePane.getDataSegmentWindow().highlightCellForAddress(Memory.configuration.getDataBaseAddress());
        executePane.getDataSegmentWindow().clearHighlighting();
        executePane.getTextSegmentWindow().resetModifiedSourceCode();
        executePane.getTextSegmentWindow().setCodeHighlighting(true);
        executePane.getTextSegmentWindow().highlightStepAtPC();
        mainUI.getRegistersPane().setSelectedComponent(executePane.getRegistersWindow());
        FileStatus.set(FileStatus.RUNNABLE);
        mainUI.setReset(true);
        mainUI.setStarted(false);

        // Aug. 24, 2005 Ken Vollmar
        SystemIO.resetFiles();  // Ensure that I/O "file descriptors" are initialized for a new program run

        mainUI.getMessagesPane().postRunMessage(
                "\n" + name + ": reset completed.\n\n");
    }
}