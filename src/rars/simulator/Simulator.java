package rars.simulator;

import rars.*;
import rars.riscv.hardware.*;
import rars.riscv.BasicInstruction;
import rars.riscv.Instruction;
import rars.util.Binary;
import rars.util.SystemIO;
import rars.venus.run.RunSpeedPanel;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Observable;

	/*
Copyright (c) 2003-2010,  Pete Sanderson and Kenneth Vollmar

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
 * Used to simulate the execution of an assembled source program.
 *
 * @author Pete Sanderson
 * @version August 2005
 **/

public class Simulator extends Observable {
    private SimThread simulatorThread;
    private static Simulator simulator = null;  // Singleton object
    private static Runnable interactiveGUIUpdater = null;

    /**
     * various reasons for simulate to end...
     */
    public enum Reason {
        BREAKPOINT,
        EXCEPTION,
        MAX_STEPS,         // includes step mode (where maxSteps is 1)
        NORMAL_TERMINATION,
        CLIFF_TERMINATION, // run off bottom of program
        PAUSE,
        STOP
    }

    /**
     * Returns the Simulator object
     *
     * @return the Simulator object in use
     */
    public static Simulator getInstance() {
        // Do NOT change this to create the Simulator at load time (in declaration above)!
        // Its constructor looks for the GUI, which at load time is not created yet,
        // and incorrectly leaves interactiveGUIUpdater null!  This causes runtime
        // exceptions while running in timed mode.
        if (simulator == null) {
            simulator = new Simulator();
        }
        return simulator;
    }

    private Simulator() {
        simulatorThread = null;
        if (Globals.getGui() != null) {
            interactiveGUIUpdater = new UpdateGUI();
        }
    }

    /**
     * Simulate execution of given source program (in this thread).  It must have already been assembled.
     *
     * @param pc          address of first instruction to simulate; this goes into program counter
     * @param maxSteps    maximum number of steps to perform before returning false (0 or less means no max)
     * @param breakPoints array of breakpoint program counter values, use null if none
     * @return true if execution completed, false otherwise
     * @throws SimulationException Throws exception if run-time exception occurs.
     **/

    public Reason simulate(int pc, int maxSteps, int[] breakPoints) throws SimulationException {
        simulatorThread = new SimThread(pc, maxSteps, breakPoints);
        simulatorThread.run(); // Just call run, this is a blocking method
        SimulationException pe = simulatorThread.pe;
        boolean done = simulatorThread.done;
        Reason out = simulatorThread.constructReturnReason;
        if (done) SystemIO.resetFiles(); // close any files opened in the process of simulating
        this.simulatorThread = null;
        if (pe != null) {
            throw pe;
        }
        return out;
    }

    /**
     * Start simulated execution of given source program (in a new thread).  It must have already been assembled.
     *
     * @param pc          address of first instruction to simulate; this goes into program counter
     * @param maxSteps    maximum number of steps to perform before returning false (0 or less means no max)
     * @param breakPoints array of breakpoint program counter values, use null if none
     **/

    public void startSimulation(int pc, int maxSteps, int[] breakPoints) {
        simulatorThread = new SimThread(pc, maxSteps, breakPoints);
        new Thread(simulatorThread, "RISCV").start();
    }


    /**
     * Set the volatile stop boolean variable checked by the execution
     * thread at the end of each instruction execution.  If variable
     * is found to be true, the execution thread will depart
     * gracefully so the main thread handling the GUI can take over.
     * This is used by both STOP and PAUSE features.
     */
    private void interruptExecution(Reason reason) {
        if (simulatorThread != null) {
            simulatorThread.setStop(reason);
            for (StopListener l : stopListeners) {
                l.stopped(this);
            }
            simulatorThread = null;
        }
    }

    public void stopExecution() {
        interruptExecution(Reason.STOP);
    }

    public void pauseExecution() {
        interruptExecution(Reason.PAUSE);
    }

    /* This interface is required by the Asker class in MessagesPane
     * to be notified about the fact that the user has requested to
     * stop the execution. When that happens, it must unblock the
     * simulator thread. */
    public interface StopListener {
        void stopped(Simulator s);
    }

    private ArrayList<StopListener> stopListeners = new ArrayList<>(1);

    public void addStopListener(StopListener l) {
        stopListeners.add(l);
    }

    public void removeStopListener(StopListener l) {
        stopListeners.remove(l);
    }

    // The Simthread object will call this method when it enters and returns from
    // its run() method.  These signal start and stop, respectively, of
    // simulation execution.  The observer can then adjust its own state depending
    // on the execution state.  Note that "stop" and "done" are not the same thing.
    // "stop" just means it is leaving execution state; this could be triggered
    // by Stop button, by Pause button, by Step button, by runtime exception, by
    // instruction count limit, by breakpoint, or by end of simulation (truly done).
    private void notifyObserversOfExecution(SimulatorNotice notice) {
        this.setChanged();
        // TODO: this is not completely threadsafe, if anything using Swing is observing
        // This can be fixed by making a SwingObserver class that is thread-safe
        this.notifyObservers(notice);
    }

    public void interrupt() {
        if (simulatorThread == null) return;
        simulatorThread.interrupt();
    }

    /**
     * Perform the simulated execution. It is "interrupted" when main thread sets
     * the "stop" variable to true. The variable is tested before the next instruction
     * is simulated.  Thus interruption occurs in a tightly controlled fashion.
     */

    class SimThread implements Runnable {
        private int pc, maxSteps;
        private int[] breakPoints;
        private boolean done;
        private SimulationException pe;
        private volatile boolean stop = false;
        private Reason constructReturnReason;

        /**
         * SimThread constructor.  Receives all the information it needs to simulate execution.
         *
         * @param pc          address in text segment of first instruction to simulate
         * @param maxSteps    maximum number of instruction steps to simulate.  Default of -1 means no maximum
         * @param breakPoints array of breakpoints (instruction addresses) specified by user
         */
        SimThread(int pc, int maxSteps, int[] breakPoints) {
            this.pc = pc;
            this.maxSteps = maxSteps;
            this.breakPoints = breakPoints;
            this.done = false;
            this.pe = null;
        }

        /**
         * Sets to "true" the volatile boolean variable that is tested after each
         * instruction is executed.  After calling this method, the next test
         * will yield "true" and "construct" will return.
         *
         * @param reason the Reason for stopping (PAUSE or STOP)
         */
        public synchronized void setStop(Reason reason) {
            stop = true;
            constructReturnReason = reason;
            notify();
        }

        private void startExecution() {
            Simulator.getInstance().notifyObserversOfExecution(new SimulatorNotice(SimulatorNotice.SIMULATOR_START,
                    maxSteps,(Globals.getGui() != null || Globals.runSpeedPanelExists)?RunSpeedPanel.getInstance().getRunSpeed():RunSpeedPanel.UNLIMITED_SPEED,
                    pc, null, pe, done));
        }

        private void stopExecution(boolean done, Reason reason) {
            this.done = done;
            this.constructReturnReason = reason;
            SystemIO.flush(true);
            if (done) SystemIO.resetFiles(); // close any files opened in the process of simulating
            Simulator.getInstance().notifyObserversOfExecution(new SimulatorNotice(SimulatorNotice.SIMULATOR_STOP,
                    maxSteps, (Globals.getGui() != null || Globals.runSpeedPanelExists)?RunSpeedPanel.getInstance().getRunSpeed():RunSpeedPanel.UNLIMITED_SPEED,
                    pc, reason, pe, done));
        }

        private synchronized void interrupt() {
            notify();
        }

        private boolean handleTrap(SimulationException se, int pc) {
            assert se.cause() != -1 : "Unhandlable exception not thrown through ExitingEception";
            assert se.cause() >= 0 : "Interrupts cannot be handled by the trap handler";

            // set the relevant CSRs
            ControlAndStatusRegisterFile.updateRegister("ucause", se.cause());
            ControlAndStatusRegisterFile.updateRegister("uepc", pc);
            ControlAndStatusRegisterFile.updateRegister("utval", se.value());

            // Get the interrupt handler if it exists
            int utvec = ControlAndStatusRegisterFile.getValue("utvec");

            // Mode can be ignored because we are only handling traps
            int base = utvec & 0xFFFFFFFC;

            ProgramStatement exceptionHandler = null;
            if ((ControlAndStatusRegisterFile.getValue("medeleg") & (1 << se.cause())) != 0) { // test if this exception is delegated
                try {
                    exceptionHandler = Globals.memory.getStatement(base);
                } catch (AddressErrorException aee) {
                    // Handled below
                }
            }

            if (exceptionHandler != null) {
                ControlAndStatusRegisterFile.orRegister("ustatus", 0x10); // Set UPIE
                ControlAndStatusRegisterFile.clearRegister("ustatus", 0x1); // Clear UIE
                RegisterFile.setProgramCounter(base);
                return true;
            } else {
                // If we don't have an error handler or exceptions are disabled terminate the process
                this.pe = se;
                stopExecution(true, Reason.EXCEPTION);
                return false;
            }
        }


        private boolean handleInterrupt(int value, int cause, int pc) {
            assert (cause & 0x80000000) != 0 : "Traps cannot be handled by the interupt handler";
            int code = cause & 0x7FFFFFFF;

            // Don't handle cases where that interrupt isn't enabled
            assert ((ControlAndStatusRegisterFile.getValue("ustatus") & 0x1) != 0 && (ControlAndStatusRegisterFile.getValue("uie") & (1 << code)) != 0) : "The interrupt handler must be enabled";

            // set the relevant CSRs
            ControlAndStatusRegisterFile.updateRegister("ucause", cause);
            ControlAndStatusRegisterFile.updateRegister("uepc", pc);
            ControlAndStatusRegisterFile.updateRegister("utval", value);

            // Get the interrupt handler if it exists
            int utvec = ControlAndStatusRegisterFile.getValue("utvec");

            // Handle vectored mode
            int base = utvec & 0xFFFFFFFC, mode = utvec & 0x3;
            if (mode == 2) {
                base += 4 * code;
            }

            ProgramStatement exceptionHandler = null;
            try {
                exceptionHandler = Globals.memory.getStatement(base);
            } catch (AddressErrorException aee) {
                // handled below
            }
            if (exceptionHandler != null) {
                ControlAndStatusRegisterFile.orRegister("ustatus", 0x10); // Set UPIE
                ControlAndStatusRegisterFile.clearRegister("ustatus", ControlAndStatusRegisterFile.INTERRUPT_ENABLE);
                RegisterFile.setProgramCounter(base);
                return true;
            } else {
                // If we don't have an error handler or exceptions are disabled terminate the process
                this.pe = new SimulationException("Interrupt handler was not supplied, but interrupt enable was high");
                stopExecution(true, Reason.EXCEPTION);
                return false;
            }
        }

        /**
         * Implements Runnable
         */

        public void run() {
            // The next two statements are necessary for GUI to be consistently updated
            // before the simulation gets underway.  Without them, this happens only intermittently,
            // with a consequence that some simulations are interruptable using PAUSE/STOP and others
            // are not (because one or the other or both is not yet enabled).
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY - 1);
            Thread.yield();  // let the main thread run a bit to finish updating the GUI

            if (breakPoints == null || breakPoints.length == 0) {
                breakPoints = null;
            } else {
                Arrays.sort(breakPoints);  // must be pre-sorted for binary search
            }

            startExecution();

            // *******************  PS addition 26 July 2006  **********************
            // A couple statements below were added for the purpose of assuring that when
            // "back stepping" is enabled, every instruction will have at least one entry
            // on the back-stepping stack.  Most instructions will because they write either
            // to a register or memory.  But "nop" and branches not taken do not.  When the
            // user is stepping backward through the program, the stack is popped and if
            // an instruction has no entry it will be skipped over in the process.  This has
            // no effect on the correctness of the mechanism but the visual jerkiness when
            // instruction highlighting skips such instrutions is disruptive.  Current solution
            // is to add a "do nothing" stack entry for instructions that do no write anything.
            // To keep this invisible to the "simulate()" method writer, we
            // will push such an entry onto the stack here if there is none for this instruction
            // by the time it has completed simulating.  This is done by the IF statement
            // just after the call to the simulate method itself.  The BackStepper method does
            // the aforementioned check and decides whether to push or not.  The result
            // is a a smoother interaction experience.  But it comes at the cost of slowing
            // simulation speed for flat-out runs, for every instruction executed even
            // though very few will require the "do nothing" stack entry.  For stepped or
            // timed execution the slower execution speed is not noticeable.
            //
            // To avoid this cost I tried a different technique: back-fill with "do nothings"
            // during the backstepping itself when this situation is recognized.  Problem
            // was in recognizing all possible situations in which the stack contained such
            // a "gap".  It became a morass of special cases and it seemed every weird test
            // case revealed another one.  In addition, when a program
            // begins with one or more such instructions ("nop" and branches not taken),
            // the backstep button is not enabled until a "real" instruction is executed.
            // This is noticeable in stepped mode.
            // *********************************************************************

            RegisterFile.initializeProgramCounter(pc);
            ProgramStatement statement = null;
            int steps = 0;
            boolean ebreak = false, waiting = false;

            // Volatile variable initialized false but can be set true by the main thread.
            // Used to stop or pause a running program.  See stopSimulation() above.
            while (!stop) {
                SystemIO.flush(false);
                // Perform the RISCV instruction in synchronized block.  If external threads agree
                // to access memory and registers only through synchronized blocks on same
                // lock variable, then full (albeit heavy-handed) protection of memory and
                // registers is assured.  Not as critical for reading from those resources.
                Globals.memoryAndRegistersLock.lock();
                try {
                    // Handle pending interupts and traps first
                    long uip = ControlAndStatusRegisterFile.getValueNoNotify("uip"), uie = ControlAndStatusRegisterFile.getValueNoNotify("uie");
                    boolean IE = (ControlAndStatusRegisterFile.getValueNoNotify("ustatus") & ControlAndStatusRegisterFile.INTERRUPT_ENABLE) != 0;
                    // make sure no interrupts sneak in while we are processing them
                    pc = RegisterFile.getProgramCounter();
                    synchronized (InterruptController.lock) {
                        boolean pendingExternal = InterruptController.externalPending(),
                                pendingTimer = InterruptController.timerPending(),
                                pendingTrap = InterruptController.trapPending();
                        // This is the explicit (in the spec) order that interrupts should be serviced
                        if (IE && pendingExternal && (uie & ControlAndStatusRegisterFile.EXTERNAL_INTERRUPT) != 0) {
                            if (handleInterrupt(InterruptController.claimExternal(), SimulationException.EXTERNAL_INTERRUPT, pc)) {
                                pendingExternal = false;
                                uip &= ~0x100;
                            } else {
                                return; // if the interrupt can't be handled, but the interrupt enable bit is high, thats an error
                            }
                        } else if (IE && (uip & 0x1) != 0 && (uie & ControlAndStatusRegisterFile.SOFTWARE_INTERRUPT) != 0) {
                            if (handleInterrupt(0, SimulationException.SOFTWARE_INTERRUPT, pc)) {
                                uip &= ~0x1;
                            } else {
                                return; // if the interrupt can't be handled, but the interrupt enable bit is high, thats an error
                            }
                        } else if (IE && pendingTimer && (uie & ControlAndStatusRegisterFile.TIMER_INTERRUPT) != 0) {
                            if (handleInterrupt(InterruptController.claimTimer(), SimulationException.TIMER_INTERRUPT, pc)) {
                                pendingTimer = false;
                                uip &= ~0x10;
                            } else {
                                return; // if the interrupt can't be handled, but the interrupt enable bit is high, thats an error
                            }
                        } else if (pendingTrap) { // if we have a pending trap and aren't handling an interrupt it must be handled
                            if (handleTrap(InterruptController.claimTrap(), pc - Instruction.INSTRUCTION_LENGTH)) { // account for that the PC has already been incremented
                            } else {
                                return;
                            }
                        }
                        uip |= (pendingExternal ? ControlAndStatusRegisterFile.EXTERNAL_INTERRUPT : 0) | (pendingTimer ? ControlAndStatusRegisterFile.TIMER_INTERRUPT : 0);
                    }
                    if (uip != ControlAndStatusRegisterFile.getValueNoNotify("uip")) {
                        ControlAndStatusRegisterFile.updateRegister("uip", uip);
                    }

                    // always handle interrupts and traps before quiting
                    // Check number of instructions executed.  Return if at limit (-1 is no limit).
                    if (maxSteps > 0) {
                        steps++;
                        if (steps > maxSteps) {
                            stopExecution(false, Reason.MAX_STEPS);
                            return;
                        }
                    }

                    pc = RegisterFile.getProgramCounter();
                    RegisterFile.incrementPC();
                    // Get instuction
                    try {
                        statement = Globals.memory.getStatement(pc);
                    } catch (AddressErrorException e) {
                        SimulationException tmp;
                        if (e.getType() == SimulationException.LOAD_ACCESS_FAULT) {
                            tmp = new SimulationException("Instruction load access error", SimulationException.INSTRUCTION_ACCESS_FAULT);
                        } else {
                            tmp = new SimulationException("Instruction load alignment error", SimulationException.INSTRUCTION_ADDR_MISALIGNED);
                        }
                        if (!InterruptController.registerSynchronousTrap(tmp, pc)) {
                            this.pe = tmp;
                            ControlAndStatusRegisterFile.updateRegister("uepc", pc);
                            stopExecution(true, Reason.EXCEPTION);
                            return;
                        } else {
                            continue;
                        }
                    }
                    if (statement == null) {
                        stopExecution(true, Reason.CLIFF_TERMINATION);
                        return;
                    }

                    try {
                        BasicInstruction instruction = (BasicInstruction) statement.getInstruction();
                        if (instruction == null) {
                            // TODO: Proper error handling here
                            throw new SimulationException(statement,
                                    "undefined instruction (" + Binary.intToHexString(statement.getBinaryStatement()) + ")",
                                    SimulationException.ILLEGAL_INSTRUCTION);
                        }
                        // THIS IS WHERE THE INSTRUCTION EXECUTION IS ACTUALLY SIMULATED!
                        instruction.simulate(statement);

                        // IF statement added 7/26/06 (explanation above)
                        if (Globals.getSettings().getBackSteppingEnabled()) {
                            Globals.program.getBackStepper().addDoNothing(pc);
                        }
                    } catch (BreakpointException b) {
                        // EBREAK needs backstepping support too.
                        if (Globals.getSettings().getBackSteppingEnabled()) {
                            Globals.program.getBackStepper().addDoNothing(pc);
                        }
                        ebreak = true;
                    } catch (WaitException w) {
                        if (Globals.getSettings().getBackSteppingEnabled()) {
                            Globals.program.getBackStepper().addDoNothing(pc);
                        }
                        waiting = true;
                    } catch (ExitingException e) {
                        if (e.error() == null) {
                            this.constructReturnReason = Reason.NORMAL_TERMINATION;
                        } else {
                            this.constructReturnReason = Reason.EXCEPTION;
                            this.pe = e;
                        }
                        // TODO: remove access to constructReturnReason
                        stopExecution(true, constructReturnReason);
                        return;
                    } catch (SimulationException se) {
                        if (InterruptController.registerSynchronousTrap(se, pc)) {
                            continue;
                        } else {
                            this.pe = se;
                            stopExecution(true, Reason.EXCEPTION);
                            return;
                        }
                    }
                } finally {
                    Globals.memoryAndRegistersLock.unlock();
                }

                // Update cycle(h) and instret(h)
                long cycle = ControlAndStatusRegisterFile.getValueNoNotify("cycle"),
                         instret = ControlAndStatusRegisterFile.getValueNoNotify("instret"),
                         time = System.currentTimeMillis();;
                ControlAndStatusRegisterFile.updateRegisterBackdoor("cycle",cycle+1);
                ControlAndStatusRegisterFile.updateRegisterBackdoor("instret",instret+1);
                ControlAndStatusRegisterFile.updateRegisterBackdoor("time",time);

                //     Return if we've reached a breakpoint.
                if (ebreak || (breakPoints != null) &&
                        (Arrays.binarySearch(breakPoints, RegisterFile.getProgramCounter()) >= 0)) {
                    stopExecution(false, Reason.BREAKPOINT);
                    return;
                }

                // Wait if WFI ran
                if (waiting) {
                    if (!(InterruptController.externalPending() || InterruptController.timerPending())) {
                        synchronized (this) {
                            try {
                                wait();
                            } catch (InterruptedException ie) {
                                // Don't bother catching an interruption
                            }
                        }
                    }
                    waiting = false;
                }

                // schedule GUI update only if: there is in fact a GUI! AND
                //                              using Run,  not Step (maxSteps != 1) AND
                //                              running slowly enough for GUI to keep up
                if (interactiveGUIUpdater != null && maxSteps != 1 &&
                        RunSpeedPanel.getInstance().getRunSpeed() < RunSpeedPanel.UNLIMITED_SPEED) {
                    SwingUtilities.invokeLater(interactiveGUIUpdater);
                }
                if (Globals.getGui() != null || Globals.runSpeedPanelExists) { // OR added by DPS 24 July 2008 to enable speed control by stand-alone tool
                    if (maxSteps != 1 &&
                            RunSpeedPanel.getInstance().getRunSpeed() < RunSpeedPanel.UNLIMITED_SPEED) {
                        try {
                            // TODO: potentially use this.wait so it can be interrupted
                            Thread.sleep((int) (1000 / RunSpeedPanel.getInstance().getRunSpeed())); // make sure it's never zero!
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
            stopExecution(false, constructReturnReason);
        }
    }

    private class UpdateGUI implements Runnable {
        public void run() {
            if (Globals.getGui().getRegistersPane().getSelectedComponent() ==
                    Globals.getGui().getMainPane().getExecutePane().getRegistersWindow()) {
                Globals.getGui().getMainPane().getExecutePane().getRegistersWindow().updateRegisters();
            } else {
                Globals.getGui().getMainPane().getExecutePane().getFloatingPointWindow().updateRegisters();
            }
            Globals.getGui().getMainPane().getExecutePane().getDataSegmentWindow().updateValues();
            Globals.getGui().getMainPane().getExecutePane().getTextSegmentWindow().setCodeHighlighting(true);
            Globals.getGui().getMainPane().getExecutePane().getTextSegmentWindow().highlightStepAtPC();
        }
    }
}
