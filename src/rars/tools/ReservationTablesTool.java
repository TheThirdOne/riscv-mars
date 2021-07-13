package rars.tools;

import rars.Globals;
import rars.riscv.hardware.AddressErrorException;
import rars.riscv.hardware.RegisterFile;
import rars.riscv.hardware.ReservationTable.bitWidth;
import rars.venus.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.border.TitledBorder;

/*
Copyright (c) 2021, Giancarlo Pernudi Segura & Siva Chowdeswar Nandipati.

Developed by Giancarlo Pernudi Segura (pernudi@ualberta.ca) & Siva Chowdeswar Nandipati (sivachow@ualberta.ca).

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

public class ReservationTablesTool extends AbstractToolAndApplication {
    private static String heading = "Reservation Table Tool";
    private static String version = "Version 1.0";
    private static String displayPanelTitle;
    private JPanel hartPanel;
    private JComboBox<Integer> hartWindowSelector;
    protected ArrayList<GeneralVenusUI> hartWindows;
    private JTable reservations;

    public ReservationTablesTool() {
        super(heading + ", " + version, heading);
        hartWindows = Globals.getHartWindows();
        Globals.reservationTables.addObserver(this);
    }

    protected JComponent buildMainDisplayArea() {
        JPanel panelTools = new JPanel(new BorderLayout());
        hartPanel = new JPanel(new BorderLayout());
        String[] columns = new String[Globals.reservationTables.harts * 2];
        for (int i = 0; i < Globals.reservationTables.harts; i++) {
            columns[i * 2] = String.format("Hart %d", i);
            columns[i * 2 + 1] = "Length";
        }
        reservations = new JTable(Globals.reservationTables.getAllAddressesAsStrings(), columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        hartPanel.add(reservations.getTableHeader(), BorderLayout.NORTH);
        reservations.setCellSelectionEnabled(true);
        reservations.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reservations.getTableHeader().setReorderingAllowed(false);
        hartPanel.add(reservations);

        TitledBorder tb = new TitledBorder(displayPanelTitle);
        tb.setTitleJustification(TitledBorder.CENTER);
        panelTools.setBorder(tb);

        Box displayOptions = Box.createHorizontalBox();
        hartWindowSelector = new JComboBox<>(SelectHartWindow());
        hartWindowSelector.setToolTipText("Technique for determining simulated transmitter device processing delay");
        hartWindowSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        int i = hartWindowSelector.getSelectedIndex();
                        hartWindows = Globals.getHartWindows();
                        Globals.setHartWindows();
                        if (i == 0)
                            return;
                        else
                            hartWindows.get(i-1).setVisible(true);
                    }
                });

        JButton clearButton = new JButton("Clear Selected");
        clearButton.setToolTipText("Clear the Selected from the Reserve Table");
        clearButton.addActionListener(l -> {
            if (connectButton.isConnected()) {
                int row = reservations.getSelectedRow();
                int col = reservations.getSelectedColumn();
                if(row < 0 || col < 0)
                    return;
                if (col % 2 == 1)
                    col--;
                int address = Integer.parseInt(reservations.getValueAt(row, col)
                    .toString().substring(2), 16);
                try {
                    Globals.reservationTables.unreserveAddress(col, address, bitWidth.word);
                } catch (AddressErrorException e) {
                    e.printStackTrace();
                }
            }
            reservations.clearSelection();
            updateDisplay();
        });
        JButton btnPlus = new JButton("+");
        JButton btnMinus = new JButton("-");
        btnPlus.addActionListener(l -> {
            Globals.setHarts(1);
            super.dialog.dispose();
            RegisterFile.initGRegisterBlock();
            RegisterFile.initProgramCounter();
            action();
            
        });
        btnMinus.addActionListener(l -> {
            Globals.setHarts(-1);
            super.dialog.dispose();
            RegisterFile.initGRegisterBlock();
            RegisterFile.initProgramCounter();
            action();
        });
        displayOptions.add(Box.createHorizontalGlue());
        displayOptions.add(btnMinus);
        displayOptions.add(hartWindowSelector);
        displayOptions.add(btnPlus);
        clearButton.addKeyListener(new EnterKeyListener(clearButton));
        displayOptions.add(Box.createHorizontalGlue());
        displayOptions.add(clearButton);
        displayOptions.add(Box.createHorizontalGlue());

        JSplitPane both = new JSplitPane(JSplitPane.VERTICAL_SPLIT, hartPanel, displayOptions);
        both.setResizeWeight(0.5);
        panelTools.add(both);
        return panelTools;
    }


    @Override
    public String getName() {
        return heading;
    }

    protected JComponent getHelpComponent() {
        final String helpContent = "Use this tool to simulate atomic operations such as store conditional.\n"
                + "While this tool is connected to the program, the table below shows the\n"
                + "reservation table for each hart. Addresses reserved by a hart\n"
                + "will appear under that hart's column. You can release an address,\n"
                + "which will release that address across all the hart's tables in\n"
                + "order to simulate some other hart performing a store conditional.\n"
                + "(contributed by Giancarlo Pernudi Segura, pernudi@ualberta.ca) &" 
                + "\n Siva Chowdeswar Nandipati (sivachow@ualberta.ca)";
        JButton help = new JButton("Help");
        help.addActionListener(l -> {
            JOptionPane.showMessageDialog(theWindow, helpContent);
        });
        return help;
    }

    @Override
    protected void updateDisplay() {
        String[][] addresses = Globals.reservationTables.getAllAddressesAsStrings();
        for (int i = 0; i < addresses.length; i++) {
            for (int j = 0; j < addresses[i].length; j++) {
                reservations.setValueAt(addresses[i][j], i, j);
            }
        }
    }

    @Override
    protected void reset() {
            Globals.reservationTables.reset();
            updateDisplay();
    }

    private Integer[] SelectHartWindow() {
        Integer hartChoser[];
        hartChoser = new Integer[(Integer) Globals.getHarts()];
        for (int i = 0; i < Globals.getHarts(); i++) {
            hartChoser[i] = i;
        }
        return hartChoser;
    }
}
