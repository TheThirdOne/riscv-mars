package rars.venus;

import rars.Globals;
import rars.riscv.dump.DumpFormat;
import rars.riscv.dump.DumpFormatLoader;
import rars.riscv.hardware.AddressErrorException;
import rars.riscv.hardware.Memory;
import rars.riscv.hardware.Range;
import rars.util.Binary;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

	/*
Copyright (c) 2003-2008,  Pete Sanderson and Kenneth Vollmar

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
 * Action  for the File -> Save For Dump Memory menu item
 */
public class FileDumpMemoryAction extends GuiAction {

    private JDialog dumpDialog;
    private static final String title = "Dump Memory To File";

    private int[] segmentListBaseArray;
    private int[] segmentListHighArray;

    private JComboBox<String> segmentListSelector;
    private JComboBox formatListSelector;

    private VenusUI mainUI;

    public FileDumpMemoryAction(String name, Icon icon, String descrip,
                                Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel);
        mainUI = gui;
    }


    public void actionPerformed(ActionEvent e) {
        dumpMemory();
    }

    /* Save the memory segment in a supported format.
   */
    private void dumpMemory() {
        dumpDialog = createDumpDialog();
        dumpDialog.pack();
        dumpDialog.setLocationRelativeTo(Globals.getGui());
        dumpDialog.setVisible(true);
    }


    // The dump dialog that appears when menu item is selected.
    private JDialog createDumpDialog() {
        JDialog dumpDialog = new JDialog(Globals.getGui(), title, true);
        dumpDialog.setContentPane(buildDialogPanel());
        dumpDialog.setDefaultCloseOperation(
                JDialog.DO_NOTHING_ON_CLOSE);
        dumpDialog.addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent we) {
                        closeDialog();
                    }
                });
        return dumpDialog;
    }

    // Set contents of dump dialog.
    private JPanel buildDialogPanel() {
        JPanel contents = new JPanel(new BorderLayout(20, 20));
        contents.setBorder(new EmptyBorder(10, 10, 10, 10));

        // A series of parallel arrays representing the memory segments that can be dumped.
        String[] segmentArray = new String[]{".text", ".data"};
        Range[] segments = new Range[]{Memory.configuration.text,Memory.configuration.data};
        int[] highAddressArray = new int[segmentArray.length];


        // These three are allocated and filled by buildDialogPanel() and used by action listeners.
        String[] segmentListArray = new String[segmentArray.length];
        segmentListBaseArray = new int[segmentArray.length];
        segmentListHighArray = new int[segmentArray.length];

        // Calculate the actual highest address to be dumped.  For text segment, this depends on the
        // program length (number of machine code instructions).  For data segment, this depends on
        // how many MARS 4K word blocks have been referenced during assembly and/or execution.
        // Then generate label from concatentation of segmentArray[i], baseAddressArray[i]
        // and highAddressArray[i].  This lets user know exactly what range will be dumped.  Initially not
        // editable but maybe add this later.
        // If there is nothing to dump (e.g. address of first null == base address), then
        // the segment will not be listed.
        int segmentCount = 0;

        for (int i = 0; i < segmentArray.length; i++) {
            try {
                highAddressArray[i] = Globals.memory.getAddressOfFirstNull(segments[i].low, segments[i].high) - Memory.WORD_LENGTH_BYTES;

            }  // Exception will not happen since the Memory base and limit addresses are on word boundaries!
            catch (AddressErrorException aee) {
                highAddressArray[i] = segments[i].low - Memory.WORD_LENGTH_BYTES;
            }
            if (highAddressArray[i] >= segments[i].low) {
                segmentListBaseArray[segmentCount] = segments[i].low;
                segmentListHighArray[segmentCount] = segments[i].high;
                segmentListArray[segmentCount] =
                        segmentArray[i] + " (" + Binary.intToHexString(segments[i].low) +
                                " - " + Binary.intToHexString(segments[i].high) + ")";
                segmentCount++;
            }
        }

        // It is highly unlikely that no segments remain after the null check, since
        // there will always be at least one instruction (.text segment has one non-null).
        // But just in case...
        if (segmentCount == 0) {
            contents.add(new Label("There is nothing to dump!"), BorderLayout.NORTH);
            JButton OKButton = new JButton("OK");
            OKButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            closeDialog();
                        }
                    });
            contents.add(OKButton, BorderLayout.SOUTH);
            return contents;
        }

        // This is needed to assure no null array elements in ComboBox list.
        if (segmentCount < segmentListArray.length) {
            String[] tempArray = new String[segmentCount];
            System.arraycopy(segmentListArray, 0, tempArray, 0, segmentCount);
            segmentListArray = tempArray;
        }

        // Create segment selector.  First element selected by default.
        segmentListSelector = new JComboBox<>(segmentListArray);
        segmentListSelector.setSelectedIndex(0);
        JPanel segmentPanel = new JPanel(new BorderLayout());
        segmentPanel.add(new Label("Memory Segment"), BorderLayout.NORTH);
        segmentPanel.add(segmentListSelector);
        contents.add(segmentPanel, BorderLayout.WEST);

        // Next, create list of all available dump formats.
        ArrayList<DumpFormat> dumpFormats = DumpFormatLoader.getDumpFormats();
        formatListSelector = new JComboBox(dumpFormats.toArray());
        formatListSelector.setRenderer(new DumpFormatComboBoxRenderer(formatListSelector));
        formatListSelector.setSelectedIndex(0);
        JPanel formatPanel = new JPanel(new BorderLayout());
        formatPanel.add(new Label("Dump Format"), BorderLayout.NORTH);
        formatPanel.add(formatListSelector);
        contents.add(formatPanel, BorderLayout.EAST);

        // Bottom row - the control buttons for Dump and Cancel
        Box controlPanel = Box.createHorizontalBox();
        JButton dumpButton = new JButton("Dump To File...");
        dumpButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (performDump(segmentListBaseArray[segmentListSelector.getSelectedIndex()],
                                segmentListHighArray[segmentListSelector.getSelectedIndex()],
                                (DumpFormat) formatListSelector.getSelectedItem())) {
                            closeDialog();
                        }
                    }
                });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        closeDialog();
                    }
                });
        controlPanel.add(Box.createHorizontalGlue());
        controlPanel.add(dumpButton);
        controlPanel.add(Box.createHorizontalGlue());
        controlPanel.add(cancelButton);
        controlPanel.add(Box.createHorizontalGlue());
        contents.add(controlPanel, BorderLayout.SOUTH);
        return contents;
    }

    // User has clicked "Dump" button, so launch a file chooser then get
    // segment (memory range) and format selections and save to the file.
    private boolean performDump(int firstAddress, int lastAddress, DumpFormat format) {
        File theFile;
        JFileChooser saveDialog;
        boolean operationOK = false;

        saveDialog = new JFileChooser(mainUI.getEditor().getCurrentSaveDirectory());
        saveDialog.setDialogTitle(title);
        while (!operationOK) {
            int decision = saveDialog.showSaveDialog(mainUI);
            if (decision != JFileChooser.APPROVE_OPTION) {
                return false;
            }
            theFile = saveDialog.getSelectedFile();
            operationOK = true;
            if (theFile.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(mainUI,
                        "File " + theFile.getName() + " already exists.  Do you wish to overwrite it?",
                        "Overwrite existing file?",
                        JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                switch (overwrite) {
                    case JOptionPane.YES_OPTION:
                        operationOK = true;
                        break;
                    case JOptionPane.NO_OPTION:
                        operationOK = false;
                        break;
                    case JOptionPane.CANCEL_OPTION:
                        return false;
                    default: // should never occur
                        return false;
                }
            }
            if (operationOK) {
                try {
                    format.dumpMemoryRange(theFile, firstAddress, lastAddress,Globals.memory);
                } catch (AddressErrorException aee) {

                } catch (IOException ioe) {
                }
            }
        }
        return true;
    }

    // We're finished with this modal dialog.
    private void closeDialog() {
        dumpDialog.setVisible(false);
        dumpDialog.dispose();
    }


    // Display tool tip for dump format list items.  Got the technique from
    // http://forum.java.sun.com/thread.jspa?threadID=488762&messageID=2292482

    private class DumpFormatComboBoxRenderer extends BasicComboBoxRenderer {
        private JComboBox myMaster;

        public DumpFormatComboBoxRenderer(JComboBox myMaster) {
            super();
            this.myMaster = myMaster;
        }

        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setToolTipText(value.toString());
            if (index >= 0 && ((DumpFormat) (myMaster.getItemAt(index))).getDescription() != null) {
                setToolTipText(((DumpFormat) (myMaster.getItemAt(index))).getDescription());
            }
            return this;
        }
    }


}