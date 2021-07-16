package rars.venus.run;

import rars.Globals;
import rars.Settings;
import rars.SimulationException;
import rars.riscv.hardware.RegisterFile;
import rars.simulator.ProgramArgumentList;
import rars.simulator.Simulator;
import rars.simulator.SimulatorNotice;
import rars.util.SystemIO;
import rars.venus.ExecutePane;
import rars.venus.FileStatus;
import rars.venus.GuiAction;
import rars.venus.VenusUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

	/*
Copyright (c) 2003-2007,  Pete Sanderson and Kenneth Vollmar

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
 * Action class for the Run -> Go menu item (and toolbar icon)
 */
public class RunGoAction extends GuiAction {

    public static int defaultMaxSteps = -1; // "forever", formerly 10000000; // 10 million
    public static int maxSteps = defaultMaxSteps;
    private String name;
    private ExecutePane executePane;
    private VenusUI mainUI;

    public RunGoAction(String name, Icon icon, String descrip,
                       Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel);
        mainUI = gui;
    }

    /**
     * Action to take when GO is selected -- run the MIPS program!
     */
    public void actionPerformed(ActionEvent e) {
        name = this.getValue(Action.NAME).toString();
        executePane = mainUI.getMainPane().getExecutePane();
        if (FileStatus.isAssembled()) {
            if (!mainUI.getStarted()) {
                processProgramArgumentsIfAny();  // DPS 17-July-2008
            }
            if (mainUI.getReset() || mainUI.getStarted()) {

                mainUI.setStarted(true);  // added 8/27/05
                mainUI.getMessagesPane().postMessage(
                        name + ": running " + FileStatus.getFile().getName() + "\n\n");
                mainUI.getMessagesPane().selectRunMessageTab();
                executePane.getTextSegmentWindow().setCodeHighlighting(false);
                executePane.getTextSegmentWindow().unhighlightAllSteps();
                //FileStatus.set(FileStatus.RUNNING);
                mainUI.setMenuState(FileStatus.RUNNING);

                // Setup cleanup procedures for the simulation
                final Observer stopListener =
                        new Observer() {
                            public void update(Observable o, Object simulator) {
                                SimulatorNotice notice = ((SimulatorNotice) simulator);
                                if (notice.getAction() != SimulatorNotice.SIMULATOR_STOP) return;
                                Simulator.Reason reason = notice.getReason();
                                if (reason == Simulator.Reason.PAUSE || reason == Simulator.Reason.BREAKPOINT) {
                                    EventQueue.invokeLater(()->paused(notice.getDone(), reason, notice.getException()));
                                } else {
                                    EventQueue.invokeLater(()->stopped(notice.getException(), reason));
                                }
                                o.deleteObserver(this);
                            }
                        };
                Simulator.getInstance().addObserver(stopListener);

                int[] breakPoints = executePane.getTextSegmentWindow().getSortedBreakPointsArray();
                Globals.program.startSimulation(maxSteps, breakPoints);
            } else {
                // This should never occur because at termination the Go and Step buttons are disabled.
                JOptionPane.showMessageDialog(mainUI, "reset " + mainUI.getReset() + " started " + mainUI.getStarted());//"You must reset before you can execute the program again.");
            }
        } else {
            // note: this should never occur since "Go" is only enabled after successful assembly.
            JOptionPane.showMessageDialog(mainUI, "The program must be assembled before it can be run.");
        }
    }

    /**
     * Method to be called when Pause is selected through menu/toolbar/shortcut.  This should only
     * happen when MIPS program is running (FileStatus.RUNNING).  See VenusUI.java for enabled
     * status of menu items based on FileStatus.  Set GUI as if at breakpoint or executing
     * step by step.
     */

    public void paused(boolean done, Simulator.Reason pauseReason, SimulationException pe) {
        // I doubt this can happen (pause when execution finished), but if so treat it as stopped.
        if (done) {
            stopped(pe, Simulator.Reason.NORMAL_TERMINATION);
            return;
        }
        if (pauseReason == Simulator.Reason.BREAKPOINT) {
            mainUI.getMessagesPane().postMessage(
                    name + ": execution paused at breakpoint: " + FileStatus.getFile().getName() + "\n\n");
        } else {
            mainUI.getMessagesPane().postMessage(
                    name + ": execution paused by user: " + FileStatus.getFile().getName() + "\n\n");
        }
        mainUI.getMessagesPane().selectMessageTab();
        executePane.getTextSegmentWindow().setCodeHighlighting(true);
        executePane.getTextSegmentWindow().highlightStepAtPC();
        executePane.getRegistersWindow().updateRegisters();
        executePane.getFloatingPointWindow().updateRegisters();
        executePane.getControlAndStatusWindow().updateRegisters();
        executePane.getDataSegmentWindow().updateValues();
        FileStatus.set(FileStatus.RUNNABLE);
        mainUI.setReset(false);
    }

    /**
     * Method to be called when Stop is selected through menu/toolbar/shortcut.  This should only
     * happen when MIPS program is running (FileStatus.RUNNING).  See VenusUI.java for enabled
     * status of menu items based on FileStatus.  Display finalized values as if execution
     * terminated due to completion or exception.
     */

    public void stopped(SimulationException pe, Simulator.Reason reason) {
        // show final register and data segment values.
        executePane.getRegistersWindow().updateRegisters();
        executePane.getFloatingPointWindow().updateRegisters();
        executePane.getControlAndStatusWindow().updateRegisters();
        executePane.getDataSegmentWindow().updateValues();
        FileStatus.set(FileStatus.TERMINATED);
        SystemIO.resetFiles(); // close any files opened in MIPS program
        // Bring CSRs to the front if terminated due to exception.
        if (pe != null) {

            mainUI.getRegistersPane().setSelectedComponent(executePane.getControlAndStatusWindow());
            executePane.getTextSegmentWindow().setCodeHighlighting(true);
            executePane.getTextSegmentWindow().unhighlightAllSteps();
            executePane.getTextSegmentWindow().highlightStepAtAddress(RegisterFile.getProgramCounter() - 4);
        }
        switch (reason) {
            case NORMAL_TERMINATION:
                mainUI.getMessagesPane().postMessage(
                        "\n" + name + ": execution completed successfully.\n\n");
                mainUI.getMessagesPane().postRunMessage(
                        "\n-- program is finished running (" + Globals.exitCode + ") --\n\n");
                mainUI.getMessagesPane().selectRunMessageTab();
                break;
            case CLIFF_TERMINATION:
                mainUI.getMessagesPane().postMessage(
                        "\n" + name + ": execution terminated by null instruction.\n\n");
                mainUI.getMessagesPane().postRunMessage(
                        "\n-- program is finished running (dropped off bottom) --\n\n");
                mainUI.getMessagesPane().selectRunMessageTab();
                break;
            case EXCEPTION:
                mainUI.getMessagesPane().postMessage(
                        pe.error().generateReport());
                mainUI.getMessagesPane().postMessage(
                        "\n" + name + ": execution terminated with errors.\n\n");
                break;
            case STOP:
                mainUI.getMessagesPane().postMessage(
                        "\n" + name + ": execution terminated by user.\n\n");
                mainUI.getMessagesPane().selectMessageTab();
                break;
            case MAX_STEPS:
                mainUI.getMessagesPane().postMessage(
                        "\n" + name + ": execution step limit of " + maxSteps + " exceeded.\n\n");
                mainUI.getMessagesPane().selectMessageTab();
                break;
            default:
                // should never get here, because the other two cases are covered by paused()
        }
        RunGoAction.resetMaxSteps();
        mainUI.setReset(false);
    }

    /**
     * Reset max steps limit to default value at termination of a simulated execution.
     */

    public static void resetMaxSteps() {
        maxSteps = defaultMaxSteps;
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // Method to store any program arguments into MIPS memory and registers before
    // execution begins. Arguments go into the gap between $sp and kernel memory.
    // Argument pointers and count go into runtime stack and $sp is adjusted accordingly.
    // $a0 gets argument count (argc), $a1 gets stack address of first arg pointer (argv).
    private void processProgramArgumentsIfAny() {
        String programArguments = executePane.getTextSegmentWindow().getProgramArguments();
        if (programArguments == null || programArguments.length() == 0 ||
                !Globals.getSettings().getBooleanSetting(Settings.Bool.PROGRAM_ARGUMENTS)) {
            return;
        }
        new ProgramArgumentList(programArguments).storeProgramArguments();
    }


}
