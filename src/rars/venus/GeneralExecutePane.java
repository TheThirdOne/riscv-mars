package rars.venus;

import rars.Globals;
import rars.Settings;
import rars.venus.registers.ControlAndStatusWindow;
import rars.venus.registers.FloatingPointWindow;
import rars.venus.registers.RegistersWindow;

import javax.swing.*;
import java.awt.*;

/*
Copyright (c) 2003-2006,  Siva Chowdeswar Nandipati

Developed by Siva Chowdeswar Nandipati (sivachow@ualberta.ca)

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
 * Container for the execution-related windows.  Currently displayed as a tabbed pane.
 *
 * @author Sanderson and Team JSpim
 **/

public class GeneralExecutePane extends JDesktopPane {
    private RegistersWindow registerValues;
    private FloatingPointWindow fpRegValues;
    private ControlAndStatusWindow csrValues;
    private TextSegmentWindow textSegment;
    private LabelsWindow labelValues;
    private GeneralVenusUI mainUI;
    private NumberDisplayBaseChooser valueDisplayBase;
    private NumberDisplayBaseChooser addressDisplayBase;
    private boolean labelWindowVisible;
    private int hart;

    /**
     * initialize the Execute pane with major components
     *
     * @param mainUI  the parent GUI
     * @param regs    window containing integer register set
     * @param fpRegs  window containing floating point register set
     * @param csrRegs window containing the CSR set
     */

    public GeneralExecutePane(GeneralVenusUI mainUI, RegistersWindow regs, FloatingPointWindow fpRegs, ControlAndStatusWindow csrRegs, int hart) {
        this.mainUI = mainUI;
        // Although these are displayed in Data Segment, they apply to all three internal
        // windows within the Execute pane.  So they will be housed here.
        addressDisplayBase = new NumberDisplayBaseChooser("Hexadecimal Addresses",
                Globals.getSettings().getBooleanSetting(Settings.Bool.DISPLAY_ADDRESSES_IN_HEX));
        valueDisplayBase = new NumberDisplayBaseChooser("Hexadecimal Values",
                Globals.getSettings().getBooleanSetting(Settings.Bool.DISPLAY_VALUES_IN_HEX));//VenusUI.DEFAULT_NUMBER_BASE);
        this.hart = hart;
        registerValues = regs;
        fpRegValues = fpRegs;
        csrValues = csrRegs;
        textSegment = new TextSegmentWindow();
        labelValues = new LabelsWindow();
        labelWindowVisible = Globals.getSettings().getBooleanSetting(Settings.Bool.LABEL_WINDOW_VISIBILITY);
        this.add(textSegment);  // these 3 LOC moved up.  DPS 3-Sept-2014
        textSegment.pack();   // these 3 LOC added.  DPS 3-Sept-2014
        textSegment.setVisible(true);
        labelValues.setVisible(labelWindowVisible);

    }

    /**
     * This method will set the bounds of this JDesktopPane's internal windows
     * relative to the current size of this JDesktopPane.  Such an operation
     * cannot be adequately done at constructor time because the actual
     * size of the desktop pane window is not yet established.  Layout manager
     * is not a good option here because JDesktopPane does not work well with
     * them (the whole idea of using JDesktopPane with internal frames is to
     * have mini-frames that you can resize, move around, minimize, etc).  This
     * method should be invoked only once: the first time the Execute tab is
     * selected (a change listener invokes it).  We do not want it invoked
     * on subsequent tab selections; otherwise, user manipulations of the
     * internal frames would be lost the next time execute tab is selected.
     */
    public void setWindowBounds() {

        int fullWidth = this.getSize().width - this.getInsets().left - this.getInsets().right;
        int fullHeight = this.getSize().height;
        int halfHeight = fullHeight / 2;
        Dimension textDim = new Dimension((int) (fullWidth * .75), halfHeight);
        Dimension lablDim = new Dimension((int) (fullWidth * .25), halfHeight);
        Dimension textFullDim = new Dimension((int) (fullWidth), halfHeight);

        if (labelWindowVisible) {
            textSegment.setBounds(0, 0, textDim.width, textDim.height);
            labelValues.setBounds(textDim.width + 1, 0, lablDim.width, lablDim.height);
        } else {
            textSegment.setBounds(0, 0, textFullDim.width, textFullDim.height);
            labelValues.setBounds(0, 0, 0, 0);
        }
    }

    /**
     * Show or hide the label window (symbol table).  If visible, it is displayed
     * to the right of the text segment and the latter is shrunk accordingly.
     *
     * @param visibility set to true or false
     */

    public void setLabelWindowVisibility(boolean visibility) {
        if (!visibility && labelWindowVisible) {
            labelWindowVisible = false;
            textSegment.setVisible(false);
            labelValues.setVisible(false);
            setWindowBounds();
            textSegment.setVisible(true);
        } else if (visibility && !labelWindowVisible) {
            labelWindowVisible = true;
            textSegment.setVisible(false);
            setWindowBounds();
            textSegment.setVisible(true);
            labelValues.setVisible(false);
        }
    }

    /**
     * Clears out all components of the Execute tab: text segment
     * display, data segment display, label display and register display.
     * This will typically be done upon File->Close, Open, New.
     */

    public void clearPane() {
        this.getTextSegmentWindow().clearWindow();
        this.getRegistersWindow().clearWindow();
        this.getFloatingPointWindow().clearWindow();
        this.getControlAndStatusWindow().clearWindow();
        this.getLabelsWindow().clearWindow();
        // seems to be required, to display cleared Execute tab contents...
        if (mainUI.getMainPane().getSelectedComponent() == this) {
            mainUI.getMainPane().setSelectedComponent(this);
        }
    }

    /**
     * Access the text segment window.
     */
    public TextSegmentWindow getTextSegmentWindow() {
        return textSegment;
    }

    /**
     * Access the register values window.
     */
    public RegistersWindow getRegistersWindow() {
        return registerValues;
    }

    /**
     * Access the floating point values window.
     */
    public FloatingPointWindow getFloatingPointWindow() {
        return fpRegValues;
    }

    /**
     * Access the Control and Status values window.
     */
    public ControlAndStatusWindow getControlAndStatusWindow() {
        return csrValues;
    }

    /**
     * Access the label values window.
     */
    public LabelsWindow getLabelsWindow() {
        return labelValues;
    }

    /**
     * Retrieve the number system base for displaying values (mem/register contents)
     */
    public int getValueDisplayBase() {
        return valueDisplayBase.getBase();
    }

    /**
     * Retrieve the number system base for displaying memory addresses
     */
    public int getAddressDisplayBase() {
        return addressDisplayBase.getBase();
    }

    /**
     * Retrieve component used to set numerical base (10 or 16) of data value display.
     *
     * @return the chooser
     */
    public NumberDisplayBaseChooser getValueDisplayBaseChooser() {
        return valueDisplayBase;
    }

    /**
     * Retrieve component used to set numerical base (10 or 16) of address display.
     *
     * @return the chooser
     */
    public NumberDisplayBaseChooser getAddressDisplayBaseChooser() {
        return addressDisplayBase;
    }

    /**
     * Update display of columns based on state of given chooser.  Normally
     * called only by the chooser's ItemListener.
     *
     * @param chooser the GUI object manipulated by the user to change number base
     */
    public void numberDisplayBaseChanged(NumberDisplayBaseChooser chooser) {
        if (chooser == valueDisplayBase) {
            // Have all internal windows update their value columns
            registerValues.updateRegisters(hart);
            fpRegValues.updateRegisters();
            csrValues.updateRegisters();
            textSegment.updateBasicStatements();
        } else { // addressDisplayBase
            // Have all internal windows update their address columns
            labelValues.updateLabelAddresses();
            textSegment.updateCodeAddresses();
            textSegment.updateBasicStatements();
        }
    }

}