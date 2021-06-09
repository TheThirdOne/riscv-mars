package rars.venus.settings;

import rars.Globals;
import rars.riscv.hardware.MemoryConfiguration;
import rars.riscv.hardware.MemoryConfigurations;
import rars.riscv.hardware.Range;
import rars.simulator.Simulator;
import rars.util.Binary;
import rars.venus.FileStatus;
import rars.venus.GuiAction;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

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
 * Action class for the Settings menu item for text editor settings.
 */
public class SettingsMemoryConfigurationAction extends GuiAction {
    /**
     * Create a new SettingsEditorAction.  Has all the GuiAction parameters.
     */
    public SettingsMemoryConfigurationAction(String name, Icon icon, String descrip,
                                             Integer mnemonic, KeyStroke accel) {
        super(name, icon, descrip, mnemonic, accel);
    }

    /**
     * When this action is triggered, launch a dialog to view and modify
     * editor settings.
     */
    public void actionPerformed(ActionEvent e) {
        JDialog configDialog = new MemoryConfigurationDialog(Globals.getGui(), "Memory Configuration", true);
        configDialog.setVisible(true);
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    //   Private class to do all the work!
    //
    private class MemoryConfigurationDialog extends JDialog implements ActionListener {
        JTextField[] addressDisplay;
        JLabel[] nameDisplay;
        ConfigurationButton selectedConfigurationButton, initialConfigurationButton;

        private MemoryConfigurationDialog(Frame owner, String title, boolean modality) {
            super(owner, title, modality);
            this.setContentPane(buildDialogPanel());
            this.setDefaultCloseOperation(
                    JDialog.DO_NOTHING_ON_CLOSE);
            this.addWindowListener(
                    new WindowAdapter() {
                        public void windowClosing(WindowEvent we) {
                            performClose();
                        }
                    });
            this.pack();
            this.setLocationRelativeTo(owner);
        }

        private JPanel buildDialogPanel() {
            JPanel dialogPanel = new JPanel(new BorderLayout());
            dialogPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel configInfo = new JPanel(new FlowLayout());
            MemoryConfigurations.buildConfigurationCollection();
            configInfo.add(buildConfigChooser());
            configInfo.add(buildConfigDisplay());
            dialogPanel.add(configInfo);
            dialogPanel.add(buildControlPanel(), BorderLayout.SOUTH);
            return dialogPanel;
        }

        private Component buildConfigChooser() {
            JPanel chooserPanel = new JPanel(new GridLayout(4, 1));
            ButtonGroup choices = new ButtonGroup();
            Iterator<MemoryConfiguration> configurationsIterator = MemoryConfigurations.getConfigurationsIterator();
            while (configurationsIterator.hasNext()) {
                MemoryConfiguration config = configurationsIterator.next();
                ConfigurationButton button = new ConfigurationButton(config);
                button.addActionListener(this);
                if (button.isSelected()) {
                    this.selectedConfigurationButton = button;
                    this.initialConfigurationButton = button;
                }
                choices.add(button);
                chooserPanel.add(button);
            }
            chooserPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.BLACK)
                    , "Configuration"));
            return chooserPanel;
        }


        private Component buildConfigDisplay() {
            JPanel displayPanel = new JPanel();
            MemoryConfiguration config = MemoryConfigurations.getCurrentConfiguration();
            Map<String, Range> ranges = config.sections;
            int numItems = ranges.size()+2;
            JPanel namesPanel = new JPanel(new GridLayout(numItems, 1));
            JPanel valuesPanel = new JPanel(new GridLayout(numItems, 1));
            Font monospaced = new Font("Monospaced", Font.PLAIN, 12);
            nameDisplay = new JLabel[numItems];
            //   for (int i=numItems-1; i >= 0; i--) {
            //      namesPanel.add(new JLabel(configurationItemNames[i]));
            //   }
            addressDisplay = new JTextField[numItems];
            for (int i = 0; i < numItems; i++) {
                nameDisplay[i] = new JLabel();
                addressDisplay[i] = new JTextField();
                addressDisplay[i].setEditable(false);
                addressDisplay[i].setFont(monospaced);
            }
            // Display vertically from high to low memory addresses so
            // add the components in reverse order.
            for (int i = addressDisplay.length - 1; i >= 0; i--) {
                namesPanel.add(nameDisplay[i]);
                valuesPanel.add(addressDisplay[i]);
            }
            setConfigDisplay(config);
            Box columns = Box.createHorizontalBox();
            columns.add(valuesPanel);
            columns.add(Box.createHorizontalStrut(6));
            columns.add(namesPanel);
            displayPanel.add(columns);
            return displayPanel;
        }


        // Carry out action for the radio buttons.
        public void actionPerformed(ActionEvent e) {
            MemoryConfiguration config = ((ConfigurationButton) e.getSource()).getConfiguration();
            setConfigDisplay(config);
            this.selectedConfigurationButton = (ConfigurationButton) e.getSource();
        }


        // Row of control buttons to be placed along the button of the dialog
        private Component buildControlPanel() {
            Box controlPanel = Box.createHorizontalBox();
            JButton okButton = new JButton("Apply and Close");
            okButton.setToolTipText(SettingsHighlightingAction.CLOSE_TOOL_TIP_TEXT);
            okButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            performApply();
                            performClose();
                        }
                    });
            JButton applyButton = new JButton("Apply");
            applyButton.setToolTipText(SettingsHighlightingAction.APPLY_TOOL_TIP_TEXT);
            applyButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            performApply();
                        }
                    });
            JButton cancelButton = new JButton("Cancel");
            cancelButton.setToolTipText(SettingsHighlightingAction.CANCEL_TOOL_TIP_TEXT);
            cancelButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            performClose();
                        }
                    });
            JButton resetButton = new JButton("Reset");
            resetButton.setToolTipText(SettingsHighlightingAction.RESET_TOOL_TIP_TEXT);
            resetButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            performReset();
                        }
                    });
            JButton loadButton = new JButton("Load");
            loadButton.setToolTipText("Load a properties files that specifies a config. Check the wiki");
            loadButton.addActionListener(e -> {
                        JFileChooser choose = new JFileChooser();
                        if(JFileChooser.APPROVE_OPTION == choose.showOpenDialog(this)){
                            System.out.println("You chose to open this file: " + choose.getSelectedFile().getName());
                            try {
                                MemoryConfiguration mc = MemoryConfigurations.loadNewConfig(new FileInputStream(choose.getSelectedFile()));
                                if(MemoryConfigurations.getConfigurationByName(mc.getConfigurationName()) == null){
                                    MemoryConfigurations.addNewConfig(mc);
                                    this.setContentPane(buildDialogPanel());
                                    this.setVisible(true);
                                }
                            } catch (FileNotFoundException ex) {
                                return;
                            }
                        }
                    });
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(okButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(applyButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(cancelButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(resetButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(loadButton);
            controlPanel.add(Box.createHorizontalGlue());
            return controlPanel;
        }

        private void performApply() {
            if (MemoryConfigurations.setCurrentConfiguration(this.selectedConfigurationButton.getConfiguration())) {
                Globals.getSettings().setMemoryConfiguration(this.selectedConfigurationButton.getConfiguration().getConfigurationIdentifier());
                Globals.getGui().getRegistersPane().getRegistersWindow().clearHighlighting();
                Globals.getGui().getRegistersPane().getRegistersWindow().updateRegisters();
                Globals.getGui().getMainPane().getExecutePane().getDataSegmentWindow().updateBaseAddressComboBox();
                // 21 July 2009 Re-assemble if the situation demands it to maintain consistency.
                if (FileStatus.get() == FileStatus.RUNNABLE ||
                        FileStatus.get() == FileStatus.RUNNING ||
                        FileStatus.get() == FileStatus.TERMINATED) {
                    // Stop execution if executing -- should NEVER happen because this
                    // Action's widget is disabled during MIPS execution.
                    if (FileStatus.get() == FileStatus.RUNNING) {
                        Simulator.getInstance().stopExecution();
                    }
                    Globals.getGui().getRunAssembleAction().actionPerformed(null);
                }
            }
        }

        private void performClose() {
            this.setVisible(false);
            this.dispose();
        }

        private void performReset() {
            this.selectedConfigurationButton = this.initialConfigurationButton;
            this.selectedConfigurationButton.setSelected(true);
            setConfigDisplay(this.selectedConfigurationButton.getConfiguration());
        }


        // Set name values in JLabels and address values in the JTextFields
        private void setConfigDisplay(MemoryConfiguration config) {
            Map<String, Range> ranges = config.sections;
            TreeMap<String, String> treeSortedByAddress = new TreeMap<>();
            String rangestring="";
            for (Map.Entry<String,Range> entry: ranges.entrySet()) {
                rangestring = Binary.intToHexString(entry.getValue().low) + "-" + Binary.intToHexString(entry.getValue().high);
                treeSortedByAddress.put(rangestring,entry.getKey());
            }
            treeSortedByAddress.put(Binary.intToHexString(config.extern_size + config.data.low),"Default .data start");
            treeSortedByAddress.put(Binary.intToHexString(config.gp_offset + config.data.low),"Global pointer");

            // Will use TreeMap to extract list of address-name pairs sorted by
            // hex-stringified address. This will correctly handle kernel addresses,
            // whose int values are negative and thus normal sorting yields incorrect
            // results.
            Iterator<Map.Entry<String, String>> setSortedByAddress = treeSortedByAddress.entrySet().iterator();
            Map.Entry<String, String> pair;
            for (int i = 0; i < treeSortedByAddress.size(); i++) {
                pair = setSortedByAddress.next();
                nameDisplay[i].setText(pair.getValue());
                addressDisplay[i].setText(pair.getKey());
            }
        }

    }

    // Handy class to connect button to its configuration...
    private class ConfigurationButton extends JRadioButton {
        private MemoryConfiguration configuration;

        public ConfigurationButton(MemoryConfiguration config) {
            super(config.getConfigurationName(), config == MemoryConfigurations.getCurrentConfiguration());
            this.configuration = config;
        }

        public MemoryConfiguration getConfiguration() {
            return configuration;
        }

    }

}