package rars.venus;

import rars.Globals;
import rars.venus.registers.ControlAndStatusWindow;
import rars.venus.registers.FloatingPointWindow;
import rars.venus.registers.RegistersWindow;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
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
 * Creates the tabbed areas in the UI and also created the internal windows that
 * exist in them.
 *
 * @author Sanderson and Bumgarner
 **/

public class GeneralMainPane extends JTabbedPane {
    GeneralExecutePane executeTab;
    private int hart;
    private GeneralVenusUI mainUI;

    /**
     * Constructor for the MainPane class.
     **/

    public GeneralMainPane(GeneralVenusUI appFrame, RegistersWindow regs,
                    FloatingPointWindow cop1Regs, ControlAndStatusWindow cop0Regs, int hart) {
        super();
        this.hart = hart;
        this.mainUI = appFrame;
        this.setTabPlacement(JTabbedPane.TOP); //LEFT);
        if (this.getUI() instanceof BasicTabbedPaneUI) {
            BasicTabbedPaneUI ui = (BasicTabbedPaneUI) this.getUI();
        }

        executeTab = new GeneralExecutePane(appFrame, regs, cop1Regs, cop0Regs, hart);
        String executeTabTitle = "Execute"; //"<html><center>&nbsp;<br>E<br>x<br>e<br>c<br>u<br>t<br>e<br>&nbsp;</center></html>";
        Icon executeTabIcon = null;//new ImageIcon(Toolkit.getDefaultToolkit().getImage(this.getClass().getResource(Globals.imagesPath+"Execute_tab.jpg")));

        // this.addTab("<html><center>&nbsp;<br>P<br>r<br>o<br>j<br>&nbsp;<br>1<br&nbsp;</center></html>", null, new JTabbedPane());
        // this.addTab("<html><center>&nbsp;<br>P<br>r<br>o<br>j<br>&nbsp;<br>2<br&nbsp;</center></html>", null, new JTabbedPane());
        // this.addTab("<html><center>&nbsp;<br>P<br>r<br>o<br>j<br>&nbsp;<br>3<br&nbsp;</center></html>", null, new JTabbedPane());
        // this.addTab("<html><center>&nbsp;<br>P<br>r<br>o<br>j<br>&nbsp;<br>4<br&nbsp;</center></html>", null, new JTabbedPane());

        this.addTab(executeTabTitle, executeTabIcon, executeTab);

        this.setToolTipTextAt(0, "View and control assembly language program execution.  Enabled upon successful assemble.");
      
    }

    /**
     * returns component containing execution-time display
     *
     * @return the execute pane
     */
    public GeneralExecutePane getExecutePane() {
        return executeTab;
    }

    /**
     * returns component containing execution-time display.
     * Same as getExecutePane().
     *
     * @return the execute pane
     */
    public GeneralExecutePane getExecuteTab() {
        return executeTab;
    }

}