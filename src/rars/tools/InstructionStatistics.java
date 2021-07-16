/*
Copyright (c) 2009,  Ingo Kofler, ITEC, Klagenfurt University, Austria

Developed by Ingo Kofler (ingo.kofler@itec.uni-klu.ac.at)
Based on the Instruction Counter tool by Felipe Lessa (felipe.lessa@gmail.com)

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
import rars.riscv.Instruction;
import rars.riscv.hardware.AccessNotice;
import rars.riscv.hardware.AddressErrorException;
import rars.riscv.hardware.Memory;
import rars.riscv.hardware.MemoryAccessNotice;
import rars.riscv.instructions.*;

import javax.swing.*;
import java.awt.*;
import java.util.Observable;


/**
 * A RARS tool for obtaining instruction statistics by instruction category.
 * <p>
 * The code of this tools is initially based on the Instruction counter tool by Felipe Lassa.
 *
 * @author Ingo Kofler <ingo.kofler@itec.uni-klu.ac.at>
 */
public class InstructionStatistics extends AbstractToolAndApplication {

    /**
     * name of the tool
     */
    private static String NAME = "Instruction Statistics";

    /**
     * version and author information of the tool
     */
    private static String VERSION = "Version 1.0 (Ingo Kofler)";

    /**
     * heading of the tool
     */
    private static String HEADING = "";


    /**
     * number of instruction categories used by this tool
     */
    private static final int MAX_CATEGORY = 5;

    /**
     * constant for ALU instructions category
     */
    private static final int CATEGORY_ALU = 0;

    /**
     * constant for jump instructions category
     */
    private static final int CATEGORY_JUMP = 1;

    /**
     * constant for branch instructions category
     */
    private static final int CATEGORY_BRANCH = 2;

    /**
     * constant for memory instructions category
     */
    private static final int CATEGORY_MEM = 3;

    /**
     * constant for any other instruction category
     */
    private static final int CATEGORY_OTHER = 4;


    /**
     * text field for visualizing the total number of instructions processed
     */
    private JTextField m_tfTotalCounter;

    /**
     * array of text field - one for each instruction category
     */
    private JTextField[] m_tfCounters;

    /**
     * array of progress pars - one for each instruction category
     */
    private JProgressBar[] m_pbCounters;


    /**
     * counter for the total number of instructions processed
     */
    private int m_totalCounter = 0;

    /**
     * array of counter variables - one for each instruction category
     */
    private int[] m_counters = new int[MAX_CATEGORY];

    /**
     * names of the instruction categories as array
     */
    private String[] m_categoryLabels = {"ALU", "Jump", "Branch", "Memory", "Other"};


    // From Felipe Lessa's instruction counter.  Prevent double-counting of instructions 
    // which happens because 2 read events are generated.   
    /**
     * The last address we saw. We ignore it because the only way for a
     * program to execute twice the same instruction is to enter an infinite
     * loop, which is not insteresting in the POV of counting instructions.
     */
    protected int lastAddress = -1;

    /**
     * Simple constructor, likely used to run a stand-alone enhanced instruction counter.
     *
     * @param title   String containing title for title bar
     * @param heading String containing text for heading shown in upper part of window.
     */
    public InstructionStatistics(String title, String heading) {
        super(title, heading);
    }


    /**
     * Simple construction, likely used by the RARS Tools menu mechanism.
     */
    public InstructionStatistics() {
        super(InstructionStatistics.NAME + ", " + InstructionStatistics.VERSION, InstructionStatistics.HEADING);
    }


    @Override
    public String getName() {
        return NAME;
    }


    /**
     * creates the display area for the tool as required by the API
     *
     * @return a panel that holds the GUI of the tool
     */
    protected JComponent buildMainDisplayArea() {

        // Create GUI elements for the tool
        JPanel panel = new JPanel(new GridBagLayout());

        m_tfTotalCounter = new JTextField("0", 10);
        m_tfTotalCounter.setEditable(false);

        m_tfCounters = new JTextField[MAX_CATEGORY];
        m_pbCounters = new JProgressBar[MAX_CATEGORY];

        // for each category a text field and a progress bar is created
        for (int i = 0; i < InstructionStatistics.MAX_CATEGORY; i++) {
            m_tfCounters[i] = new JTextField("0", 10);
            m_tfCounters[i].setEditable(false);
            m_pbCounters[i] = new JProgressBar(JProgressBar.HORIZONTAL);
            m_pbCounters[i].setStringPainted(true);
        }

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.gridheight = c.gridwidth = 1;

        // create the label and text field for the total instruction counter
        c.gridx = 2;
        c.gridy = 1;
        c.insets = new Insets(0, 0, 17, 0);
        panel.add(new JLabel("Total: "), c);
        c.gridx = 3;
        panel.add(m_tfTotalCounter, c);

        c.insets = new Insets(3, 3, 3, 3);

        // create label, text field and progress bar for each category
        for (int i = 0; i < InstructionStatistics.MAX_CATEGORY; i++) {
            c.gridy++;
            c.gridx = 2;
            panel.add(new JLabel(m_categoryLabels[i] + ":   "), c);
            c.gridx = 3;
            panel.add(m_tfCounters[i], c);
            c.gridx = 4;
            panel.add(m_pbCounters[i], c);
        }

        return panel;
    }


    /**
     * registers the tool as observer for the text segment of the program
     */
    protected void addAsObserver() {
        addAsObserver(Memory.textBaseAddress, Memory.textLimitAddress);
    }

    /**
     * decodes the instruction and determines the category of the instruction.
     * <p>
     * The instruction is decoded by checking the java instance of the instruction.
     * Supported instructions are RV32I, RV64I, M, and A extensions.
     *
     * @param instruction the instruction to decode
     * @return the category of the instruction
     * @see InstructionStatistics#CATEGORY_ALU
     * @see InstructionStatistics#CATEGORY_JUMP
     * @see InstructionStatistics#CATEGORY_BRANCH
     * @see InstructionStatistics#CATEGORY_MEM
     * @see InstructionStatistics#CATEGORY_OTHER
     * @author Giancarlo Pernudi Segura
     */
    protected int getInstructionCategory(Instruction instruction) {
        if (instruction instanceof Arithmetic)
            return InstructionStatistics.CATEGORY_ALU;      // add, addw, sub, subw, and, or, xor, slt, sltu, m extension
        if (instruction instanceof ADDI || instruction instanceof ADDIW || instruction instanceof ANDI
                || instruction instanceof ORI || instruction instanceof XORI
                || instruction instanceof SLTI || instruction instanceof SLTIU
                || instruction instanceof LUI || instruction instanceof AUIPC)
            return InstructionStatistics.CATEGORY_ALU;      // addi, addiw, andi, ori, xori, slti, sltiu, lui, auipc
        if (instruction instanceof SLLI || instruction instanceof SLLI64 || instruction instanceof SLLIW)
            return InstructionStatistics.CATEGORY_ALU;      // slli, slliw
        if (instruction instanceof SRLI || instruction instanceof SRLI64 || instruction instanceof SRLIW)
            return InstructionStatistics.CATEGORY_ALU;      // srli, srliw
        if (instruction instanceof SRAI || instruction instanceof SRAI64 || instruction instanceof SRAIW)
            return InstructionStatistics.CATEGORY_ALU;      // srai, sraiw
        if (instruction instanceof JAL || instruction instanceof JALR)
            return InstructionStatistics.CATEGORY_JUMP;     // jal, jalr
        if (instruction instanceof Branch)
            return InstructionStatistics.CATEGORY_BRANCH;   // beq, bge, bgeu, blt, bltu, bne
        if (instruction instanceof Load)
            return InstructionStatistics.CATEGORY_MEM;      // lb, lh, lwl, lw, lbu, lhu, lwr
        if (instruction instanceof Store)
            return InstructionStatistics.CATEGORY_MEM;      // sb, sh, swl, sw, swr
        if (instruction instanceof Atomic)
            return InstructionStatistics.CATEGORY_MEM;      // a extension

        return InstructionStatistics.CATEGORY_OTHER;
    }


    /**
     * method that is called each time the simulator accesses the text segment.
     * Before an instruction is executed by the simulator, the instruction is fetched from the program memory.
     * This memory access is observed and the corresponding instruction is decoded and categorized by the tool.
     * According to the category the counter values are increased and the display gets updated.
     *
     * @param resource the observed resource
     * @param notice   signals the type of access (memory, register etc.)
     */
    protected void processRISCVUpdate(Observable resource, AccessNotice notice) {

        if (!notice.accessIsFromRISCV())
            return;

        // check for a read access in the text segment
        if (notice.getAccessType() == AccessNotice.READ && notice instanceof MemoryAccessNotice) {

            // now it is safe to make a cast of the notice
            MemoryAccessNotice memAccNotice = (MemoryAccessNotice) notice;

            // The next three statments are from Felipe Lessa's instruction counter.  Prevents double-counting.
            int a = memAccNotice.getAddress();
            if (a == lastAddress)
                return;
            lastAddress = a;

            try {

                // access the statement in the text segment without notifying other tools etc.
                ProgramStatement stmt = Memory.getInstance().getStatementNoNotify(memAccNotice.getAddress());

                // necessary to handle possible null pointers at the end of the program
                // (e.g., if the simulator tries to execute the next instruction after the last instruction in the text segment)
                if (stmt != null) {
                    int category = getInstructionCategory(stmt.getInstruction());

                    m_totalCounter++;
                    m_counters[category]++;
                    updateDisplay();
                }
            } catch (AddressErrorException e) {
                // silently ignore these exceptions
            }
        }
    }


    /**
     * performs initialization tasks of the counters before the GUI is created.
     */
    protected void initializePreGUI() {
        m_totalCounter = 0;
        lastAddress = -1; // from Felipe Lessa's instruction counter tool
        for (int i = 0; i < InstructionStatistics.MAX_CATEGORY; i++)
            m_counters[i] = 0;
    }


    /**
     * resets the counter values of the tool and updates the display.
     */
    protected void reset() {
        m_totalCounter = 0;
        lastAddress = -1; // from Felipe Lessa's instruction counter tool
        for (int i = 0; i < InstructionStatistics.MAX_CATEGORY; i++)
            m_counters[i] = 0;
        updateDisplay();
    }


    /**
     * updates the text fields and progress bars according to the current counter values.
     */
    protected void updateDisplay() {
        m_tfTotalCounter.setText(String.valueOf(m_totalCounter));

        for (int i = 0; i < InstructionStatistics.MAX_CATEGORY; i++) {
            m_tfCounters[i].setText(String.valueOf(m_counters[i]));
            m_pbCounters[i].setMaximum(m_totalCounter);
            m_pbCounters[i].setValue(m_counters[i]);
        }
    }
}
