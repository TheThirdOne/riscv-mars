package rars.tools;

import rars.AssemblyException;
import rars.Globals;
import rars.RISCVprogram;
import rars.Settings;
import rars.riscv.hardware.*;
import rars.simulator.Simulator;
import rars.simulator.SimulatorNotice;
import rars.util.FilenameFinder;
import rars.venus.run.RunSpeedPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

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
 * An abstract class that provides generic components to facilitate implementation of
 * a Tool and/or stand-alone Rars-based application.  Provides default definitions
 * of both the action() method required to implement Tool and the go() method
 * conventionally used to launch a Rars-based stand-alone application. It also provides
 * generic definitions for interactively controlling the application.  The generic controls
 * for RarsTools are 3 buttons:  connect/disconnect to a resource (memory and/or
 * registers), reset, and close (exit).  The generic controls for stand-alone Rars apps
 * include: button that triggers a file open dialog, a text field to display status
 * messages, the run-speed slider to control execution rate when running a program,
 * a button that assembles and runs the current program, a button to interrupt
 * the running program, a reset button, and an exit button.
 * Pete Sanderson, 14 November 2006.
 */
public abstract class AbstractToolAndApplication extends JFrame implements Tool, Observer {
    protected boolean isBeingUsedAsATool = false;  // can use to determine whether invoked as Tool or stand-alone.
    protected JDialog dialog;  // used only for Tool use.  This is the pop-up dialog that appears when menu item selected.
    protected Window theWindow;  // highest level GUI component (a JFrame for app, a JDialog for Tool)
    // Major GUI components
    private JLabel headingLabel;
    private String title;  // descriptive title for title bar provided to constructor.
    private String heading; // Text to be displayed in the top portion of the main window.

    // Some GUI settings
    private EmptyBorder emptyBorder = new EmptyBorder(4, 4, 4, 4);
    private Color backgroundColor = Color.WHITE;


    private int lowMemoryAddress = Memory.dataSegmentBaseAddress;
    private int highMemoryAddress = Memory.stackBaseAddress;
    // For Tool, is set true when "Connect" clicked, false when "Disconnect" clicked.
    // For app, is set true when "Assemble and Run" clicked, false when program terminates.
    private volatile boolean observing = false;

    // Several structures required for stand-alone use only (not Tool use)
    private File mostRecentlyOpenedFile = null;
    private Runnable interactiveGUIUpdater = new GUIUpdater();
    private MessageField operationStatusMessages;
    private JButton openFileButton, assembleRunButton, stopButton;
    private boolean multiFileAssemble = false;

    // Structure required for Tool use only (not stand-alone use). Want subclasses to have access.
    protected ConnectButton connectButton;


    /**
     * Simple constructor
     *
     * @param title String containing title bar text
     */
    protected AbstractToolAndApplication(String title, String heading) {
        this.title = title;
        this.heading = heading;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////      ABSTRACT METHODS       ///////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Required Tool method to return Tool name.  Must be defined by subclass.
     *
     * @return Tool name. RARS will display this in menu item.
     */
    public abstract String getName();

    /**
     * Abstract method that must be instantiated by subclass to build the main display area
     * of the GUI.  It will be placed in the CENTER area of a BorderLayout.  The title
     * is in the NORTH area, and the controls are in the SOUTH area.
     */
    protected abstract JComponent buildMainDisplayArea();

    //////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////  METHODS WITH DEFAULT IMPLEMENTATIONS //////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Run the simulator as stand-alone application.  For this default implementation,
     * the user-defined main display of the user interface is identical for both stand-alone
     * and RARS Tools menu use, but the control buttons are different because the stand-alone
     * must include a mechansim for controlling the opening, assembling, and executing of
     * an underlying source program.  The generic controls include: a button that triggers a
     * file open dialog, a text field to display status messages, the run-speed slider
     * to control execution rate when running a program, a button that assembles and
     * runs the current source program, a reset button, and an exit button.
     * This method calls 3 methods that can be defined/overriden in the subclass: initializePreGUI()
     * for any special initialization that must be completed before building the user
     * interface (e.g. data structures whose properties determine default GUI settings),
     * initializePostGUI() for any special initialization that cannot be
     * completed until after the building the user interface (e.g. data structure whose
     * properties are determined by default GUI settings), and buildMainDisplayArea()
     * to contain application-specific displays of parameters and results.
     */
    public void go() {
        theWindow = this;
        this.isBeingUsedAsATool = false;
        this.setTitle(this.title);
        rars.Globals.initialize(true);
        // assure the dialog goes away if user clicks the X
        this.addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        performAppClosingDuties();
                    }
                });
        initializePreGUI();

        JPanel contentPane = new JPanel(new BorderLayout(5, 5));
        contentPane.setBorder(emptyBorder);
        contentPane.setOpaque(true);
        contentPane.add(buildHeadingArea(), BorderLayout.NORTH);
        contentPane.add(buildMainDisplayArea(), BorderLayout.CENTER);
        contentPane.add(buildButtonAreaStandAlone(), BorderLayout.SOUTH);

        this.setContentPane(contentPane);
        this.pack();
        this.setLocationRelativeTo(null); // center on screen
        this.setVisible(true);
        initializePostGUI();
    }


    /**
     * Required Tool method to carry out Tool functions.  It is invoked when the
     * user selects this tool from the Tools menu.  This default implementation provides
     * generic definitions for interactively controlling the tool.  The generic controls
     * for RarsTools are 3 buttons:  connect/disconnect to a resource (memory and/or
     * registers), reset, and close (exit).  Like "go()" above, this default version
     * calls 3 methods that can be defined/overriden in the subclass: initializePreGUI()
     * for any special initialization that must be completed before building the user
     * interface (e.g. data structures whose properties determine default GUI settings),
     * initializePostGUI() for any special initialization that cannot be
     * completed until after the building the user interface (e.g. data structure whose
     * properties are determined by default GUI settings), and buildMainDisplayArea()
     * to contain application-specific displays of parameters and results.
     */

    public void action() {
        this.isBeingUsedAsATool = true;
        dialog = new JDialog(Globals.getGui(), this.title);
        // assure the dialog goes away if user clicks the X
        dialog.addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        performToolClosingDuties();
                    }
                });
        theWindow = dialog;
        initializePreGUI();
        JPanel contentPane = new JPanel(new BorderLayout(5, 5));
        contentPane.setBorder(emptyBorder);
        contentPane.setOpaque(true);
        contentPane.add(buildHeadingArea(), BorderLayout.NORTH);
        contentPane.add(buildMainDisplayArea(), BorderLayout.CENTER);
        contentPane.add(buildButtonAreaForTool(), BorderLayout.SOUTH);
        initializePostGUI();
        dialog.setContentPane(contentPane);
        dialog.pack();
        dialog.setLocationRelativeTo(Globals.getGui());
        dialog.setVisible(true);
    }


    /**
     * Method that will be called once just before the GUI is constructed in the go() and action()
     * methods.  Use it to initialize any data structures needed for the application whose values
     * will be needed to determine the initial state of GUI components.  By default it does nothing.
     */
    protected void initializePreGUI() {
    }

    /**
     * Method that will be called once just after the GUI is constructed in the go() and action()
     * methods.  Use it to initialize data structures needed for the application whose values
     * may depend on the initial state of GUI components.  By default it does nothing.
     */
    protected void initializePostGUI() {
    }

    /**
     * Method that will be called each time the default Reset button is clicked.
     * Use it to reset any data structures and/or GUI components.  By default it does nothing.
     */
    protected void reset() {
    }


    /**
     * Constructs GUI header as label with default positioning and font.  May be overridden.
     */
    protected JComponent buildHeadingArea() {
        // OVERALL STRUCTURE OF MESSAGE (TOP)
        headingLabel = new JLabel();
        Box headingPanel = Box.createHorizontalBox();//new JPanel(new BorderLayout());
        headingPanel.add(Box.createHorizontalGlue());
        headingPanel.add(headingLabel);
        headingPanel.add(Box.createHorizontalGlue());
        // Details for heading area (top)
        headingLabel.setText(heading);
        headingLabel.setHorizontalTextPosition(JLabel.CENTER);
        headingLabel.setFont(new Font(headingLabel.getFont().getFontName(), Font.PLAIN, 18));
        return headingPanel;
    }


    /**
     * The Tool default set of controls has one row of 3 buttons.  It includes a dual-purpose button to
     * attach or detach simulator to memory, a button to reset the cache, and one to close the tool.
     */
    protected JComponent buildButtonAreaForTool() {
        Box buttonArea = Box.createHorizontalBox();
        TitledBorder tc = new TitledBorder("Tool Control");
        tc.setTitleJustification(TitledBorder.CENTER);
        buttonArea.setBorder(tc);
        connectButton = new ConnectButton();
        connectButton.setToolTipText("Control whether tool will respond to running program");
        connectButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (connectButton.isConnected()) {
                            connectButton.disconnect();
                        } else {
                            connectButton.connect();
                        }
                    }
                });
        connectButton.addKeyListener(new EnterKeyListener(connectButton));

        JButton resetButton = new JButton("Reset");
        resetButton.setToolTipText("Reset all counters and other structures");
        resetButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        reset();
                    }
                });
        resetButton.addKeyListener(new EnterKeyListener(resetButton));

        JButton closeButton = new JButton("Close");
        closeButton.setToolTipText("Close (exit) this tool");
        closeButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        performToolClosingDuties();
                    }
                });
        closeButton.addKeyListener(new EnterKeyListener(closeButton));

        // Add all the buttons...
        buttonArea.add(connectButton);
        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(resetButton);
        buttonArea.add(Box.createHorizontalGlue());
        JComponent helpComponent = getHelpComponent();
        if (helpComponent != null) {
            buttonArea.add(helpComponent);
            buttonArea.add(Box.createHorizontalGlue());
        }
        buttonArea.add(closeButton);
        return buttonArea;
    }

    /**
     * The Rars stand-alone app default set of controls has two rows of controls.  It includes a text field for
     * displaying status messages, a button to trigger an open file dialog, the RARS run speed slider
     * to control timed execution, a button to assemble and run the program, a reset button
     * whose action is determined by the subclass reset() method, and an exit button.
     */

    protected JComponent buildButtonAreaStandAlone() {
        // Overall structure of control area (two rows).
        Box operationArea = Box.createVerticalBox();
        Box fileControlArea = Box.createHorizontalBox();
        Box buttonArea = Box.createHorizontalBox();
        operationArea.add(fileControlArea);
        operationArea.add(Box.createVerticalStrut(5));
        operationArea.add(buttonArea);
        TitledBorder ac = new TitledBorder("Application Control");
        ac.setTitleJustification(TitledBorder.CENTER);
        operationArea.setBorder(ac);

        AbstractToolAndApplication that = this;
        // Top row of controls consists of button to launch file open operation,
        // text field to show filename, and run speed slider.
        openFileButton = new JButton("Open program...");
        openFileButton.setToolTipText("Select program file to assemble and run");
        openFileButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        JFileChooser fileChooser = new JFileChooser();
                        JCheckBox multiFileAssembleChoose = new JCheckBox("Assemble all in selected file's directory", multiFileAssemble);
                        multiFileAssembleChoose.setToolTipText("If checked, selected file will be assembled first and all other assembly files in directory will be assembled also.");
                        fileChooser.setAccessory(multiFileAssembleChoose);
                        if (mostRecentlyOpenedFile != null) {
                            fileChooser.setSelectedFile(mostRecentlyOpenedFile);
                        }
                        // DPS 13 June 2007.  The next 4 lines add file filter to file chooser.
                        FileFilter defaultFileFilter = FilenameFinder.getFileFilter(Globals.fileExtensions, "Assembler Files", true);
                        fileChooser.addChoosableFileFilter(defaultFileFilter);
                        fileChooser.addChoosableFileFilter(fileChooser.getAcceptAllFileFilter());
                        fileChooser.setFileFilter(defaultFileFilter);

                        if (fileChooser.showOpenDialog(that) == JFileChooser.APPROVE_OPTION) {
                            multiFileAssemble = multiFileAssembleChoose.isSelected();
                            File theFile = fileChooser.getSelectedFile();
                            try {
                                theFile = theFile.getCanonicalFile();
                            } catch (IOException ioe) {
                                // nothing to do, theFile will keep current value
                            }
                            String currentFilePath = theFile.getPath();
                            mostRecentlyOpenedFile = theFile;
                            operationStatusMessages.setText("File: " + currentFilePath);
                            operationStatusMessages.setCaretPosition(0);
                            assembleRunButton.setEnabled(true);
                        }
                    }
                });
        openFileButton.addKeyListener(new EnterKeyListener(openFileButton));

        operationStatusMessages = new MessageField("No file open.");
        operationStatusMessages.setColumns(40);
        operationStatusMessages.setMargin(new Insets(0, 3, 0, 3)); //(top, left, bottom, right)
        operationStatusMessages.setBackground(backgroundColor);
        operationStatusMessages.setFocusable(false);
        operationStatusMessages.setToolTipText("Display operation status messages");

        RunSpeedPanel speed = RunSpeedPanel.getInstance();

        // Bottom row of controls consists of the three buttons defined here.
        assembleRunButton = new JButton("Assemble and Run");
        assembleRunButton.setToolTipText("Assemble and run the currently selected program");
        assembleRunButton.setEnabled(false);
        assembleRunButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        assembleRunButton.setEnabled(false);
                        openFileButton.setEnabled(false);
                        stopButton.setEnabled(true);
                        new Thread(new CreateAssembleRunProgram()).start();
                    }
                });
        assembleRunButton.addKeyListener(new EnterKeyListener(assembleRunButton));

        stopButton = new JButton("Stop");
        stopButton.setToolTipText("Terminate program execution");
        stopButton.setEnabled(false);
        stopButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        rars.simulator.Simulator.getInstance().stopExecution();
                    }
                });
        stopButton.addKeyListener(new EnterKeyListener(stopButton));

        JButton resetButton = new JButton("Reset");
        resetButton.setToolTipText("Reset all counters and other structures");
        resetButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        reset();
                    }
                });
        resetButton.addKeyListener(new EnterKeyListener(resetButton));

        JButton closeButton = new JButton("Exit");
        closeButton.setToolTipText("Exit this application");
        closeButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        performAppClosingDuties();
                    }
                });
        closeButton.addKeyListener(new EnterKeyListener(closeButton));


        // Add top row of controls...
        //fileControlArea.add(Box.createHorizontalStrut(5));

        Box fileDisplayBox = Box.createVerticalBox();
        fileDisplayBox.add(Box.createVerticalStrut(8));
        fileDisplayBox.add(operationStatusMessages);
        fileDisplayBox.add(Box.createVerticalStrut(8));
        fileControlArea.add(fileDisplayBox);

        fileControlArea.add(Box.createHorizontalGlue());
        fileControlArea.add(speed);

        // Add bottom row of buttons...

        buttonArea.add(openFileButton);
        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(assembleRunButton);
        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(stopButton);
        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(resetButton);
        buttonArea.add(Box.createHorizontalGlue());
        JComponent helpComponent = getHelpComponent();
        if (helpComponent != null) {
            buttonArea.add(helpComponent);
            buttonArea.add(Box.createHorizontalGlue());
        }
        buttonArea.add(closeButton);
        return operationArea;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Rest of the methods.  Some are used by stand-alone (JFrame-based) only, some are
    //  used by Tool (JDialog-based) only, others are used by both.
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Called when receiving notice of access to memory or registers.  Default
     * implementation of method required by Observer interface.  This method will filter out
     * notices originating from the RARS GUI or from direct user editing of memory or register
     * displays. Only notices arising from program access are allowed in.
     * It then calls two methods to be overridden by the subclass (since they do
     * nothing by default): processRISCVUpdate() then updateDisplay().
     *
     * @param resource     the attached resource
     * @param accessNotice AccessNotice information provided by the resource
     */
    public void update(Observable resource, Object accessNotice) {
        if (((AccessNotice) accessNotice).accessIsFromRISCV()) {
            processRISCVUpdate(resource, (AccessNotice) accessNotice);
            updateDisplay();
        }
    }

    /**
     * Override this method to process a received notice from an Observable (memory or register)
     * It will only be called if the notice was generated as the result of RISCV instruction execution.
     * By default it does nothing. After this method is complete, the updateDisplay() method will be
     * invoked automatically.
     */
    protected void processRISCVUpdate(Observable resource, AccessNotice notice) {
    }

    /**
     * This method is called when tool/app is exited either through the close/exit button or the window's X box.
     * Override it to perform any special housecleaning needed.  By default it does nothing.
     */
    protected void performSpecialClosingDuties() {
    }


    /**
     * Add this app/tool as an Observer of desired Observables (memory and registers).
     * By default, will add as an Observer of the entire Data Segment in memory.
     * Override if you want something different.  Note that the Memory methods to add an
     * Observer to memory are flexible (you can register for a range of addresses) but
     * may throw an AddressErrorException that you need to catch.
     * This method is called whenever the default "Connect" button on a Tool or the
     * default "Assemble and run" on a stand-alone Rars app is selected.  The corresponding
     * NOTE: if you do not want to register as an Observer of the entire data segment
     * (starts at address 0x10000000) then override this to either do some alternative
     * or nothing at all.  This method is also overloaded to allow arbitrary memory
     * subrange.
     */

    protected void addAsObserver() {
        addAsObserver(lowMemoryAddress, highMemoryAddress);
    }

    /**
     * Add this app/tool as an Observer of the specified subrange of memory.  Note
     * that this method is not invoked automatically like the no-argument version, but
     * if you use this method, you can still take advantage of provided default deleteAsObserver()
     * since it will remove the app as a memory observer regardless of the subrange
     * or number of subranges it is registered for.
     *
     * @param lowEnd  low end of memory address range.
     * @param highEnd high end of memory address range; must be >= lowEnd
     */

    protected void addAsObserver(int lowEnd, int highEnd) {
        String errorMessage = "Error connecting to memory";
        try {
            Globals.memory.addObserver(this, lowEnd, highEnd);
        } catch (AddressErrorException aee) {
            if (this.isBeingUsedAsATool) {
                headingLabel.setText(errorMessage);
            } else {
                operationStatusMessages.displayTerminatingMessage(errorMessage);
            }
        }
    }

    /**
     * Add this app/tool as an Observer of the specified register.
     */
    protected void addAsObserver(Register reg) {
        if (reg != null) {
            reg.addObserver(this);
        }
    }


    /**
     * Delete this app/tool as an Observer of Observables (memory and registers).
     * By default, will delete as an Observer of memory.
     * Override if you want something different.
     * This method is called when the default "Disconnect" button on a Tool is selected or
     * when the RISCV program execution triggered by the default "Assemble and run" on a stand-alone
     * app terminates (e.g. when the button is re-enabled).
     */

    protected void deleteAsObserver() {
        Globals.memory.deleteObserver(this);
    }

    /**
     * Delete this app/tool as an Observer of the specified register
     */

    protected void deleteAsObserver(Register reg) {
        if (reg != null) {
            reg.deleteObserver(this);
        }
    }

    /**
     * Query method to let you know if the tool/app is (or could be) currently
     * "observing" any resources.  When running as a Tool, this
     * will be true by default after clicking the "Connect to Program" button until "Disconnect
     * from Program" is clicked.  When running as a stand-alone app, this will be
     * true by default after clicking the "Assemble and Run" button until until
     * program execution has terminated either normally or by clicking the "Stop"
     * button.  The phrase "or could be" was added above because depending on how
     * the tool/app operates, it may be possible to run the program without
     * first registering as an Observer -- i.e. addAsObserver() is overridden and
     * takes no action.
     *
     * @return true if tool/app is (or could be) currently active as an Observer.
     */

    protected boolean isObserving() {
        return observing;
    }

    /**
     * Override this method to implement updating of GUI after each instruction is executed,
     * while running in "timed" mode (user specifies execution speed on the slider control).
     * Does nothing by default.
     */
    protected void updateDisplay() {
    }

    /**
     * Override this method to provide a JComponent (probably a JButton) of your choice
     * to be placed just left of the Close/Exit button.  Its anticipated use is for a
     * "help" button that launches a help message or dialog.  But it can be any valid
     * JComponent that doesn't mind co-existing among a bunch of JButtons.
     */
    protected JComponent getHelpComponent() {
        return null;
    }

    //////////////////////////////////////////////////////////////////////////////////
    ////////////////////  PRIVATE HELPER METHODS    //////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////

    // Closing duties for Tool only.
    private void performToolClosingDuties() {
        performSpecialClosingDuties();
        if (connectButton.isConnected()) {
            connectButton.disconnect();
        }
        dialog.setVisible(false);
        dialog.dispose();
    }

    // Closing duties for stand-alone application only.
    private void performAppClosingDuties() {
        performSpecialClosingDuties();
        this.setVisible(false);
        System.exit(0);
    }


    //////////////////////////////////////////////////////////////////////////////////
    ////////////////////  PRIVATE HELPER CLASSES    //////////////////////////////////
    //  Specialized inner classes.  Either used by stand-alone (JFrame-based) only  //
    //  or used by Tool (JDialog-based) only.                                   //
    //////////////////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////
    // Little class for this dual-purpose button.  It is used only by the Tool
    // (not by the stand-alone app).
    protected class ConnectButton extends JButton {
        private static final String connectText = "Connect to Program";
        private static final String disconnectText = "Disconnect from Program";

        public ConnectButton() {
            super();
            disconnect();
        }

        public void connect() {
            observing = true;
            Globals.memoryAndRegistersLock.lock();
            try {
                addAsObserver();
            } finally {
                Globals.memoryAndRegistersLock.unlock();
            }
            setText(disconnectText);
        }

        public void disconnect() {
            Globals.memoryAndRegistersLock.lock();
            try {
                deleteAsObserver();
            } finally {
                Globals.memoryAndRegistersLock.unlock();
            }
            observing = false;
            setText(connectText);
        }

        public boolean isConnected() {
            return observing;
        }
    }


    ///////////////////////////////////////////////////////////////////////
    //  Every control button will get one of these so when it has focus
    //  the Enter key can be used instead of a mouse click to perform
    //  its associated action.  It will do nothing if no action listeners
    //  are attached to the button at the time of the call.  Otherwise,
    //  it will call actionPerformed for the first action listener in the
    //  button's list.
    protected class EnterKeyListener extends KeyAdapter {
        AbstractButton myButton;

        public EnterKeyListener(AbstractButton who) {
            myButton = who;
        }

        public void keyPressed(KeyEvent e) {
            if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                e.consume();
                try {
                    myButton.getActionListeners()[0].actionPerformed(new ActionEvent(myButton, 0, myButton.getText()));
                } catch (ArrayIndexOutOfBoundsException oob) {
                    // do nothing, since there is no action listener.
                }
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////////////
    // called when the Assemble and Run button is pressed.  Used only by stand-alone app.
    private class CreateAssembleRunProgram implements Runnable {
        public void run() {
            //String noSupportForExceptionHandler = null;  // no auto-loaded exception handlers.
            // boolean extendedAssemblerEnabled = true;     // In this context, no reason to constrain.
            // boolean warningsAreErrors = false;           // Ditto.

            String exceptionHandler = null;
            if (Globals.getSettings().getBooleanSetting(Settings.Bool.EXCEPTION_HANDLER_ENABLED) &&
                    Globals.getSettings().getExceptionHandler() != null &&
                    Globals.getSettings().getExceptionHandler().length() > 0) {
                exceptionHandler = Globals.getSettings().getExceptionHandler();
            }

            Thread.currentThread().setPriority(Thread.NORM_PRIORITY - 1);
            Thread.yield();
            RISCVprogram program = new RISCVprogram();
            rars.Globals.program = program; // Shouldn't have to do this...
            String fileToAssemble = mostRecentlyOpenedFile.getPath();
            ArrayList<String> filesToAssemble;
            if (multiFileAssemble) {// setting (check box in file open dialog) calls for multiple file assembly 
                filesToAssemble = FilenameFinder.getFilenameList(
                        new File(fileToAssemble).getParent(), Globals.fileExtensions);
            } else {
                filesToAssemble = new ArrayList<>();
                filesToAssemble.add(fileToAssemble);
            }
            ArrayList<RISCVprogram> programsToAssemble;
            try {
                operationStatusMessages.displayNonTerminatingMessage("Assembling " + fileToAssemble);
                programsToAssemble = program.prepareFilesForAssembly(filesToAssemble, fileToAssemble, exceptionHandler);
            } catch (AssemblyException pe) {
                operationStatusMessages.displayTerminatingMessage("Error reading file(s): " + fileToAssemble);
                return;
            }

            try {
                program.assemble(programsToAssemble, Globals.getSettings().getBooleanSetting(Settings.Bool.EXTENDED_ASSEMBLER_ENABLED),
                        Globals.getSettings().getBooleanSetting(Settings.Bool.WARNINGS_ARE_ERRORS));
            } catch (AssemblyException pe) {
                operationStatusMessages.displayTerminatingMessage("Assembly Error: " + fileToAssemble);
                return;
            }
            // Moved these three register resets from before the try block to after it.  17-Dec-09 DPS.
            RegisterFile.resetRegisters();
            FloatingPointRegisterFile.resetRegisters();
            ControlAndStatusRegisterFile.resetRegisters();
            InterruptController.reset();

            addAsObserver();
            observing = true;
            operationStatusMessages.displayNonTerminatingMessage("Running " + fileToAssemble);
            final Observer stopListener =
                    new Observer() {
                        public void update(Observable o, Object simulator) {
                            SimulatorNotice notice = ((SimulatorNotice) simulator);
                            if (notice.getAction() != SimulatorNotice.SIMULATOR_STOP) return;
                            deleteAsObserver();
                            observing = false;
                            String terminatingMessage = "Normal termination: ";
                            if (notice.getReason() == Simulator.Reason.EXCEPTION)
                                terminatingMessage = "Runtime error: ";
                            if (notice.getReason() == Simulator.Reason.STOP || notice.getReason() == Simulator.Reason.PAUSE) {
                                terminatingMessage = "User interrupt: ";
                            }
                            operationStatusMessages.displayTerminatingMessage(terminatingMessage + fileToAssemble);
                            o.deleteObserver(this);
                        }
                    };
            Simulator.getInstance().addObserver(stopListener);
            program.startSimulation(-1, null); // unlimited steps
        }
    }


    //////////////////////////////////////////////////////////////////////////
    //  Class for text message field used to update operation status when
    //  assembling and running programs.
    private class MessageField extends JTextField {

        public MessageField(String text) {
            super(text);
        }

        private void displayTerminatingMessage(String text) {
            displayMessage(text, true);
        }

        private void displayNonTerminatingMessage(String text) {
            displayMessage(text, false);
        }

        private void displayMessage(String text, boolean terminating) {
            SwingUtilities.invokeLater(new MessageWriter(text, terminating));
        }

        /////////////////////////////////////////////////////////////////////////////////
        // Little inner-inner class to display processing error message on AWT thread.
        // Used only by stand-alone app.
        private class MessageWriter implements Runnable {
            private String text;
            private boolean terminatingMessage;

            public MessageWriter(String text, boolean terminating) {
                this.text = text;
                this.terminatingMessage = terminating;
            }

            public void run() {
                if (text != null) {
                    operationStatusMessages.setText(text);
                    operationStatusMessages.setCaretPosition(0);
                }
                if (terminatingMessage) {
                    assembleRunButton.setEnabled(true);
                    openFileButton.setEnabled(true);
                    stopButton.setEnabled(false);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //  For scheduling GUI update on timed runs...used only by stand-alone app.
    private class GUIUpdater implements Runnable {
        public void run() {
            updateDisplay();
        }
    }

}