package rars.tools;

import rars.Globals;
import rars.riscv.hardware.*;
import rars.util.Binary;
import rars.venus.util.AbstractFontSettingDialog;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.Observable;
import java.util.Random;


/*
Copyright (c) 2003-2014,  Pete Sanderson and Kenneth Vollmar

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

// TODO: make an example that uses this. I'm not sure it wasn't broken in the porting process.

/**
 * Keyboard and Display Simulator.  It can be run either as a stand-alone Java application having
 * access to the rars package, or through RARS as an item in its Tools menu.  It makes
 * maximum use of methods inherited from its abstract superclass AbstractToolAndApplication.
 * Pete Sanderson<br>
 * Version 1.0, 24 July 2008.<br>
 * Version 1.1, 24 November 2008 corrects two omissions: (1) the tool failed to register as an observer
 * of kernel text memory when counting instruction executions for transmitter ready bit
 * reset delay, and (2) the tool failed to test the Status register's Exception Level bit before
 * raising the exception that results in the interrupt (if the Exception Level bit is 1, that
 * means an interrupt is being processed, so disable further interrupts).
 * <p>
 * Version 1.2, August 2009, soft-codes the MMIO register locations for new memory configuration
 * feature of MARS 3.7.  Previously memory segment addresses were fixed and final.  Now they
 * can be modified dynamically so the tool has to get its values dynamically as well.
 * <p>
 * Version 1.3, August 2011, corrects bug to enable Display window to scroll when needed.
 * <p>
 * Version 1.4, August 2014, adds two features: (1) ASCII control character 12 (form feed) when
 * transmitted will clear the Display window.  (2) ASCII control character 7 (bell) when
 * transmitted with properly coded (X,Y) values will reposition the cursor to the specified
 * position of a virtual text-based terminal.  X represents column, Y represents row.
 */

public class KeyboardAndDisplaySimulator extends AbstractToolAndApplication {

    private static String version = "Version 1.4";
    private static String heading = "Keyboard and Display MMIO Simulator";
    private static String displayPanelTitle, keyboardPanelTitle;
    private static char VT_FILL = ' ';  // fill character for virtual terminal (random access mode)

    public static Dimension preferredTextAreaDimension = new Dimension(400, 200);
    private static Insets textAreaInsets = new Insets(4, 4, 4, 4);

    // Time delay to process Transmitter Data is simulated by counting instruction executions.
    // After this many executions, the Transmitter Controller Ready bit set to 1.
    private final TransmitterDelayTechnique[] delayTechniques = {
            new FixedLengthDelay(),
            new UniformlyDistributedDelay(),
            new NormallyDistributedDelay()
    };
    public static int RECEIVER_CONTROL;    // keyboard Ready in low-order bit
    public static int RECEIVER_DATA;       // keyboard character in low-order byte
    public static int TRANSMITTER_CONTROL; // display Ready in low-order bit
    public static int TRANSMITTER_DATA;    // display character in low-order byte
    // These are used to track instruction counts to simulate driver delay of Transmitter Data
    private boolean countingInstructions;
    private int instructionCount;
    private int transmitDelayInstructionCountLimit;
    private int currentDelayInstructionLimit;

    // Should the transmitted character be displayed before the transmitter delay period?
    // If not, hold onto it and print at the end of delay period.
    private int intWithCharacterToDisplay;
    private boolean displayAfterDelay = true;

    // Whether or not display position is sequential (JTextArea append)
    // or random access (row, column).  Supports new random access feature. DPS 17-July-2014
    private boolean displayRandomAccessMode = false;
    private int rows, columns;
    private DisplayResizeAdapter updateDisplayBorder;
    private KeyboardAndDisplaySimulator simulator;

    // Major GUI components
    private JPanel keyboardAndDisplay;
    private JScrollPane displayScrollPane;
    private JTextArea display;
    private JPanel displayPanel, displayOptions;
    private JComboBox<TransmitterDelayTechnique> delayTechniqueChooser;
    private DelayLengthPanel delayLengthPanel;
    private JSlider delayLengthSlider;
    private JCheckBox displayAfterDelayCheckBox;
    private JPanel keyboardPanel;
    private JScrollPane keyAccepterScrollPane;
    private JTextArea keyEventAccepter;
    private JButton fontButton;
    private Font defaultFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);


    public static final int EXTERNAL_INTERRUPT_KEYBOARD = 0x00000040;
    public static final int EXTERNAL_INTERRUPT_DISPLAY = 0x00000080;

    /**
     * Simple constructor, likely used to run a stand-alone keyboard/display simulator.
     *
     * @param title   String containing title for title bar
     * @param heading String containing text for heading shown in upper part of window.
     */
    public KeyboardAndDisplaySimulator(String title, String heading) {
        super(title, heading);
        simulator = this;
    }

    /**
     * Simple constructor, likely used by the RARS Tools menu mechanism
     */
    public KeyboardAndDisplaySimulator() {
        this(heading + ", " + version, heading);
    }

    /**
     * Main provided for pure stand-alone use.  Recommended stand-alone use is to write a
     * driver program that instantiates a KeyboardAndDisplaySimulator object then invokes its go() method.
     * "stand-alone" means it is not invoked from the RARS Tools menu.  "Pure" means there
     * is no driver program to invoke the application.
     */
    public static void main(String[] args) {
        new KeyboardAndDisplaySimulator(heading + " stand-alone, " + version, heading).go();
    }

    @Override
    public String getName() {
        return heading;
    }

    // Set the MMIO addresses.  Prior to MARS 3.7 these were final because
    // address space was final as well.  Now we will get MMIO base address
    // each time to reflect possible change in memory configuration. DPS 6-Aug-09
    protected void initializePreGUI() {
        RECEIVER_CONTROL = Memory.configuration.getMemoryMapBaseAddress(); //0xffff0000; // keyboard Ready in low-order bit
        RECEIVER_DATA = Memory.configuration.getMemoryMapBaseAddress() + 4; //0xffff0004; // keyboard character in low-order byte
        TRANSMITTER_CONTROL = Memory.configuration.getMemoryMapBaseAddress() + 8; //0xffff0008; // display Ready in low-order bit
        TRANSMITTER_DATA = Memory.configuration.getMemoryMapBaseAddress() + 12; //0xffff000c; // display character in low-order byte
        displayPanelTitle = "DISPLAY: Store to Transmitter Data " + Binary.intToHexString(TRANSMITTER_DATA);
        keyboardPanelTitle = "KEYBOARD: Characters typed here are stored to Receiver Data " + Binary.intToHexString(RECEIVER_DATA);
    }


    /**
     * Override the inherited method, which registers us as an Observer over the static data segment
     * (starting address 0x10010000) only.
     * <p>
     * When user enters keystroke, set RECEIVER_CONTROL and RECEIVER_DATA using the action listener.
     * When user loads word (lw) from RECEIVER_DATA (we are notified of the read), then clear RECEIVER_CONTROL.
     * When user stores word (sw) to TRANSMITTER_DATA (we are notified of the write), then clear TRANSMITTER_CONTROL, read TRANSMITTER_DATA,
     * echo the character to display, wait for delay period, then set TRANSMITTER_CONTROL.
     * <p>
     * If you use the inherited GUI buttons, this method is invoked when you click "Connect" button on Tool or the
     * "Assemble and Run" button on a Rars-based app.
     */
    protected void addAsObserver() {
        // Set transmitter Control ready bit to 1, means we're ready to accept display character.
        updateMMIOControl(TRANSMITTER_CONTROL, readyBitSet(TRANSMITTER_CONTROL));
        // We want to be an observer only of reads from RECEIVER_DATA and writes to TRANSMITTER_DATA.
        // Use the Globals.memory.addObserver() methods instead of inherited method to achieve this.
        addAsObserver(RECEIVER_DATA, RECEIVER_DATA);
        addAsObserver(TRANSMITTER_DATA, TRANSMITTER_DATA);
        // We want to be notified of each instruction execution, because instruction count is the
        // basis for delay in re-setting (literally) the TRANSMITTER_CONTROL register.  SPIM does
        // this too.  This simulates the time required for the display unit to process the
        // TRANSMITTER_DATA.
        addAsObserver(Memory.configuration.text.low,Memory.configuration.text.high);
    }

    /**
     * Method that constructs the main display area.  It is organized vertically
     * into two major components: the display and the keyboard.  The display itself
     * is a JTextArea and it echoes characters placed into the low order byte of
     * the Transmitter Data location, 0xffff000c.  They keyboard is also a JTextArea
     * places each typed character into the Receive Data location 0xffff0004.
     *
     * @return the GUI component containing these two areas
     */
    protected JComponent buildMainDisplayArea() {
        // Changed arrangement of the display and keyboard panels from GridLayout(2,1)
        // to BorderLayout to hold a JSplitPane containing both panels.  This permits user
        // to apportion the relative sizes of the display and keyboard panels within
        // the overall frame.  Will be convenient for use with the new random-access
        // display positioning feature.  Previously, both the display and the keyboard
        // text areas were equal in size and there was no way for the user to change that.
        // DPS 17-July-2014
        keyboardAndDisplay = new JPanel(new BorderLayout());
        JSplitPane both = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildDisplay(), buildKeyboard());
        both.setResizeWeight(0.5);
        keyboardAndDisplay.add(both);
        return keyboardAndDisplay;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Rest of the protected methods.  These all override do-nothing methods inherited from
    //  the abstract superclass.
    //////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void processRISCVUpdate(Observable memory, AccessNotice accessNotice) {
        MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;
        // If the program has just read (loaded) the receiver (keyboard) data register,
        // then clear the Ready bit to indicate there is no longer a keystroke available.
        // If Ready bit was initially clear, they'll get the old keystroke -- serves 'em right
        // for not checking!
        if (notice.getAddress() == RECEIVER_DATA && notice.getAccessType() == AccessNotice.READ) {
            updateMMIOControl(RECEIVER_CONTROL, readyBitCleared(RECEIVER_CONTROL));
        }
        // The program has just written (stored) the transmitter (display) data register.  If transmitter
        // Ready bit is clear, device is not ready yet so ignore this event -- serves 'em right for not checking!
        // If transmitter Ready bit is set, then clear it to indicate the display device is processing the character.
        // Also start an intruction counter that will simulate the delay of the slower
        // display device processing the character.
        if (isReadyBitSet(TRANSMITTER_CONTROL) && notice.getAddress() == TRANSMITTER_DATA && notice.getAccessType() == AccessNotice.WRITE) {
            updateMMIOControl(TRANSMITTER_CONTROL, readyBitCleared(TRANSMITTER_CONTROL));
            intWithCharacterToDisplay = notice.getValue();
            if (!displayAfterDelay) displayCharacter(intWithCharacterToDisplay);
            this.countingInstructions = true;
            this.instructionCount = 0;
            this.transmitDelayInstructionCountLimit = generateDelay();
        }
        // We have been notified of an instruction execution.
        // If we are in transmit delay period, increment instruction count and if limit
        // has been reached, set the transmitter Ready flag to indicate the program
        // can write another character to the transmitter data register.  If the Interrupt-Enabled
        // bit had been set by the program, generate an interrupt!
        if (this.countingInstructions &&
                notice.getAccessType() == AccessNotice.READ && Memory.inTextSegment(notice.getAddress())) {
            this.instructionCount++;
            if (this.instructionCount >= this.transmitDelayInstructionCountLimit) {
                if (displayAfterDelay) displayCharacter(intWithCharacterToDisplay);
                this.countingInstructions = false;
                int updatedTransmitterControl = readyBitSet(TRANSMITTER_CONTROL);
                updateMMIOControl(TRANSMITTER_CONTROL, updatedTransmitterControl);
                if (updatedTransmitterControl != 1) {
                    InterruptController.registerExternalInterrupt(EXTERNAL_INTERRUPT_DISPLAY);
                }
            }
        }
    }

    private static final char CLEAR_SCREEN = 12; // ASCII Form Feed
    private static final char SET_CURSOR_X_Y = 7; // ASCII Bell  (ding ding!)

    // Method to display the character stored in the low-order byte of
    // the parameter.  We also recognize two non-printing characters:
    //  Decimal 12 (Ascii Form Feed) to clear the display
    //  Decimal  7 (Ascii Bell) to place the cursor at a specified (X,Y) position.
    //             of a virtual text terminal.  The position is specified in the high
    //             order 24 bits of the transmitter word (X in 20-31, Y in 8-19).
    //             Thus the parameter is the entire word, not just the low-order byte.
    // Once the latter is performed, the display mode changes to random
    // access, which has repercussions for the implementation of character display.
    private void displayCharacter(int intWithCharacterToDisplay) {
        char characterToDisplay = (char) (intWithCharacterToDisplay & 0x000000FF);
        if (characterToDisplay == CLEAR_SCREEN) {
            initializeDisplay(displayRandomAccessMode);
        } else if (characterToDisplay == SET_CURSOR_X_Y) {
            // First call will activate random access mode.
            // We're using JTextArea, where caret has to be within text.
            // So initialize text to all spaces to fill the JTextArea to its
            // current capacity.  Then set caret.  Subsequent character
            // displays will replace, not append, in the text.
            if (!displayRandomAccessMode) {
                displayRandomAccessMode = true;
                initializeDisplay(displayRandomAccessMode);
            }
            // For SET_CURSOR_X_Y, we need data from the rest of the word.
            // High order 3 bytes are split in half to store (X,Y) value.
            // High 12 bits contain X value, next 12 bits contain Y value.
            int x = (intWithCharacterToDisplay & 0xFFF00000) >>> 20;
            int y = (intWithCharacterToDisplay & 0x000FFF00) >>> 8;
            // If X or Y values are outside current range, set to range limit.
            if (x < 0) x = 0;
            if (x >= columns) x = columns - 1;
            if (y < 0) y = 0;
            if (y >= rows) y = rows - 1;
            // display is a JTextArea whose character positioning in the text is linear.
            // Converting (row,column) to linear position requires knowing how many columns
            // are in each row.  I add one because each row except the last ends with '\n' that
            // does not count as a column but occupies a position in the text string.
            // The values of rows and columns is set in initializeDisplay().
            display.setCaretPosition(y * (columns + 1) + x);
        } else {
            if (displayRandomAccessMode) {
                try {
                    int caretPosition = display.getCaretPosition();
                    // if caret is positioned at the end of a line (at the '\n'), skip over the '\n'
                    if ((caretPosition + 1) % (columns + 1) == 0) {
                        caretPosition++;
                        display.setCaretPosition(caretPosition);
                    }
                    display.replaceRange("" + characterToDisplay, caretPosition, caretPosition + 1);
                } catch (IllegalArgumentException e) {
                    // tried to write off the end of the defined grid.
                    display.setCaretPosition(display.getCaretPosition() - 1);
                    display.replaceRange("" + characterToDisplay, display.getCaretPosition(), display.getCaretPosition() + 1);
                }
            } else {
                display.append("" + characterToDisplay);
            }
        }
    }

    @Override
    protected void initializePostGUI() {
        initializeTransmitDelaySimulator();
        keyEventAccepter.requestFocusInWindow();
    }


    /**
     * Method to reset counters and display when the Reset button selected.
     * Overrides inherited method that does nothing.
     */
    protected void reset() {
        displayRandomAccessMode = false;
        initializeTransmitDelaySimulator();
        initializeDisplay(displayRandomAccessMode);
        keyEventAccepter.setText("");
        ((TitledBorder) displayPanel.getBorder()).setTitle(displayPanelTitle);
        displayPanel.repaint();
        keyEventAccepter.requestFocusInWindow();
        updateMMIOControl(TRANSMITTER_CONTROL, readyBitSet(TRANSMITTER_CONTROL));
    }


    // The display JTextArea (top half) is initialized either to the empty
    // string, or to a string filled with lines of spaces. It will do the
    // latter only if the program has sent the BELL character (Ascii 7) to
    // the transmitter.  This sets the caret (cursor) to a specific (x,y) position
    // on a text-based virtual display.  The lines of spaces is necessary because
    // the caret can only be placed at a position within the current text string.
    private void initializeDisplay(boolean randomAccess) {
        String initialText = "";
        if (randomAccess) {
            Dimension textDimensions = getDisplayPanelTextDimensions();
            columns = (int) textDimensions.getWidth();
            rows = (int) textDimensions.getHeight();
            repaintDisplayPanelBorder();
            char[] charArray = new char[columns];
            Arrays.fill(charArray, VT_FILL);
            String row = new String(charArray);
            StringBuffer str = new StringBuffer(row);
            for (int i = 1; i < rows; i++) {
                str.append("\n" + row);
            }
            initialText = str.toString();
        }
        display.setText(initialText);
        display.setCaretPosition(0);
    }

    // Update display window title with current text display capacity (columns and rows)
    // This will be called when window resized or font changed.
    private void repaintDisplayPanelBorder() {
        Dimension size = this.getDisplayPanelTextDimensions();
        int cols = (int) size.getWidth();
        int rows = (int) size.getHeight();
        int caretPosition = display.getCaretPosition();
        String stringCaretPosition;
        // display position as stream or 2D depending on random access
        if (displayRandomAccessMode) {
            //             if ( caretPosition == rows*(columns+1)+1) {
            //                stringCaretPosition = "(0,0)";
            //             }
            //             else if ( (caretPosition+1) % (columns+1) == 0) {
            //                stringCaretPosition = "(0,"+((caretPosition/(columns+1))+1)+")";
            //             }
            //             else {
            //                stringCaretPosition = "("+(caretPosition%(columns+1))+","+(caretPosition/(columns+1))+")";
            //             }
            if (((caretPosition + 1) % (columns + 1) != 0)) {
                stringCaretPosition = "(" + (caretPosition % (columns + 1)) + "," + (caretPosition / (columns + 1)) + ")";
            } else if (((caretPosition + 1) % (columns + 1) == 0) && ((caretPosition / (columns + 1)) + 1 == rows)) {
                stringCaretPosition = "(" + (caretPosition % (columns + 1) - 1) + "," + (caretPosition / (columns + 1)) + ")";
            } else {
                stringCaretPosition = "(0," + ((caretPosition / (columns + 1)) + 1) + ")";
            }
        } else {
            stringCaretPosition = "" + caretPosition;
        }
        String title = displayPanelTitle + ", cursor " + stringCaretPosition + ", area " + cols + " x " + rows;
        ((TitledBorder) displayPanel.getBorder()).setTitle(title);
        displayPanel.repaint();
    }


    // Calculate text display capacity of display window. Text dimensions are based
    // on pixel dimensions of window divided by font size properties.
    private Dimension getDisplayPanelTextDimensions() {
        Dimension areaSize = display.getSize();
        int widthInPixels = (int) areaSize.getWidth();
        int heightInPixels = (int) areaSize.getHeight();
        FontMetrics metrics = getFontMetrics(display.getFont());
        int rowHeight = metrics.getHeight();
        int charWidth = metrics.charWidth('m');
        // Estimate number of columns/rows of text that will fit in current window with current font.
        // I subtract 1 because initial tests showed slight scroll otherwise.
        return new Dimension(widthInPixels / charWidth - 1, heightInPixels / rowHeight - 1);
    }


    // Trigger recalculation and update of display text dimensions when window resized.
    private class DisplayResizeAdapter extends ComponentAdapter {
        public void componentResized(ComponentEvent e) {
            getDisplayPanelTextDimensions();
            repaintDisplayPanelBorder();
        }
    }

    @Override
    protected JComponent getHelpComponent() {
        final String helpContent =
                "Keyboard And Display MMIO Simulator\n\n" +
                        "Use this program to simulate Memory-Mapped I/O (MMIO) for a keyboard input device and character " +
                        "display output device.  It may be run either from Tools menu or as a stand-alone application. " +
                        "For the latter, simply write a driver to instantiate a rars.tools.KeyboardAndDisplaySimulator object " +
                        "and invoke its go() method.\n" +
                        "\n" +
                        "While the tool is connected to the program, each keystroke in the text area causes the corresponding ASCII " +
                        "code to be placed in the Receiver Data register (low-order byte of memory word " + Binary.intToHexString(RECEIVER_DATA) + "), and the " +
                        "Ready bit to be set to 1 in the Receiver Control register (low-order bit of " + Binary.intToHexString(RECEIVER_CONTROL) + ").  The Ready " +
                        "bit is automatically reset to 0 when the program reads the Receiver Data using an 'lw' instruction.\n" +
                        "\n" +
                        "A program may write to the display area by detecting the Ready bit set (1) in the Transmitter Control " +
                        "register (low-order bit of memory word " + Binary.intToHexString(TRANSMITTER_CONTROL) + "), then storing the ASCII code of the character to be " +
                        "displayed in the Transmitter Data register (low-order byte of " + Binary.intToHexString(TRANSMITTER_DATA) + ") using a 'sw' instruction.  This " +
                        "triggers the simulated display to clear the Ready bit to 0, delay awhile to simulate processing the data, " +
                        "then set the Ready bit back to 1.  The delay is based on a count of executed instructions.\n" +
                        "\n" +
                        "In a polled approach to I/O, a program idles in a loop, testing the device's Ready bit on each " +
                        "iteration until it is set to 1 before proceeding.  This tool also supports an interrupt-driven approach " +
                        "which requires the program to provide an interrupt handler but allows it to perform useful processing " +
                        "instead of idly looping.  When the device is ready, it signals an interrupt and the RARS simuator will " +
                        "transfer control to the interrupt handler. Interrupt-driven I/O is enabled " +
                        "when the program sets the Interrupt-Enable bit in the device's control register.  Details below.\n" +
                        "\n" +
                        "Upon setting the Receiver Controller's Ready bit to 1, its Interrupt-Enable bit (bit position 1) is tested. " +
                        "If 1, then an External Interrupt will be generated. The Interrupt-Enable " +
                        "bit is 0 by default and has to be set by the program if interrupt-driven input is desired.  Interrupt-driven " +
                        "input permits the program to perform useful tasks instead of idling in a loop polling the Receiver Ready bit!  " +
                        "Very event-oriented.  The Ready bit is supposed to be read-only but in RARS it is not.\n" +
                        "\n" +
                        "A similar test and potential response occurs when the Transmitter Controller's Ready bit is set to 1.  This " +
                        "occurs after the simulated delay described above.  The only difference that utval will have a different code.  This permits you to " +
                        "write programs that perform interrupt-driven output - the program can perform useful tasks while the " +
                        "output device is processing its data.  Much better than idling in a loop polling the Transmitter Ready bit! " +
                        "The Ready bit is supposed to be read-only but in RARS it is not.\n" +
                        "\n" +
                        "IMPORTANT NOTE: The Transmitter Controller Ready bit is set to its initial value of 1 only when you click the tool's " +
                        "'Connect to Program' button ('Assemble and Run' in the stand-alone version) or the tool's Reset button!  If you run a " +
                        "program and reset it in RARS, the controller's Ready bit is cleared to 0!  Configure the Data Segment Window to " +
                        "display the MMIO address range so you can directly observe values stored in the MMIO addresses given above.\n" +
                        "\n" +
                        "Clear the display window from the program:\n" +
                        "\n" +
                        "When ASCII 12 (form feed) is stored in the Transmitter Data register, the tool's Display window will be cleared " +
                        "following the specified transmission delay.\n" +
                        "\n" +
                        "Simulate a text-based virtual terminal with (x,y) positioning:\n" +
                        "\n" +
                        "When ASCII 7 (bell) is stored in the Transmitter Data register, the cursor in the tool's Display window will " +
                        "be positioned at the (X,Y) coordinate specified by its high-order 3 bytes, following the specfied transmission delay. " +
                        "Place the X position (column) in bit positions 20-31 of the " +
                        "Transmitter Data register and place the Y position (row) in bit positions 8-19.  The cursor is not displayed " +
                        "but subsequent transmitted characters will be displayed starting at that position. Position (0,0) is at upper left. " +
                        "Why did I select the ASCII Bell character?  Just for fun!\n" +
                        "\n" +
                        "The dimensions (number of columns and rows) of the virtual text-based terminal are calculated based on the display " +
                        "window size and font specifications.  This calculation occurs during program execution upon first use of the ASCII 7 code. " +
                        "It will not change until the Reset button is clicked, even if the window is resized.  The window dimensions are included in " +
                        "its title, which will be updated upon window resize or font change.  No attempt is made to reposition data characters already " +
                        "transmitted by the program.  To change the dimensions of the virtual terminal, resize the Display window as desired (note there " +
                        "is an adjustible splitter between the Display and Keyboard windows) then click the tool's Reset button.  " +
                        "Implementation detail: the window is implemented by a JTextArea to which text is written as a string. " +
                        "Its caret (cursor) position is required to be a position within the string.  I simulated a text terminal with random positioning " +
                        "by pre-allocating a string of spaces with one space per (X,Y) position and an embedded newline where each line ends. Each character " +
                        "transmitted to the window thus replaces an existing character in the string.\n" +
                        "\n" +
                        "Thanks to Eric Wang at Washington State University, who requested these features to enable use of this display as the target " +
                        "for programming MMIO text-based games.";
        JButton help = new JButton("Help");
        help.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        JTextArea ja = new JTextArea(helpContent);
                        ja.setRows(30);
                        ja.setColumns(60);
                        ja.setLineWrap(true);
                        ja.setWrapStyleWord(true);
                        // TODO: potentially implement method 2
                        // Make the Help dialog modeless (can remain visible while working with other components).
                        // Unfortunately, JOptionPane.showMessageDialog() cannot be made modeless.  I found two
                        // workarounds:
                        //  (1) Use JDialog and the additional work that requires
                        //  (2) create JOptionPane object, get JDialog from it, make the JDialog modeless
                        // Solution 2 is shorter but requires Java 1.6.  Trying to keep MARS at 1.5.  So we
                        // do it the hard way.  DPS 16-July-2014
                        final JDialog d;
                        final String title = "Simulating the Keyboard and Display";
                        // The following is necessary because there are different JDialog constructors for Dialog and
                        // Frame and theWindow is declared a Window, superclass for both.
                        d = (theWindow instanceof Dialog) ? new JDialog((Dialog) theWindow, title, false)
                                : new JDialog((Frame) theWindow, title, false);
                        d.setSize(ja.getPreferredSize());
                        d.getContentPane().setLayout(new BorderLayout());
                        d.getContentPane().add(new JScrollPane(ja), BorderLayout.CENTER);
                        JButton b = new JButton("Close");
                        b.addActionListener(
                                new ActionListener() {
                                    public void actionPerformed(ActionEvent ev) {
                                        d.setVisible(false);
                                        d.dispose();
                                    }
                                });
                        JPanel p = new JPanel(); // Flow layout will center button.
                        p.add(b);
                        d.getContentPane().add(p, BorderLayout.SOUTH);
                        d.setLocationRelativeTo(theWindow);
                        d.setVisible(true);
                        // This alternative technique is simpler than the above but requires java 1.6!  DPS 16-July-2014
                        //       JOptionPane theStuff = new JOptionPane(new JScrollPane(ja),JOptionPane.INFORMATION_MESSAGE,
                        //            JOptionPane.DEFAULT_OPTION, null, new String[]{"Close"} );
                        //       JDialog theDialog = theStuff.createDialog(theWindow, "Simulating the Keyboard and Display");
                        //       theDialog.setModal(false);
                        //       theDialog.setVisible(true);
                        // The original code. Cannot be made modeless.
                        //       JOptionPane.showMessageDialog(theWindow, new JScrollPane(ja),
                        //           "Simulating the Keyboard and Display", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
        return help;
    }


    //////////////////////////////////////////////////////////////////////////////////////
    //  Private methods defined to support the above.
    //////////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////////////////////////////
    // UI components and layout for upper part of GUI, where simulated display is located.
    private JComponent buildDisplay() {
        displayPanel = new JPanel(new BorderLayout());
        TitledBorder tb = new TitledBorder(displayPanelTitle);
        tb.setTitleJustification(TitledBorder.CENTER);
        displayPanel.setBorder(tb);
        display = new JTextArea();
        display.setFont(defaultFont);
        display.setEditable(false);
        display.setMargin(textAreaInsets);
        updateDisplayBorder = new DisplayResizeAdapter();
        // 	To update display of size in the Display text area when window or font size changes.
        display.addComponentListener(updateDisplayBorder);
        // 	To update display of caret position in the Display text area when caret position changes.
        display.addCaretListener(
                new CaretListener() {
                    public void caretUpdate(CaretEvent e) {
                        simulator.repaintDisplayPanelBorder();
                    }
                }
        );

        // 2011-07-29: Patrik Lundin, patrik@lundin.info
        // Added code so display autoscrolls.
        DefaultCaret caret = (DefaultCaret) display.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        // end added autoscrolling

        displayScrollPane = new JScrollPane(display);
        displayScrollPane.setPreferredSize(preferredTextAreaDimension);

        displayPanel.add(displayScrollPane);
        displayOptions = new JPanel();
        delayTechniqueChooser = new JComboBox<>(delayTechniques);
        delayTechniqueChooser.setToolTipText("Technique for determining simulated transmitter device processing delay");
        delayTechniqueChooser.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        transmitDelayInstructionCountLimit = generateDelay();
                    }
                });
        delayLengthPanel = new DelayLengthPanel();
        displayAfterDelayCheckBox = new JCheckBox("DAD", true);
        displayAfterDelayCheckBox.setToolTipText("Display After Delay: if checked, transmitter data not displayed until after delay");
        displayAfterDelayCheckBox.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        displayAfterDelay = displayAfterDelayCheckBox.isSelected();
                    }
                });

        //font button to display font
        fontButton = new JButton("Font");
        fontButton.setToolTipText("Select the font for the display panel");
        fontButton.addActionListener(new FontChanger());
        displayOptions.add(fontButton);
        displayOptions.add(displayAfterDelayCheckBox);
        displayOptions.add(delayTechniqueChooser);
        displayOptions.add(delayLengthPanel);
        displayPanel.add(displayOptions, BorderLayout.SOUTH);
        return displayPanel;
    }


    //////////////////////////////////////////////////////////////////////////////////////
    // UI components and layout for lower part of GUI, where simulated keyboard is located.
    private JComponent buildKeyboard() {
        keyboardPanel = new JPanel(new BorderLayout());
        keyEventAccepter = new JTextArea();
        keyEventAccepter.setEditable(true);
        keyEventAccepter.setFont(defaultFont);
        keyEventAccepter.setMargin(textAreaInsets);
        keyAccepterScrollPane = new JScrollPane(keyEventAccepter);
        keyAccepterScrollPane.setPreferredSize(preferredTextAreaDimension);
        keyEventAccepter.addKeyListener(new KeyboardKeyListener());
        keyboardPanel.add(keyAccepterScrollPane);
        TitledBorder tb = new TitledBorder(keyboardPanelTitle);
        tb.setTitleJustification(TitledBorder.CENTER);
        keyboardPanel.setBorder(tb);
        return keyboardPanel;
    }

    ////////////////////////////////////////////////////////////////////
    // update the MMIO Control register memory cell. We will delegate.
    private void updateMMIOControl(int addr, int intValue) {
        updateMMIOControlAndData(addr, intValue, 0, 0, true);
    }


    /////////////////////////////////////////////////////////////////////
    // update the MMIO Control and Data register pair -- 2 memory cells. We will delegate.
    private void updateMMIOControlAndData(int controlAddr, int controlValue, int dataAddr, int dataValue) {
        updateMMIOControlAndData(controlAddr, controlValue, dataAddr, dataValue, false);
    }


    /////////////////////////////////////////////////////////////////////////////////////////////////////
    // This one does the work: update the MMIO Control and optionally the Data register as well
    // NOTE: last argument TRUE means update only the MMIO Control register; FALSE means update both Control and Data.
    private void updateMMIOControlAndData(int controlAddr, int controlValue, int dataAddr, int dataValue, boolean controlOnly) {
        if (!this.isBeingUsedAsATool || (this.isBeingUsedAsATool && connectButton.isConnected())) {
            Globals.memoryAndRegistersLock.lock();
            try {
                try {
                    Globals.memory.setRawWord(controlAddr, controlValue);
                    if (!controlOnly) Globals.memory.setRawWord(dataAddr, dataValue);
                } catch (AddressErrorException aee) {
                    System.out.println("Tool author specified incorrect MMIO address!" + aee);
                    System.exit(0);
                }
            } finally {
                Globals.memoryAndRegistersLock.unlock();
            }
            // HERE'S A HACK!!  Want to immediately display the updated memory value in MARS
            // but that code was not written for event-driven update (e.g. Observer) --
            // it was written to poll the memory cells for their values.  So we force it to do so.

            if (Globals.getGui() != null && Globals.getGui().getMainPane().getExecutePane().getTextSegmentWindow().getCodeHighlighting()) {
                Globals.getGui().getMainPane().getExecutePane().getDataSegmentWindow().updateValues();
            }
        }
    }


    /////////////////////////////////////////////////////////////////////
    // Return value of the given MMIO control register after ready (low order) bit set (to 1).
    // Have to preserve the value of Interrupt Enable bit (bit 1)
    private static boolean isReadyBitSet(int mmioControlRegister) {
        try {
            return (Globals.memory.get(mmioControlRegister, Memory.WORD_LENGTH_BYTES) & 1) == 1;
        } catch (AddressErrorException aee) {
            System.out.println("Tool author specified incorrect MMIO address!" + aee);
            System.exit(0);
        }
        return false; // to satisfy the compiler -- this will never happen.
    }


    /////////////////////////////////////////////////////////////////////
    // Return value of the given MMIO control register after ready (low order) bit set (to 1).
    // Have to preserve the value of Interrupt Enable bit (bit 1)
    private static int readyBitSet(int mmioControlRegister) {
        try {
            return Globals.memory.get(mmioControlRegister, Memory.WORD_LENGTH_BYTES) | 1;
        } catch (AddressErrorException aee) {
            System.out.println("Tool author specified incorrect MMIO address!" + aee);
            System.exit(0);
        }
        return 1; // to satisfy the compiler -- this will never happen.
    }

    /////////////////////////////////////////////////////////////////////
    //  Return value of the given MMIO control register after ready (low order) bit cleared (to 0).
    // Have to preserve the value of Interrupt Enable bit (bit 1). Bits 2 and higher don't matter.
    private static int readyBitCleared(int mmioControlRegister) {
        try {
            return Globals.memory.get(mmioControlRegister, Memory.WORD_LENGTH_BYTES) & 2;
        } catch (AddressErrorException aee) {
            System.out.println("Tool author specified incorrect MMIO address!" + aee);
            System.exit(0);
        }
        return 0; // to satisfy the compiler -- this will never happen.
    }


    /////////////////////////////////////////////////////////////////////
    // Transmit delay is simulated by counting instruction executions.
    // Here we simly initialize (or reset) the variables.
    private void initializeTransmitDelaySimulator() {
        this.countingInstructions = false;
        this.instructionCount = 0;
        this.transmitDelayInstructionCountLimit = this.generateDelay();
    }


    /////////////////////////////////////////////////////////////////////
    //  Calculate transmitter delay (# instruction executions) based on
    //  current combo box and slider settings.

    private int generateDelay() {
        double sliderValue = delayLengthPanel.getDelayLength();
        TransmitterDelayTechnique technique = (TransmitterDelayTechnique) delayTechniqueChooser.getSelectedItem();
        return technique.generateDelay(sliderValue);
    }


    ///////////////////////////////////////////////////////////////////////////////////
    //
    //  Class to grab keystrokes going to keyboard echo area and send them to MMIO area
    //

    private class KeyboardKeyListener implements KeyListener {
        public void keyTyped(KeyEvent e) {
            int updatedReceiverControl = readyBitSet(RECEIVER_CONTROL);
            updateMMIOControlAndData(RECEIVER_CONTROL, updatedReceiverControl, RECEIVER_DATA, e.getKeyChar() & 0x00000ff);
            if (updatedReceiverControl != 1) {
                InterruptController.registerExternalInterrupt(EXTERNAL_INTERRUPT_KEYBOARD);
            }
        }


        /* Ignore key pressed event from the text field. */
        public void keyPressed(KeyEvent e) {
        }

        /* Ignore key released event from the text field. */
        public void keyReleased(KeyEvent e) {
        }
    }


    //////////////////////////////////////////////////////////////////////////////////
    //
    //  Class for selecting transmitter delay lengths (# of instruction executions).
    //

    private class DelayLengthPanel extends JPanel {
        private final static int DELAY_INDEX_MIN = 0;
        private final static int DELAY_INDEX_MAX = 40;
        private final static int DELAY_INDEX_INIT = 4;
        private double[] delayTable = {
                1, 2, 3, 4, 5, 10, 20, 30, 40, 50, 100,  // 0-10
                150, 200, 300, 400, 500, 600, 700, 800, 900, 1000,  //11-20
                1500, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000,  //21-30
                20000, 40000, 60000, 80000, 100000, 200000, 400000, 600000, 800000, 1000000//31-40
        };
        private JLabel sliderLabel = null;
        private volatile int delayLengthIndex = DELAY_INDEX_INIT;

        public DelayLengthPanel() {
            super(new BorderLayout());
            delayLengthSlider = new JSlider(JSlider.HORIZONTAL, DELAY_INDEX_MIN, DELAY_INDEX_MAX, DELAY_INDEX_INIT);
            delayLengthSlider.setSize(new Dimension(100, (int) delayLengthSlider.getSize().getHeight()));
            delayLengthSlider.setMaximumSize(delayLengthSlider.getSize());
            delayLengthSlider.addChangeListener(new DelayLengthListener());
            sliderLabel = new JLabel(setLabel(delayLengthIndex));
            sliderLabel.setHorizontalAlignment(JLabel.CENTER);
            sliderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            this.add(sliderLabel, BorderLayout.NORTH);
            this.add(delayLengthSlider, BorderLayout.CENTER);
            this.setToolTipText("Parameter for simulated delay length (instruction execution count)");
        }

        // returns current delay length setting, in instructions.
        public double getDelayLength() {
            return delayTable[delayLengthIndex];
        }


        // set label wording depending on current speed setting
        private String setLabel(int index) {
            return "Delay length: " + ((int) delayTable[index]) + " instruction executions";
        }


        // Both revises label as user slides and updates current index when sliding stops.
        private class DelayLengthListener implements ChangeListener {
            public void stateChanged(ChangeEvent e) {
                JSlider source = (JSlider) e.getSource();
                if (!source.getValueIsAdjusting()) {
                    delayLengthIndex = source.getValue();
                    transmitDelayInstructionCountLimit = generateDelay();
                } else {
                    sliderLabel.setText(setLabel(source.getValue()));
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////
    //
    //Interface and classes for Transmitter Delay-generating techniques.
    //

    private interface TransmitterDelayTechnique {
        int generateDelay(double parameter);
    }

    // Delay value is fixed, and equal to slider value.
    private class FixedLengthDelay implements TransmitterDelayTechnique {
        public String toString() {
            return "Fixed transmitter delay, select using slider";
        }

        public int generateDelay(double fixedDelay) {
            return (int) fixedDelay;
        }
    }

    // Randomly pick value from range 1 to slider setting, uniform distribution
    // (each value has equal probability of being chosen).
    private class UniformlyDistributedDelay implements TransmitterDelayTechnique {
        Random randu;

        public UniformlyDistributedDelay() {
            randu = new Random();
        }

        public String toString() {
            return "Uniformly distributed delay, min=1, max=slider";
        }

        public int generateDelay(double max) {
            return randu.nextInt((int) max) + 1;
        }
    }

    // Pretty badly-hacked normal distribution, but is more realistic than uniform!
    // Get sample from Normal(0,1) -- mean=0, s.d.=1 -- multiply it by slider
    // value, take absolute value to make sure we don't get negative,
    // add 1 to make sure we don't get 0.
    private class NormallyDistributedDelay implements TransmitterDelayTechnique {
        Random randn;

        public NormallyDistributedDelay() {
            randn = new Random();
        }

        public String toString() {
            return "'Normally' distributed delay: floor(abs(N(0,1)*slider)+1)";
        }

        public int generateDelay(double mult) {
            return (int) (Math.abs(randn.nextGaussian() * mult) + 1);
        }
    }

    /**
     * Font dialog for the display panel
     * Almost all of the code is used from the SettingsHighlightingAction
     * class.
     */

    private class FontSettingDialog extends AbstractFontSettingDialog {
        private boolean resultOK;

        public FontSettingDialog(Frame owner, String title, Font currentFont) {
            super(owner, title, true, currentFont);
        }

        private Font showDialog() {
            resultOK = true;
            // Because dialog is modal, this blocks until user terminates the dialog.
            this.setVisible(true);
            return resultOK ? getFont() : null;
        }

        protected void closeDialog() {
            this.setVisible(false);
            // Update display text dimensions based on current font and size. DPS 22-July-2014
            updateDisplayBorder.componentResized(null);
        }

        private void performCancel() {
            resultOK = false;
        }

        // Control buttons for the dialog.
        protected Component buildControlPanel() {
            Box controlPanel = Box.createHorizontalBox();
            JButton okButton = new JButton("OK");
            okButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            apply(getFont());
                            closeDialog();
                        }
                    });
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            performCancel();
                            closeDialog();
                        }
                    });
            JButton resetButton = new JButton("Reset");
            resetButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            reset();
                        }
                    });
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(okButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(cancelButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(resetButton);
            controlPanel.add(Box.createHorizontalGlue());
            return controlPanel;
        }

        // Change the font for the keyboard and display
        protected void apply(Font font) {
            display.setFont(font);
            keyEventAccepter.setFont(font);
        }

    }

    private class FontChanger implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            JButton button = (JButton) e.getSource();
            FontSettingDialog fontDialog = new FontSettingDialog(null, "Select Text Font", display.getFont());
            Font newFont = fontDialog.showDialog();
        }
    }


}