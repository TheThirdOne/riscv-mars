/*
Copyright (c) 2009,  Ingo Kofler, ITEC, Klagenfurt University, Austria

Developed by Ingo Kofler (ingo.kofler@itec.uni-klu.ac.at)

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

package rars.tools;

import rars.ProgramStatement;
import rars.riscv.hardware.*;
import rars.riscv.instructions.Branch;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Observable;


/**
 * A RARS tool for simulating branch prediction with a Branch History Table (BHT)
 * <p>
 * The simulation is based on observing the access to the instruction memory area (text segment).
 * If a branch instruction is encountered, a prediction based on a BHT is performed.
 * The outcome of the branch is compared with the prediction and the prediction is updated accordingly.
 * Statistics about the correct and incorrect number of predictions can be obtained for each BHT entry.
 * The number of entries in the BHT and the history that is considered for each prediction can be configured interactively.
 * A change of the configuration however causes a re-initialization of the BHT.
 * <p>
 * The tool can be used to show how branch prediction works in case of loops and how effective such simple methods are.
 * In case of nested loops the difference of BHT with 1 or 2 Bit history can be explored and visualized.
 *
 * @author ingo.kofler@itec.uni-klu.ac.at
 */
public class BHTSimulator extends AbstractToolAndApplication implements ActionListener {
    /**
     * constant for the default size of the BHT
     */
    public static final int BHT_DEFAULT_SIZE = 16;

    /**
     * constant for the default history size
     */
    public static final int BHT_DEFAULT_HISTORY = 1;

    /**
     * constant for the default inital value
     */
    public static final boolean BHT_DEFAULT_INITVAL = false;

    /**
     * the name of the tool
     */
    public static final String BHT_NAME = "BHT Simulator";

    /**
     * the version of the tool
     */
    public static final String BHT_VERSION = "Version 1.0 (Ingo Kofler)";

    /**
     * the heading of the tool
     */
    public static final String BHT_HEADING = "Branch History Table Simulator";

    /**
     * the GUI of the BHT simulator
     */
    private BHTSimGUI m_gui;

    /**
     * the model of the BHT
     */
    private BHTableModel m_bhtModel;

    /**
     * state variable that indicates that the last instruction was a branch instruction (if address != 0) or not (address == 0)
     */
    private int m_pendingBranchInstAddress;

    /**
     * state variable that signals if the last branch was taken
     */
    private boolean m_lastBranchTaken;


    /**
     * Creates a BHT Simulator with given name and heading.
     */
    public BHTSimulator() {
        super(BHTSimulator.BHT_NAME + ", " + BHTSimulator.BHT_VERSION, BHTSimulator.BHT_HEADING);
    }


    /**
     * Adds BHTSimulator as observer of the text segment.
     */
    protected void addAsObserver() {
        addAsObserver(Memory.configuration.text.low,Memory.configuration.text.high);
        addAsObserver(RegisterFile.getProgramCounterRegister());
    }


    /**
     * Creates a GUI and initialize the GUI with the default values.
     */
    protected JComponent buildMainDisplayArea() {

        m_gui = new BHTSimGUI();
        m_bhtModel = new BHTableModel(BHTSimulator.BHT_DEFAULT_SIZE, BHTSimulator.BHT_DEFAULT_HISTORY, BHT_DEFAULT_INITVAL);

        m_gui.getTabBHT().setModel(m_bhtModel);
        m_gui.getCbBHThistory().setSelectedItem(BHTSimulator.BHT_DEFAULT_HISTORY);
        m_gui.getCbBHTentries().setSelectedItem(BHTSimulator.BHT_DEFAULT_SIZE);

        m_gui.getCbBHTentries().addActionListener(this);
        m_gui.getCbBHThistory().addActionListener(this);
        m_gui.getCbBHTinitVal().addActionListener(this);

        return m_gui;
    }

    @Override
    public String getName() {
        return BHTSimulator.BHT_NAME;
    }


    /**
     * Performs a reset of the simulator.
     * This causes the BHT to be reseted and the log messages to be cleared.
     */
    protected void reset() {
        resetSimulator();
    }


    /**
     * Handles the actions when selecting another value in one of the two combo boxes.
     * Selecting a different BHT size or history causes a reset of the simulator.
     */
    public void actionPerformed(ActionEvent event) {
        // change of the BHT size or BHT bit configuration
        // resets the simulator
        if (event.getSource() == m_gui.getCbBHTentries() || event.getSource() == m_gui.getCbBHThistory() || event.getSource() == m_gui.getCbBHTinitVal()) {
            resetSimulator();
        }
    }


    /**
     * Resets the simulator by clearing the GUI elements and resetting the BHT.
     */
    protected void resetSimulator() {
        m_gui.getTfInstruction().setText("");
        m_gui.getTfAddress().setText("");
        m_gui.getTfIndex().setText("");
        m_gui.getTaLog().setText("");
        m_bhtModel.initBHT((Integer) m_gui.getCbBHTentries().getSelectedItem(),
                (Integer) m_gui.getCbBHThistory().getSelectedItem(),
                ((String) m_gui.getCbBHTinitVal().getSelectedItem()).equals(BHTSimGUI.BHT_TAKE_BRANCH));

        m_pendingBranchInstAddress = 0;
        m_lastBranchTaken = false;
    }


    /**
     * Handles the execution branch instruction.
     * This method is called each time a branch instruction is executed.
     * Based on the address of the instruction the corresponding index into the BHT is calculated.
     * The prediction is obtained from the BHT at the calculated index and is visualized appropriately.
     *
     * @param stmt the branch statement that is executed
     */
    protected void handlePreBranchInst(ProgramStatement stmt) {

        String strStmt = stmt.getBasicAssemblyStatement();
        int address = stmt.getAddress();
        int idx = m_bhtModel.getIdxForAddress(address);

        // update the GUI
        m_gui.getTfInstruction().setText(strStmt);
        m_gui.getTfAddress().setText("0x" + Integer.toHexString(address));
        m_gui.getTfIndex().setText("" + idx);

        // mark the affected BHT row
        m_gui.getTabBHT().setSelectionBackground(BHTSimGUI.COLOR_PREPREDICTION);
        m_gui.getTabBHT().addRowSelectionInterval(idx, idx);

        // add output to log
        m_gui.getTaLog().append("instruction " + strStmt + " at address 0x" + Integer.toHexString(address) + ", maps to index " + idx + "\n");
        m_gui.getTaLog().append("branches to address 0x" + BHTSimulator.extractBranchAddress(stmt) + "\n");
        m_gui.getTaLog().append("prediction is: " + (m_bhtModel.getPredictionAtIdx(idx) ? "take" : "do not take") + "...\n");
        m_gui.getTaLog().setCaretPosition(m_gui.getTaLog().getDocument().getLength());

    }


    /**
     * Handles the execution of the branch instruction.
     * The correctness of the prediction is visualized in both the table and the log message area.
     * The BHT is updated based on the information if the branch instruction was taken or not.
     *
     * @param branchInstAddr the address of the branch instruction
     * @param branchTaken    the information if the branch is taken or not (determined in a step before)
     */
    protected void handleExecBranchInst(int branchInstAddr, boolean branchTaken) {

        // determine the index in the BHT for the branch instruction
        int idx = m_bhtModel.getIdxForAddress(branchInstAddr);

        // check if the prediction is correct
        boolean correctPrediction = m_bhtModel.getPredictionAtIdx(idx) == branchTaken;

        m_gui.getTabBHT().setSelectionBackground(correctPrediction ? BHTSimGUI.COLOR_PREDICTION_CORRECT : BHTSimGUI.COLOR_PREDICTION_INCORRECT);

        // add some output at the log
        m_gui.getTaLog().append("branch " + (branchTaken ? "taken" : "not taken") + ", prediction was " + (correctPrediction ? "correct" : "incorrect") + "\n\n");
        m_gui.getTaLog().setCaretPosition(m_gui.getTaLog().getDocument().getLength());

        // update the BHT -> causes refresh of the table
        m_bhtModel.updatePredictionAtIdx(idx, branchTaken);
    }

    /**
     * Extracts the target address of the branch.
     *
     * @param stmt the branch instruction
     * @return the address of the instruction that is executed if the branch is taken
     */
    protected static int extractBranchAddress(ProgramStatement stmt) {
        assert stmt.getInstruction() instanceof Branch: "Should only be called on branch instructions";
        int offset = stmt.getOperand(2);

        return stmt.getAddress() + (offset << 1);
    }


    /**
     * Callback for text segment access by the RISCV simulator.
     * <p>
     * The method is called each time the text segment is accessed to fetch the next instruction.
     * If the next instruction to execute was a branch instruction, the branch prediction is performed and visualized.
     * In case the last instruction was a branch instruction, the outcome of the branch prediction is analyzed and visualized.
     *
     * @param resource the observed resource
     * @param notice   signals the type of access (memory, register etc.)
     */
    protected void processRISCVUpdate(Observable resource, AccessNotice notice) {

        if (!notice.accessIsFromRISCV()) return;


        if (notice.getAccessType() == AccessNotice.READ && notice instanceof MemoryAccessNotice) {

            // now it is safe to make a cast of the notice
            MemoryAccessNotice memAccNotice = (MemoryAccessNotice) notice;

            try {
                // access the statement in the text segment without notifying other tools etc.
                ProgramStatement stmt = Memory.getInstance().getStatementNoNotify(memAccNotice.getAddress());

                // necessary to handle possible null pointers at the end of the program
                // (e.g., if the simulator tries to execute the next instruction after the last instruction in the text segment)
                if (stmt != null) {

                    boolean clearTextFields = true;

                    // first, check if there's a pending branch to handle
                    if (m_pendingBranchInstAddress != 0) {
                        handleExecBranchInst(m_pendingBranchInstAddress, m_lastBranchTaken);
                        clearTextFields = false;
                        m_pendingBranchInstAddress = 0;
                    }


                    // if current instruction is branch instruction
                    if (stmt.getInstruction() instanceof Branch) {
                        handlePreBranchInst(stmt);
                        m_lastBranchTaken = ((Branch) stmt.getInstruction()).willBranch(stmt);
                        m_pendingBranchInstAddress = stmt.getAddress();
                        clearTextFields = false;
                    }


                    // clear text fields and selection
                    if (clearTextFields) {
                        m_gui.getTfInstruction().setText("");
                        m_gui.getTfAddress().setText("");
                        m_gui.getTfIndex().setText("");
                        m_gui.getTabBHT().clearSelection();
                    }
                } else {
                    // check if there's a pending branch to handle
                    if (m_pendingBranchInstAddress != 0) {
                        handleExecBranchInst(m_pendingBranchInstAddress, m_lastBranchTaken);
                        m_pendingBranchInstAddress = 0;
                    }
                }
            } catch (AddressErrorException e) {
                // silently ignore these exceptions
            }

        }
    }
}
