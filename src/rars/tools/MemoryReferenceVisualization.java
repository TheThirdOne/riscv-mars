package rars.tools;

import rars.riscv.hardware.AccessNotice;
import rars.riscv.hardware.Memory;
import rars.riscv.hardware.MemoryAccessNotice;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Observable;

/*
Copyright (c) 2003-2006,  Pete Sanderson and Kenneth Vollmar

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
 * Memory reference visualization.  It can be run either as a stand-alone Java application having
 * access to the rars package, or through RARS as an item in its Tools menu.  It makes
 * maximum use of methods inherited from its abstract superclass AbstractToolAndApplication.
 * Pete Sanderson, verison 1.0, 14 November 2006.
 */
public class MemoryReferenceVisualization extends AbstractToolAndApplication {

    private static String version = "Version 1.0";
    private static String heading = "Visualizing memory reference patterns";

    // Major GUI components
    private JComboBox<String> wordsPerUnitSelector, visualizationUnitPixelWidthSelector, visualizationUnitPixelHeightSelector,
            visualizationPixelWidthSelector, visualizationPixelHeightSelector, displayBaseAddressSelector;
    private JCheckBox drawHashMarksSelector;
    private Graphics drawingArea;
    private JPanel canvas;
    private JPanel results;

    // Some GUI settings
    private EmptyBorder emptyBorder = new EmptyBorder(4, 4, 4, 4);
    private Font countFonts = new Font("Times", Font.BOLD, 12);
    private Color backgroundColor = Color.WHITE;

    // Values for Combo Boxes

    private static final String[] wordsPerUnitChoices = {"1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024", "2048"};
    private static final int defaultWordsPerUnitIndex = 0;
    private static final String[] visualizationUnitPixelWidthChoices = {"1", "2", "4", "8", "16", "32"};
    private static final int defaultVisualizationUnitPixelWidthIndex = 4;
    private static final String[] visualizationUnitPixelHeightChoices = {"1", "2", "4", "8", "16", "32"};
    private static final int defaultVisualizationUnitPixelHeightIndex = 4;
    private static final String[] displayAreaPixelWidthChoices = {"64", "128", "256", "512", "1024"};
    private static final int defaultDisplayWidthIndex = 2;
    private static final String[] displayAreaPixelHeightChoices = {"64", "128", "256", "512", "1024"};
    private static final int defaultDisplayHeightIndex = 2;
    private static final boolean defaultDrawHashMarks = true;

    // Values for display canvas.  Note their initialization uses the identifiers just above.

    private int unitPixelWidth = Integer.parseInt(visualizationUnitPixelWidthChoices[defaultVisualizationUnitPixelWidthIndex]);
    private int unitPixelHeight = Integer.parseInt(visualizationUnitPixelHeightChoices[defaultVisualizationUnitPixelHeightIndex]);
    private int wordsPerUnit = Integer.parseInt(wordsPerUnitChoices[defaultWordsPerUnitIndex]);
    private int visualizationAreaWidthInPixels = Integer.parseInt(displayAreaPixelWidthChoices[defaultDisplayWidthIndex]);
    private int visualizationAreaHeightInPixels = Integer.parseInt(displayAreaPixelHeightChoices[defaultDisplayHeightIndex]);

    //`Values for mapping of reference counts to colors for display.

    // This array of (count,color) pairs must be kept sorted! count is low end of subrange.
    // This array will grow if user adds colors at additional counter points (see below).
    private CounterColor[] defaultCounterColors =
            {new CounterColor(0, Color.black),
                    new CounterColor(1, Color.blue),
                    new CounterColor(2, Color.green),
                    new CounterColor(3, Color.yellow),
                    new CounterColor(5, Color.orange),
                    new CounterColor(10, Color.red)
            };
    /*  Values for reference count color slider. These are all possible counter values for which
     *  colors can be assigned.  As you can see just above, not all these values are assigned
     *  a default color.
     */
    private int[] countTable = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,   // 0-10
            20, 30, 40, 50, 100, 200, 300, 400, 500, 1000,   // 11-20
            2000, 3000, 4000, 5000, 10000, 50000, 100000, 500000, 1000000          // 21-29
    };
    private static final int COUNT_INDEX_INIT = 10;  // array element #10, arbitrary starting point

    // The next four are initialized dynamically in initializeDisplayBaseChoices()
    private String[] displayBaseAddressChoices;
    private int[] displayBaseAddresses;
    private int defaultBaseAddressIndex;
    private int baseAddress;

    private Grid theGrid;
    private CounterColorScale counterColorScale;

    /**
     * Simple constructor, likely used to run a stand-alone memory reference visualizer.
     *
     * @param title   String containing title for title bar
     * @param heading String containing text for heading shown in upper part of window.
     */
    public MemoryReferenceVisualization(String title, String heading) {
        super(title, heading);
    }

    /**
     * Simple constructor, likely used by the RARS Tools menu mechanism
     */
    public MemoryReferenceVisualization() {
        super("Memory Reference Visualization, " + version, heading);
    }


    /**
     * Main provided for pure stand-alone use.  Recommended stand-alone use is to write a
     * driver program that instantiates a MemoryReferenceVisualization object then invokes its go() method.
     * "stand-alone" means it is not invoked from the RARS Tools menu.  "Pure" means there
     * is no driver program to invoke the application.
     */
    public static void main(String[] args) {
        new MemoryReferenceVisualization("Memory Reference Visualization stand-alone, " + version, heading).go();
    }


    @Override
    public String getName() {
        return "Memory Reference Visualization";
    }


    /**
     * Override the inherited method, which registers us as an Observer over the static data segment
     * (starting address 0x10010000) only.  This version will register us as observer over the
     * the memory range as selected by the base address combo box and capacity of the visualization display
     * (number of visualization elements times the number of memory words each one represents).
     * It does so by calling the inherited 2-parameter overload of this method.
     * If you use the inherited GUI buttons, this
     * method is invoked when you click "Connect" button on Tool or the
     * "Assemble and Run" button on a Rars-based app.
     */
    protected void addAsObserver() {
        int highAddress = baseAddress + theGrid.getRows() * theGrid.getColumns() * Memory.WORD_LENGTH_BYTES * wordsPerUnit;
        // Special case: baseAddress<0 means we're in kernel memory (0x80000000 and up) and most likely
        // in memory map address space (0xffff0000 and up).  In this case, we need to make sure the high address
        // does not drop off the high end of 32 bit address space.  Highest allowable word address is 0xfffffffc,
        // which is interpreted in Java int as -4.
        if (baseAddress < 0 && highAddress > -4) {
            highAddress = -4;
        }
        addAsObserver(baseAddress, highAddress);
    }


    /**
     * Method that constructs the main display area.  It is organized vertically
     * into two major components: the display configuration which an be modified
     * using combo boxes, and the visualization display which is updated as the
     * attached program executes.
     *
     * @return the GUI component containing these two areas
     */
    protected JComponent buildMainDisplayArea() {
        results = new JPanel();
        results.add(buildOrganizationArea());
        results.add(buildVisualizationArea());
        return results;
    }


    //////////////////////////////////////////////////////////////////////////////////////
    //  Rest of the protected methods.  These override do-nothing methods inherited from
    //  the abstract superclass.
    //////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void processRISCVUpdate(Observable memory, AccessNotice accessNotice) {
        incrementReferenceCountForAddress(((MemoryAccessNotice) accessNotice).getAddress());
    }


    /**
     * Initialize all JComboBox choice structures not already initialized at declaration.
     * Overrides inherited method that does nothing.
     */
    protected void initializePreGUI() {
        initializeDisplayBaseChoices();
        counterColorScale = new CounterColorScale(defaultCounterColors);
        // NOTE: Can't call "createNewGrid()" here because it uses settings from
        //       several combo boxes that have not been created yet.  But a default grid
        //       needs to be allocated for initial canvas display.
        theGrid = new Grid(visualizationAreaHeightInPixels / unitPixelHeight,
                visualizationAreaWidthInPixels / unitPixelWidth);
    }


    /**
     * The only post-GUI initialization is to create the initial Grid object based on the default settings
     * of the various combo boxes. Overrides inherited method that does nothing.
     */

    protected void initializePostGUI() {
        wordsPerUnit = getIntComboBoxSelection(wordsPerUnitSelector);
        theGrid = createNewGrid();
        updateBaseAddress();
    }


    /**
     * Method to reset counters and display when the Reset button selected.
     * Overrides inherited method that does nothing.
     */
    protected void reset() {
        resetCounts();
        updateDisplay();
    }

    @Override
    protected void updateDisplay() {
        canvas.repaint();
    }


    /**
     * Overrides default method, to provide a Help button for this tool/app.
     */
    protected JComponent getHelpComponent() {
        final String helpContent =
                "Use this program to visualize dynamic memory reference\n" +
                        "patterns in assembly programs.  It may be run either\n" +
                        "from Tools menu or as a stand-alone application.  For\n" +
                        "the latter, simply write a small driver to instantiate a\n" +
                        "MemoryReferenceVisualization object and invoke its go() method.\n" +
                        "\n" +
                        "You can easily learn to use this small program by playing with\n" +
                        "it!  For the best animation, set the program to run in\n" +
                        "timed mode using the Run Speed slider.  Each rectangular unit\n" +
                        "on the display represents one or more memory words (default 1)\n" +
                        "and each time a memory word is accessed by the program,\n" +
                        "its reference count is incremented then rendered in the color\n" +
                        "assigned to the count value.  You can change the count-color\n" +
                        "assignments using the count slider and color patch.  Select a\n" +
                        "counter value then click on the color patch to change the color.\n" +
                        "This color will apply beginning at the selected count and\n" +
                        "extending up to the next slider-provided count.\n";
        JButton help = new JButton("Help");
        help.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        JOptionPane.showMessageDialog(theWindow, helpContent);
                    }
                });
        return help;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Private methods defined to support the above.
    //////////////////////////////////////////////////////////////////////////////////////

    // UI components and layout for left half of GUI, where settings are specified.
    private JComponent buildOrganizationArea() {
        JPanel organization = new JPanel(new GridLayout(9, 1));

        drawHashMarksSelector = new JCheckBox();
        drawHashMarksSelector.setSelected(defaultDrawHashMarks);
        drawHashMarksSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        updateDisplay();
                    }
                });
        wordsPerUnitSelector = new JComboBox<>(wordsPerUnitChoices);
        wordsPerUnitSelector.setEditable(false);
        wordsPerUnitSelector.setBackground(backgroundColor);
        wordsPerUnitSelector.setSelectedIndex(defaultWordsPerUnitIndex);
        wordsPerUnitSelector.setToolTipText("Number of memory words represented by one visualization element (rectangle)");
        wordsPerUnitSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        wordsPerUnit = getIntComboBoxSelection(wordsPerUnitSelector);
                        reset();
                    }
                });
        visualizationUnitPixelWidthSelector = new JComboBox<>(visualizationUnitPixelWidthChoices);
        visualizationUnitPixelWidthSelector.setEditable(false);
        visualizationUnitPixelWidthSelector.setBackground(backgroundColor);
        visualizationUnitPixelWidthSelector.setSelectedIndex(defaultVisualizationUnitPixelWidthIndex);
        visualizationUnitPixelWidthSelector.setToolTipText("Width in pixels of rectangle representing memory access");
        visualizationUnitPixelWidthSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        unitPixelWidth = getIntComboBoxSelection(visualizationUnitPixelWidthSelector);
                        theGrid = createNewGrid();
                        updateDisplay();
                    }
                });
        visualizationUnitPixelHeightSelector = new JComboBox<>(visualizationUnitPixelHeightChoices);
        visualizationUnitPixelHeightSelector.setEditable(false);
        visualizationUnitPixelHeightSelector.setBackground(backgroundColor);
        visualizationUnitPixelHeightSelector.setSelectedIndex(defaultVisualizationUnitPixelHeightIndex);
        visualizationUnitPixelHeightSelector.setToolTipText("Height in pixels of rectangle representing memory access");
        visualizationUnitPixelHeightSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        unitPixelHeight = getIntComboBoxSelection(visualizationUnitPixelHeightSelector);
                        theGrid = createNewGrid();
                        updateDisplay();
                    }
                });
        visualizationPixelWidthSelector = new JComboBox<>(displayAreaPixelWidthChoices);
        visualizationPixelWidthSelector.setEditable(false);
        visualizationPixelWidthSelector.setBackground(backgroundColor);
        visualizationPixelWidthSelector.setSelectedIndex(defaultDisplayWidthIndex);
        visualizationPixelWidthSelector.setToolTipText("Total width in pixels of visualization area");
        visualizationPixelWidthSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        visualizationAreaWidthInPixels = getIntComboBoxSelection(visualizationPixelWidthSelector);
                        canvas.setPreferredSize(getDisplayAreaDimension());
                        canvas.setSize(getDisplayAreaDimension());
                        theGrid = createNewGrid();
                        canvas.repaint();
                        updateDisplay();
                    }
                });
        visualizationPixelHeightSelector = new JComboBox<>(displayAreaPixelHeightChoices);
        visualizationPixelHeightSelector.setEditable(false);
        visualizationPixelHeightSelector.setBackground(backgroundColor);
        visualizationPixelHeightSelector.setSelectedIndex(defaultDisplayHeightIndex);
        visualizationPixelHeightSelector.setToolTipText("Total height in pixels of visualization area");
        visualizationPixelHeightSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        visualizationAreaHeightInPixels = getIntComboBoxSelection(visualizationPixelHeightSelector);
                        canvas.setPreferredSize(getDisplayAreaDimension());
                        canvas.setSize(getDisplayAreaDimension());
                        theGrid = createNewGrid();
                        canvas.repaint();
                        updateDisplay();
                    }
                });
        displayBaseAddressSelector = new JComboBox<>(displayBaseAddressChoices);
        displayBaseAddressSelector.setEditable(false);
        displayBaseAddressSelector.setBackground(backgroundColor);
        displayBaseAddressSelector.setSelectedIndex(defaultBaseAddressIndex);
        displayBaseAddressSelector.setToolTipText("Base address for visualization area (upper left corner)");
        displayBaseAddressSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        // This may also affect what address range we should be registered as an Observer
                        // for.  The default (inherited) address range is the MIPS static data segment
                        // starting at 0x10010000. To change this requires override of
                        // AbstractToolAndApplication.addAsObserver().  The no-argument version of
                        // that method is called automatically  when "Connect" button is clicked for Tool
                        // and when "Assemble and Run" button is clicked for Rars application.
                        updateBaseAddress();
                        // If display base address is changed while connected to a program (this can only occur
                        // when being used as a Tool), we have to delete ourselves as an observer and re-register.
                        if (connectButton != null && connectButton.isConnected()) {
                            deleteAsObserver();
                            addAsObserver();
                        }
                        theGrid = createNewGrid();
                        updateDisplay();
                    }
                });

        // ALL COMPONENTS FOR "ORGANIZATION" SECTION

        JPanel hashMarksRow = getPanelWithBorderLayout();
        hashMarksRow.setBorder(emptyBorder);
        hashMarksRow.add(new JLabel("Show unit boundaries (grid marks)"), BorderLayout.WEST);
        hashMarksRow.add(drawHashMarksSelector, BorderLayout.EAST);

        JPanel wordsPerUnitRow = getPanelWithBorderLayout();
        wordsPerUnitRow.setBorder(emptyBorder);
        wordsPerUnitRow.add(new JLabel("Memory Words per Unit "), BorderLayout.WEST);
        wordsPerUnitRow.add(wordsPerUnitSelector, BorderLayout.EAST);

        JPanel unitWidthInPixelsRow = getPanelWithBorderLayout();
        unitWidthInPixelsRow.setBorder(emptyBorder);
        unitWidthInPixelsRow.add(new JLabel("Unit Width in Pixels "), BorderLayout.WEST);
        unitWidthInPixelsRow.add(visualizationUnitPixelWidthSelector, BorderLayout.EAST);

        JPanel unitHeightInPixelsRow = getPanelWithBorderLayout();
        unitHeightInPixelsRow.setBorder(emptyBorder);
        unitHeightInPixelsRow.add(new JLabel("Unit Height in Pixels "), BorderLayout.WEST);
        unitHeightInPixelsRow.add(visualizationUnitPixelHeightSelector, BorderLayout.EAST);

        JPanel widthInPixelsRow = getPanelWithBorderLayout();
        widthInPixelsRow.setBorder(emptyBorder);
        widthInPixelsRow.add(new JLabel("Display Width in Pixels "), BorderLayout.WEST);
        widthInPixelsRow.add(visualizationPixelWidthSelector, BorderLayout.EAST);

        JPanel heightInPixelsRow = getPanelWithBorderLayout();
        heightInPixelsRow.setBorder(emptyBorder);
        heightInPixelsRow.add(new JLabel("Display Height in Pixels "), BorderLayout.WEST);
        heightInPixelsRow.add(visualizationPixelHeightSelector, BorderLayout.EAST);

        JPanel baseAddressRow = getPanelWithBorderLayout();
        baseAddressRow.setBorder(emptyBorder);
        baseAddressRow.add(new JLabel("Base address for display "), BorderLayout.WEST);
        baseAddressRow.add(displayBaseAddressSelector, BorderLayout.EAST);

        ColorChooserControls colorChooserControls = new ColorChooserControls();

        // Lay 'em out in the grid...
        organization.add(hashMarksRow);
        organization.add(wordsPerUnitRow);
        organization.add(unitWidthInPixelsRow);
        organization.add(unitHeightInPixelsRow);
        organization.add(widthInPixelsRow);
        organization.add(heightInPixelsRow);
        organization.add(baseAddressRow);
        organization.add(colorChooserControls.colorChooserRow);
        organization.add(colorChooserControls.countDisplayRow);
        return organization;
    }

    // UI components and layout for right half of GUI, the visualization display area.
    private JComponent buildVisualizationArea() {
        canvas = new GraphicsPanel();
        canvas.setPreferredSize(getDisplayAreaDimension());
        canvas.setToolTipText("Memory reference count visualization area");
        return canvas;
    }

    // For greatest flexibility, initialize the display base choices directly from
    // the constants defined in the Memory class.  This method called prior to
    // building the GUI.  Here are current values from Memory.java:
    //textBaseAddress=0x00400000, dataSegmentBaseAddress=0x10000000, globalPointer=0x10008000
    //dataBaseAddress=0x10010000, heapBaseAddress=0x10040000, memoryMapBaseAddress=0xffff0000
    private void initializeDisplayBaseChoices() {
        int[] displayBaseAddressArray = {
                Memory.configuration.getTextBaseAddress(),
                Memory.configuration.getExternBaseAddress(),
                Memory.configuration.getGlobalPointer(),
                Memory.configuration.getDataBaseAddress(),
                Memory.configuration.getHeapBaseAddress(),
                Memory.configuration.getMemoryMapBaseAddress(),
        };


        // Must agree with above in number and order...
        String[] descriptions = {" (text)", " (global data)", " (gp)", " (static data)", " (heap)", " (memory map)"};
        displayBaseAddresses = displayBaseAddressArray;
        displayBaseAddressChoices = new String[displayBaseAddressArray.length];
        for (int i = 0; i < displayBaseAddressChoices.length; i++) {
            displayBaseAddressChoices[i] = rars.util.Binary.intToHexString(displayBaseAddressArray[i]) + descriptions[i];
        }
        defaultBaseAddressIndex = 3;  // default to 0x10010000 (static data)
        baseAddress = displayBaseAddressArray[defaultBaseAddressIndex];
    }

    // update based on combo box selection (currently not editable but that may change).
    private void updateBaseAddress() {
        baseAddress = displayBaseAddresses[displayBaseAddressSelector.getSelectedIndex()];
          /*  If you want to extend this app to allow user to edit combo box, you can always
              parse the getSelectedItem() value, because the pre-defined items are all formatted
      		 such that the first 10 characters contain the integer's hex value.  And if the
      		 value is user-entered, the numeric part cannot exceed 10 characters for a 32-bit
      		 address anyway.  So if the value is > 10 characters long, slice off the first
      		 10 and apply Integer.parseInt() to it to get custom base address.
      	*/
    }

    // Returns Dimension object with current width and height of display area as determined
    // by current settings of respective combo boxes.
    private Dimension getDisplayAreaDimension() {
        return new Dimension(visualizationAreaWidthInPixels, visualizationAreaHeightInPixels);
    }

    // reset all counters in the Grid.
    private void resetCounts() {
        theGrid.reset();
    }

    // Will return int equivalent of specified combo box's current selection.
    // The selection must be a String that parses to an int.
    private int getIntComboBoxSelection(JComboBox<String> comboBox) {
        try {
            return Integer.parseInt((String) comboBox.getSelectedItem());
        } catch (NumberFormatException nfe) {
            // Can occur only if initialization list contains badly formatted numbers.  This
            // is a developer's error, not a user error, and better be caught before release.
            return 1;
        }
    }

    // Use this for consistent results.
    private JPanel getPanelWithBorderLayout() {
        return new JPanel(new BorderLayout(2, 2));
    }

    // Method to determine grid dimensions based on durrent control settings.
    // Each grid element corresponds to one visualization unit.
    private Grid createNewGrid() {
        int rows = visualizationAreaHeightInPixels / unitPixelHeight;
        int columns = visualizationAreaWidthInPixels / unitPixelWidth;
        return new Grid(rows, columns);
    }

    // Given memory address, increment the counter for the corresponding grid element.
    // Need to consider words per unit (number of memory words that each visual element represents).
    // If address maps to invalid grid element (e.g. is outside the current bounds based on all
    // display settings) then nothing happens.
    private void incrementReferenceCountForAddress(int address) {
        int offset = (address - baseAddress) / Memory.WORD_LENGTH_BYTES / wordsPerUnit;
        // If you care to do anything with it, the following will return -1 if the address
        // maps outside the dimensions of the grid (e.g. below the base address or beyond end).
        theGrid.incrementElement(offset / theGrid.getColumns(), offset % theGrid.getColumns());
    }


    //////////////////////////////////////////////////////////////////////////////////////
    //  Specialized inner classes for modeling and animation.
    //////////////////////////////////////////////////////////////////////////////////////


    /////////////////////////////////////////////////////////////////////////////
    //  Class that represents the panel for visualizing and animating memory reference
    //  patterns.
    private class GraphicsPanel extends JPanel {
        // override default paint method to assure visualized reference pattern is produced every time
        // the panel is repainted.
        public void paint(Graphics g) {
            paintGrid(g, theGrid);
            if (drawHashMarksSelector.isSelected()) {
                paintHashMarks(g, theGrid);
            }
        }

        // Paint (ash marks on the grid.  Their color is chosef to be in
        // "contrast" to the current color for reference count of zero.
        private void paintHashMarks(Graphics g, Grid grid) {
            g.setColor(getContrastingColor(counterColorScale.getColor(0)));
            int leftX = 0;
            int rightX = visualizationAreaWidthInPixels;
            int upperY = 0;
            int lowerY = visualizationAreaHeightInPixels;
            // draw vertical hash marks
            for (int j = 0; j < grid.getColumns(); j++) {
                g.drawLine(leftX, upperY, leftX, lowerY);
                leftX += unitPixelWidth;   // faster than multiplying
            }
            leftX = 0;
            // draw horizontal hash marks
            for (int i = 0; i < grid.getRows(); i++) {
                g.drawLine(leftX, upperY, rightX, upperY);
                upperY += unitPixelHeight;   // faster than multiplying
            }
        }

        // Paint the color codes for reference counts.
        private void paintGrid(Graphics g, Grid grid) {
            int upperLeftX = 0, upperLeftY = 0;
            for (int i = 0; i < grid.getRows(); i++) {
                for (int j = 0; j < grid.getColumns(); j++) {
                    g.setColor(counterColorScale.getColor(grid.getElementFast(i, j)));
                    g.fillRect(upperLeftX, upperLeftY, unitPixelWidth, unitPixelHeight);
                    upperLeftX += unitPixelWidth;   // faster than multiplying
                }
                // get ready for next row...
                upperLeftX = 0;
                upperLeftY += unitPixelHeight;     // faster than multiplying
            }
        }

        private Color getContrastingColor(Color color) {
         /* Usual and quick method is to XOR with 0xFFFFFF. Here's a better but slower 
            algorithm from www.codeproject.com/tips/JbColorContrast.asp :
         	If all 3 color components are "close" to 0x80 (midpoint - choose your tolerance),
         	you can get better contrast by adding 0x7F7F7F then ANDing with 0xFFFFFF.
         */
            return new Color(color.getRGB() ^ 0xFFFFFF);
        }
    }


    /////////////////////////////////////////////////////////////////////
    // Class that simply defines UI controls for use with slider to view and/or
    // change the color associated with each memory reference count value.

    private class ColorChooserControls {
        private JLabel sliderLabel = null;
        private JSlider colorRangeSlider = null;
        private JButton currentColorButton;
        private JPanel colorChooserRow;
        private JPanel countDisplayRow;
        private volatile int counterIndex;

        private ColorChooserControls() {
            colorRangeSlider = new JSlider(JSlider.HORIZONTAL, 0, countTable.length - 1, COUNT_INDEX_INIT);
            colorRangeSlider.setToolTipText("View or change color associated with each reference count value");
            colorRangeSlider.setPaintTicks(false);
            colorRangeSlider.addChangeListener(new ColorChooserListener());
            counterIndex = COUNT_INDEX_INIT;
            sliderLabel = new JLabel(setLabel(countTable[counterIndex]));
            sliderLabel.setToolTipText("Reference count values listed on non-linear scale of " +
                    countTable[0] + " to " + countTable[countTable.length - 1]);
            sliderLabel.setHorizontalAlignment(JLabel.CENTER);
            sliderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            currentColorButton = new JButton("   ");
            currentColorButton.setToolTipText("Click here to change color for the reference count subrange based at current value");
            currentColorButton.setBackground(counterColorScale.getColor(countTable[counterIndex]));
            currentColorButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            int counterValue = countTable[counterIndex];
                            int highEnd = counterColorScale.getHighEndOfRange(counterValue);
                            String dialogLabel = "Select color for reference count " +
                                    ((counterValue == highEnd)
                                            ? "value " + counterValue
                                            : "range " + counterValue + "-" + highEnd);
                            Color newColor = JColorChooser.showDialog(theWindow, dialogLabel, counterColorScale.getColor(counterValue));
                            if (newColor != null && !newColor.equals(counterColorScale.getColor(counterValue))) {
                                counterColorScale.insertOrReplace(new CounterColor(counterValue, newColor));
                                currentColorButton.setBackground(newColor);
                                updateDisplay();
                            }
                        }
                    });
            colorChooserRow = new JPanel();
            countDisplayRow = new JPanel();
            colorChooserRow.add(colorRangeSlider);
            colorChooserRow.add(currentColorButton);
            countDisplayRow.add(sliderLabel);
        }

        // set label wording depending on current speed setting
        private String setLabel(int value) {
            String spaces = "  ";
            if (value >= 10) {
                spaces = " ";
            } else if (value >= 100) {
                spaces = "";
            }
            return "Counter value " + spaces + value;
        }

        // Listener that both revises label as user slides and updates current index when sliding stops.
        private class ColorChooserListener implements ChangeListener {
            public void stateChanged(ChangeEvent e) {
                JSlider source = (JSlider) e.getSource();
                if (!source.getValueIsAdjusting()) {
                    counterIndex = source.getValue();
                } else {
                    int count = countTable[source.getValue()];
                    sliderLabel.setText(setLabel(count));
                    currentColorButton.setBackground(counterColorScale.getColor(count));
                }
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////
    // Object that represents mapping from counter value to color it is displayed as.
    //
    private class CounterColorScale {
        CounterColor[] counterColors;

        CounterColorScale(CounterColor[] colors) {
            counterColors = colors;
        }

        // return color associated with specified counter value
        private Color getColor(int count) {
            Color result = counterColors[0].associatedColor;
            int index = 0;
            while (index < counterColors.length && count >= counterColors[index].colorRangeStart) {
                result = counterColors[index].associatedColor;
                index++;
            }
            return result;
        }

        // For a given counter value, return the counter value at the high end of the range of
        // counter values having the same color.
        private int getHighEndOfRange(int count) {
            int highEnd = Integer.MAX_VALUE;
            if (count < counterColors[counterColors.length - 1].colorRangeStart) {
                int index = 0;
                while (index < counterColors.length - 1 && count >= counterColors[index].colorRangeStart) {
                    highEnd = counterColors[index + 1].colorRangeStart - 1;
                    index++;
                }
            }
            return highEnd;
        }

        // The given entry should either be inserted into the the scale or replace an existing
        // element.  The latter occurs if the new CounterColor has same starting counter value
        // as an existing one.
        private void insertOrReplace(CounterColor newColor) {
            int index = Arrays.binarySearch(counterColors, newColor);
            if (index >= 0) { // found, so replace
                counterColors[index] = newColor;
            } else { // not found, so insert
                int insertIndex = -index - 1;
                CounterColor[] newSortedArray = new CounterColor[counterColors.length + 1];
                System.arraycopy(counterColors, 0, newSortedArray, 0, insertIndex);
                System.arraycopy(counterColors, insertIndex, newSortedArray, insertIndex + 1, counterColors.length - insertIndex);
                newSortedArray[insertIndex] = newColor;
                counterColors = newSortedArray;
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////
    // Each object represents beginning of a counter value range (non-negative integer) and
    // color for rendering the range.  High end of the range is defined as low end of the
    // next range minus 1.  For last range, high end is Integer.MAX_VALUE.
    private class CounterColor implements Comparable<CounterColor> {
        private int colorRangeStart;
        private Color associatedColor;

        public CounterColor(int start, Color color) {
            this.colorRangeStart = start;
            this.associatedColor = color;
        }

        // Necessary for sorting in ascending order of range low end.
        public int compareTo(CounterColor other) {
            return this.colorRangeStart - other.colorRangeStart;
        }
    }


    ////////////////////////////////////////////////////////////////////////
    // Represents grid of memory access counts
    private class Grid {

        int[][] grid;
        int rows, columns;

        private Grid(int rows, int columns) {
            grid = new int[rows][columns];
            this.rows = rows;
            this.columns = columns;
            // automatically initialized to 0, so I won't bother to....
        }

        private int getRows() {
            return rows;
        }

        private int getColumns() {
            return columns;
        }

        // Returns value in given grid element; -1 if row or column is out of range.
        private int getElement(int row, int column) {
            return (row >= 0 && row <= rows && column >= 0 && column <= columns) ? grid[row][column] : -1;
        }

        // Returns value in given grid element without doing any row/column index checking.
        // Is faster than getElement but will throw array index out of bounds exception if
        // parameter values are outside the bounds of the grid.
        private int getElementFast(int row, int column) {
            return grid[row][column];
        }

        // Increment the given grid element and return incremented value.
        // Returns -1 if row or column is out of range.
        private int incrementElement(int row, int column) {
            return (row >= 0 && row <= rows && column >= 0 && column <= columns) ? ++grid[row][column] : -1;
        }

        // Just set all grid elements to 0.
        private void reset() {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    grid[i][j] = 0;
                }
            }
        }
    }

}