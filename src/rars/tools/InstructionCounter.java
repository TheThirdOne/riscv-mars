/*
Copyright (c) 2008,  Felipe Lessa

Developed by Felipe Lessa (felipe.lessa@gmail.com)

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
import rars.riscv.hardware.AccessNotice;
import rars.riscv.hardware.AddressErrorException;
import rars.riscv.hardware.Memory;
import rars.riscv.hardware.MemoryAccessNotice;
import rars.riscv.BasicInstruction;
import rars.riscv.BasicInstructionFormat;

import javax.swing.*;
import java.awt.*;
import java.util.Observable;

/**
 * Instruction counter tool. Can be used to know how many instructions
 * were executed to complete a given program.
 * <p>
 * Code slightly based on MemoryReferenceVisualization.
 *
 * @author Felipe Lessa <felipe.lessa@gmail.com>
 */
public class InstructionCounter extends AbstractToolAndApplication {
    private static String name = "Instruction Counter";
    private static String version = "Version 1.0 (Felipe Lessa)";
    private static String heading = "Counting the number of instructions executed";

    /**
     * Number of instructions executed until now.
     */
    private int counter = 0;
    private JTextField counterField;

    /**
     * Number of instructions of type R.
     */
    private int counterR = 0;
    private JTextField counterRField;
    private JProgressBar progressbarR;
    
    /**
     * Number of instructions of type R4.
     */
    private int counterR4 = 0;
    private JTextField counterR4Field;
    private JProgressBar progressbarR4;

    /**
     * Number of instructions of type I.
     */
    private int counterI = 0;
    private JTextField counterIField;
    private JProgressBar progressbarI;

    /**
     * Number of instructions of type S.
     */
    private int counterS = 0;
    private JTextField counterSField;
    private JProgressBar progressbarS;

    /**
     * Number of instructions of type B.
     */
    private int counterB = 0;
    private JTextField counterBField;
    private JProgressBar progressbarB;
    
    /**
     * Number of instructions of type U.
     */
    private int counterU = 0;
    private JTextField counterUField;
    private JProgressBar progressbarU;

    /**
     * Number of instructions of type J.
     */
    private int counterJ = 0;
    private JTextField counterJField;
    private JProgressBar progressbarJ;
    
    /**
     * The last address we saw. We ignore it because the only way for a
     * program to execute twice the same instruction is to enter an infinite
     * loop, which is not insteresting in the POV of counting instructions.
     */
    private int lastAddress = -1;

    /**
     * Simple constructor, likely used to run a stand-alone memory reference visualizer.
     *
     * @param title   String containing title for title bar
     * @param heading String containing text for heading shown in upper part of window.
     */
    public InstructionCounter(String title, String heading) {
        super(title, heading);
    }

    /**
     * Simple construction, likely used by the RARS Tools menu mechanism.
     */
    public InstructionCounter() {
        super(name + ", " + version, heading);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    protected JComponent buildMainDisplayArea() {
        // Create everything
        JPanel panel = new JPanel(new GridBagLayout());

        counterField = new JTextField("0", 10);
        counterField.setEditable(false);

        counterRField = new JTextField("0", 10);
        counterRField.setEditable(false);
        progressbarR = new JProgressBar(JProgressBar.HORIZONTAL);
        progressbarR.setStringPainted(true);
        
        counterR4Field = new JTextField("0", 10);
        counterR4Field.setEditable(false);
        progressbarR4 = new JProgressBar(JProgressBar.HORIZONTAL);
        progressbarR4.setStringPainted(true);

        counterIField = new JTextField("0", 10);
        counterIField.setEditable(false);
        progressbarI = new JProgressBar(JProgressBar.HORIZONTAL);
        progressbarI.setStringPainted(true);

        counterSField = new JTextField("0", 10);
        counterSField.setEditable(false);
        progressbarS = new JProgressBar(JProgressBar.HORIZONTAL);
        progressbarS.setStringPainted(true);

        counterBField = new JTextField("0", 10);
        counterBField.setEditable(false);
        progressbarB = new JProgressBar(JProgressBar.HORIZONTAL);
        progressbarB.setStringPainted(true);
        
        counterUField = new JTextField("0", 10);
        counterUField.setEditable(false);
        progressbarU = new JProgressBar(JProgressBar.HORIZONTAL);
        progressbarU.setStringPainted(true);
        
        counterJField = new JTextField("0", 10);
        counterJField.setEditable(false);
        progressbarJ = new JProgressBar(JProgressBar.HORIZONTAL);
        progressbarJ.setStringPainted(true);

        // Add them to the panel

        // Fields
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.gridheight = c.gridwidth = 1;
        c.gridx = 3;
        c.gridy = 1;
        c.insets = new Insets(0, 0, 17, 0);
        panel.add(counterField, c);

        c.insets = new Insets(0, 0, 0, 0);
        c.gridy++;
        panel.add(counterRField, c);

        c.gridy++;
        panel.add(counterR4Field, c);
        
        c.gridy++;
        panel.add(counterIField, c);

        c.gridy++;
        panel.add(counterSField, c);
        
        c.gridy++;
        panel.add(counterBField, c);

        c.gridy++;
        panel.add(counterUField, c);
        
        c.gridy++;
        panel.add(counterJField, c);

        // Labels
        c.anchor = GridBagConstraints.LINE_END;
        c.gridx = 1;
        c.gridwidth = 2;
        c.gridy = 1;
        c.insets = new Insets(0, 0, 17, 0);
        panel.add(new JLabel("Instructions so far: "), c);

        c.insets = new Insets(0, 0, 0, 0);
        c.gridx = 2;
        c.gridwidth = 1;
        c.gridy++;
        panel.add(new JLabel("R-type: "), c);
        
        c.gridy++;
        panel.add(new JLabel("R4-type: "), c);

        c.gridy++;
        panel.add(new JLabel("I-type: "), c);

        c.gridy++;
        panel.add(new JLabel("S-type: "), c);
        
        c.gridy++;
        panel.add(new JLabel("B-type: "), c);

        c.gridy++;
        panel.add(new JLabel("U-type: "), c);
        
        c.gridy++;
        panel.add(new JLabel("J-type: "), c);

        // Progress bars
        c.insets = new Insets(3, 3, 3, 3);
        c.gridx = 4;
        c.gridy = 2;
        panel.add(progressbarR, c);

        c.gridy++;
        panel.add(progressbarR4, c);
        
        c.gridy++;
        panel.add(progressbarI, c);

        c.gridy++;
        panel.add(progressbarS, c);
        
        c.gridy++;
        panel.add(progressbarB, c);

        c.gridy++;
        panel.add(progressbarU, c);
        
        c.gridy++;
        panel.add(progressbarJ, c);

        return panel;
    }

    @Override
    protected void addAsObserver() {
        addAsObserver(Memory.configuration.text.low,Memory.configuration.text.high);
    }

    @Override
    protected void processRISCVUpdate(Observable resource, AccessNotice notice) {
        if (!notice.accessIsFromRISCV()) return;
        if (notice.getAccessType() != AccessNotice.READ) return;
        MemoryAccessNotice m = (MemoryAccessNotice) notice;
        int a = m.getAddress();
        if (a == lastAddress) return;
        lastAddress = a;
        counter++;
        try {
            ProgramStatement stmt = Memory.getInstance().getStatement(a);
            
            // If the program is finished, getStatement() will return null,
            // a null statement will cause the simulator to stall.
            if(stmt != null) {
	            BasicInstruction instr = (BasicInstruction) stmt.getInstruction();
	            BasicInstructionFormat format = instr.getInstructionFormat();
	            if (format == BasicInstructionFormat.R_FORMAT)
	                counterR++;
	            else if (format == BasicInstructionFormat.R4_FORMAT)
	                counterR4++;
	            else if (format == BasicInstructionFormat.I_FORMAT)
	                counterI++;
	            else if (format == BasicInstructionFormat.S_FORMAT)
	                counterS++;
	            else if(format == BasicInstructionFormat.B_FORMAT)
	            	counterB++;
	            else if (format == BasicInstructionFormat.U_FORMAT)
	                counterU++;
	            else if(format == BasicInstructionFormat.J_FORMAT)
	            	counterJ++;
            }
        } catch (AddressErrorException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        updateDisplay();
    }

    @Override
    protected void initializePreGUI() {
        counter = counterR = counterR4 = counterI = counterS = counterB = counterU = counterJ = 0;
        lastAddress = -1;
    }

    @Override
    protected void reset() {
        counter = counterR = counterR4 = counterI = counterS = counterB = counterU = counterJ = 0;
        lastAddress = -1;
        updateDisplay();
    }

    @Override
    protected void updateDisplay() {
        counterField.setText(String.valueOf(counter));

        counterRField.setText(String.valueOf(counterR));
        progressbarR.setMaximum(counter);
        progressbarR.setValue(counterR);
        
        counterR4Field.setText(String.valueOf(counterR4));
        progressbarR4.setMaximum(counter);
        progressbarR4.setValue(counterR4);

        counterIField.setText(String.valueOf(counterI));
        progressbarI.setMaximum(counter);
        progressbarI.setValue(counterI);

        counterSField.setText(String.valueOf(counterS));
        progressbarS.setMaximum(counter);
        progressbarS.setValue(counterS);
        
        counterBField.setText(String.valueOf(counterB));
        progressbarB.setMaximum(counter);
        progressbarB.setValue(counterB);

        counterUField.setText(String.valueOf(counterU));
        progressbarU.setMaximum(counter);
        progressbarU.setValue(counterU);

        counterJField.setText(String.valueOf(counterJ));
        progressbarJ.setMaximum(counter);
        progressbarJ.setValue(counterJ);
        
        if (counter == 0) {
            progressbarR.setString("0%");
            progressbarR4.setString("0%");
            progressbarI.setString("0%");
            progressbarS.setString("0%");
            progressbarB.setString("0%");
            progressbarU.setString("0%");
            progressbarJ.setString("0%");
        } else {
            progressbarR.setString((counterR * 100) / counter + "%");
            progressbarR4.setString((counterR4 * 100) / counter + "%");
            progressbarI.setString((counterI * 100) / counter + "%");
            progressbarS.setString((counterS * 100) / counter + "%");
            progressbarB.setString((counterB * 100) / counter + "%");
            progressbarU.setString((counterU * 100) / counter + "%");
            progressbarJ.setString((counterJ * 100) / counter + "%");
        }
    }
}
