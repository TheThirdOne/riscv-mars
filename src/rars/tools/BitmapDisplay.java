package rars.tools;

import rars.riscv.hardware.AccessNotice;
import rars.riscv.hardware.Memory;
import rars.riscv.hardware.MemoryAccessNotice;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Observable;

/*
Copyright (c) 2010-2011,  Pete Sanderson and Kenneth Vollmar

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
 * Bitmapp display simulator.  It can be run either as a stand-alone Java application having
 * access to the rars package, or through RARS as an item in its Tools menu.  It makes
 * maximum use of methods inherited from its abstract superclass AbstractToolAndApplication.
 * Pete Sanderson, verison 1.0, 23 December 2010.
 */
public class BitmapDisplay extends AbstractToolAndApplication {

    private static String version = "Version 1.0";
    private static String heading = "Bitmap Display";

    // Major GUI components
    private JComboBox<String> visualizationUnitPixelWidthSelector, visualizationUnitPixelHeightSelector,
            visualizationPixelWidthSelector, visualizationPixelHeightSelector, displayBaseAddressSelector;
    private Graphics drawingArea;
    private JPanel canvas;
    private JPanel results;

    // Some GUI settings
    private EmptyBorder emptyBorder = new EmptyBorder(4, 4, 4, 4);
    private Font countFonts = new Font("Times", Font.BOLD, 12);
    private Color backgroundColor = Color.WHITE;

    // Values for Combo Boxes

    private static final String[] visualizationUnitPixelWidthChoices = {"1", "2", "4", "8", "16", "32"};
    private static final int defaultVisualizationUnitPixelWidthIndex = 0;
    private static final String[] visualizationUnitPixelHeightChoices = {"1", "2", "4", "8", "16", "32"};
    private static final int defaultVisualizationUnitPixelHeightIndex = 0;
    private static final String[] displayAreaPixelWidthChoices = {"64", "128", "256", "512", "1024"};
    private static final int defaultDisplayWidthIndex = 3;
    private static final String[] displayAreaPixelHeightChoices = {"64", "128", "256", "512", "1024"};
    private static final int defaultDisplayHeightIndex = 2;

    // Values for display canvas.  Note their initialization uses the identifiers just above.

    private int unitPixelWidth = Integer.parseInt(visualizationUnitPixelWidthChoices[defaultVisualizationUnitPixelWidthIndex]);
    private int unitPixelHeight = Integer.parseInt(visualizationUnitPixelHeightChoices[defaultVisualizationUnitPixelHeightIndex]);
    private int displayAreaWidthInPixels = Integer.parseInt(displayAreaPixelWidthChoices[defaultDisplayWidthIndex]);
    private int displayAreaHeightInPixels = Integer.parseInt(displayAreaPixelHeightChoices[defaultDisplayHeightIndex]);


    // The next four are initialized dynamically in initializeDisplayBaseChoices()
    private String[] displayBaseAddressChoices;
    private int[] displayBaseAddresses;
    private int defaultBaseAddressIndex;
    private int baseAddress;

    private Grid theGrid;

    /**
     * Simple constructor, likely used to run a stand-alone bitmap display tool.
     *
     * @param title   String containing title for title bar
     * @param heading String containing text for heading shown in upper part of window.
     */
    public BitmapDisplay(String title, String heading) {
        super(title, heading);
    }

    /**
     * Simple constructor, likely used by the RARS Tools menu mechanism
     */
    public BitmapDisplay() {
        super("Bitmap Display, " + version, heading);
    }


    /**
     * Main provided for pure stand-alone use.  Recommended stand-alone use is to write a
     * driver program that instantiates a Bitmap object then invokes its go() method.
     * "stand-alone" means it is not invoked from the RARS Tools menu.  "Pure" means there
     * is no driver program to invoke the application.
     */
    public static void main(String[] args) {
        new BitmapDisplay("Bitmap Display stand-alone, " + version, heading).go();
    }

    @Override
    public String getName() {
        return "Bitmap Display";
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
        int highAddress = baseAddress + theGrid.getRows() * theGrid.getColumns() * Memory.WORD_LENGTH_BYTES;
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

    /**
     * Update display when the connected program accesses (data) memory.
     *
     * @param memory       the attached memory
     * @param accessNotice information provided by memory in MemoryAccessNotice object
     */
    protected void processRISCVUpdate(Observable memory, AccessNotice accessNotice) {
        if (accessNotice.getAccessType() == AccessNotice.WRITE) {
            updateColorForAddress((MemoryAccessNotice) accessNotice);
        }
    }


    /**
     * Initialize all JComboBox choice structures not already initialized at declaration.
     * Overrides inherited method that does nothing.
     */
    protected void initializePreGUI() {
        initializeDisplayBaseChoices();
        // NOTE: Can't call "createNewGrid()" here because it uses settings from
        //       several combo boxes that have not been created yet.  But a default grid
        //       needs to be allocated for initial canvas display.
        theGrid = new Grid(displayAreaHeightInPixels / unitPixelHeight,
                displayAreaWidthInPixels / unitPixelWidth);
    }


    /**
     * The only post-GUI initialization is to create the initial Grid object based on the default settings
     * of the various combo boxes. Overrides inherited method that does nothing.
     */

    protected void initializePostGUI() {
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

    /**
     * Updates display immediately after each update (AccessNotice) is processed, after
     * display configuration changes as needed, and after each execution step when Rars
     * is running in timed mode.  Overrides inherited method that does nothing.
     */
    protected void updateDisplay() {
        canvas.repaint();
    }


    /**
     * Overrides default method, to provide a Help button for this tool/app.
     */
    protected JComponent getHelpComponent() {
        final String helpContent =
                "Use this program to simulate a basic bitmap display where\n" +
                        "each memory word in a specified address space corresponds to\n" +
                        "one display pixel in row-major order starting at the upper left\n" +
                        "corner of the display.  This tool may be run either from the\n" +
                        "Tools menu or as a stand-alone application.\n" +
                        "\n" +
                        "You can easily learn to use this small program by playing with\n" +
                        "it!   Each rectangular unit on the display represents one memory\n" +
                        "word in a contiguous address space starting with the specified\n" +
                        "base address.  The value stored in that word will be interpreted\n" +
                        "as a 24-bit RGB color value with the red component in bits 16-23,\n" +
                        "the green component in bits 8-15, and the blue component in bits 0-7.\n" +
                        "Each time a memory word within the display address space is written\n" +
                        "by the program, its position in the display will be rendered in the\n" +
                        "color that its value represents.\n" +
                        "\n" +
                        "Version 1.0 is very basic and was constructed from the Memory\n" +
                        "Reference Visualization tool's code.  Feel free to improve it and\n" +
                        "send your code for consideration in the next release.\n" +
                        "\n";
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
        JPanel organization = new JPanel(new GridLayout(8, 1));

        visualizationUnitPixelWidthSelector = new JComboBox<>(visualizationUnitPixelWidthChoices);
        visualizationUnitPixelWidthSelector.setEditable(false);
        visualizationUnitPixelWidthSelector.setBackground(backgroundColor);
        visualizationUnitPixelWidthSelector.setSelectedIndex(defaultVisualizationUnitPixelWidthIndex);
        visualizationUnitPixelWidthSelector.setToolTipText("Width in pixels of rectangle representing memory word");
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
        visualizationUnitPixelHeightSelector.setToolTipText("Height in pixels of rectangle representing memory word");
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
        visualizationPixelWidthSelector.setToolTipText("Total width in pixels of display area");
        visualizationPixelWidthSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        displayAreaWidthInPixels = getIntComboBoxSelection(visualizationPixelWidthSelector);
                        canvas.setPreferredSize(getDisplayAreaDimension());
                        canvas.setSize(getDisplayAreaDimension());
                        theGrid = createNewGrid();
                        updateDisplay();
                    }
                });
        visualizationPixelHeightSelector = new JComboBox<>(displayAreaPixelHeightChoices);
        visualizationPixelHeightSelector.setEditable(false);
        visualizationPixelHeightSelector.setBackground(backgroundColor);
        visualizationPixelHeightSelector.setSelectedIndex(defaultDisplayHeightIndex);
        visualizationPixelHeightSelector.setToolTipText("Total height in pixels of display area");
        visualizationPixelHeightSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        displayAreaHeightInPixels = getIntComboBoxSelection(visualizationPixelHeightSelector);
                        canvas.setPreferredSize(getDisplayAreaDimension());
                        canvas.setSize(getDisplayAreaDimension());
                        theGrid = createNewGrid();
                        updateDisplay();
                    }
                });
        displayBaseAddressSelector = new JComboBox<>(displayBaseAddressChoices);
        displayBaseAddressSelector.setEditable(false);
        displayBaseAddressSelector.setBackground(backgroundColor);
        displayBaseAddressSelector.setSelectedIndex(defaultBaseAddressIndex);
        displayBaseAddressSelector.setToolTipText("Base address for display area (upper left corner)");
        displayBaseAddressSelector.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        // This may also affect what address range we should be registered as an Observer
                        // for.  The default (inherited) address range is the static data segment
                        // starting at 0x10010000. To change this requires override of
                        // AbstractToolAndApplication.addAsObserver().  The no-argument version of
                        // that method is called automatically  when "Connect" button is clicked for Tool
                        // and when "Assemble and Run" button is clicked for Rars application.
                        updateBaseAddress();
                        // If display base address is changed while connected to the program (this can only occur
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


        // Lay 'em out in the grid...
        organization.add(unitWidthInPixelsRow);
        organization.add(unitHeightInPixelsRow);
        organization.add(widthInPixelsRow);
        organization.add(heightInPixelsRow);
        organization.add(baseAddressRow);
        return organization;
    }

    // UI components and layout for right half of GUI, the visualization display area.
    private JComponent buildVisualizationArea() {
        canvas = new GraphicsPanel();
        canvas.setPreferredSize(getDisplayAreaDimension());
        canvas.setToolTipText("Bitmap display area");
        return canvas;
    }

    // For greatest flexibility, initialize the display base choices directly from
    // the constants defined in the Memory class.  This method called prior to
    // building the GUI.  Here are current values from Memory.java:
    //dataSegmentBaseAddress=0x10000000, globalPointer=0x10008000
    //dataBaseAddress=0x10010000, heapBaseAddress=0x10040000, memoryMapBaseAddress=0xffff0000
    private void initializeDisplayBaseChoices() {
        int[] displayBaseAddressArray = {
                Memory.configuration.getExternBaseAddress(),
                Memory.configuration.getGlobalPointer(),
                Memory.configuration.getDataBaseAddress(),
                Memory.configuration.getHeapBaseAddress(),
                Memory.configuration.getMemoryMapBaseAddress(),
        };
        // Must agree with above in number and order...
        String[] descriptions = {" (global data)", " (gp)", " (static data)", " (heap)", " (memory map)"};
        displayBaseAddresses = displayBaseAddressArray;
        displayBaseAddressChoices = new String[displayBaseAddressArray.length];
        for (int i = 0; i < displayBaseAddressChoices.length; i++) {
            displayBaseAddressChoices[i] = rars.util.Binary.intToHexString(displayBaseAddressArray[i]) + descriptions[i];
        }
        defaultBaseAddressIndex = 2;  // default to 0x10010000 (static data)
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
        return new Dimension(displayAreaWidthInPixels, displayAreaHeightInPixels);
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

    // Method to determine grid dimensions based on current control settings.
    // Each grid element corresponds to one visualization unit.
    private Grid createNewGrid() {
        int rows = displayAreaHeightInPixels / unitPixelHeight;
        int columns = displayAreaWidthInPixels / unitPixelWidth;
        return new Grid(rows, columns);
    }

    // Given memory address, update color for the corresponding grid element.
    private void updateColorForAddress(MemoryAccessNotice notice) {
        int address = notice.getAddress();
        int offset = (address - baseAddress) / Memory.WORD_LENGTH_BYTES;

        try {
            theGrid.setElement(offset / theGrid.getColumns(), offset % theGrid.getColumns(),
                    Memory.getInstance().getWord(address / Memory.WORD_LENGTH_BYTES * Memory.WORD_LENGTH_BYTES));
        } catch (Exception e) {
            // If address is out of range for display, do nothing.
        }
    }


    //////////////////////////////////////////////////////////////////////////////////////
    //  Specialized inner classes for modeling and animation.
    //////////////////////////////////////////////////////////////////////////////////////


    /////////////////////////////////////////////////////////////////////////////
    //  Class that represents the panel for visualizing and animating memory reference
    //  patterns.
    private class GraphicsPanel extends JPanel {

        // override default paint method to assure display updated correctly every time
        // the panel is repainted.
        public void paint(Graphics g) {
            paintGrid(g, theGrid);
        }

        // Paint the color codes.
        private void paintGrid(Graphics g, Grid grid) {
            int upperLeftX = 0, upperLeftY = 0;
            for (int i = 0; i < grid.getRows(); i++) {
                for (int j = 0; j < grid.getColumns(); j++) {
                    g.setColor(grid.getElementFast(i, j));
                    g.fillRect(upperLeftX, upperLeftY, unitPixelWidth, unitPixelHeight);
                    upperLeftX += unitPixelWidth;   // faster than multiplying
                }
                // get ready for next row...
                upperLeftX = 0;
                upperLeftY += unitPixelHeight;     // faster than multiplying
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////
    // Represents grid of colors
    private class Grid {

        Color[][] grid;
        int rows, columns;

        private Grid(int rows, int columns) {
            grid = new Color[rows][columns];
            this.rows = rows;
            this.columns = columns;
            reset();
        }

        private int getRows() {
            return rows;
        }

        private int getColumns() {
            return columns;
        }

        // Returns value in given grid element; null if row or column is out of range.
        private Color getElement(int row, int column) {
            return (row >= 0 && row <= rows && column >= 0 && column <= columns) ? grid[row][column] : null;
        }

        // Returns value in given grid element without doing any row/column index checking.
        // Is faster than getElement but will throw array index out of bounds exception if
        // parameter values are outside the bounds of the grid.
        private Color getElementFast(int row, int column) {
            return grid[row][column];
        }

        // Set the grid element.
        private void setElement(int row, int column, int color) {
            grid[row][column] = new Color(color);
        }

        // Set the grid element.
        private void setElement(int row, int column, Color color) {
            grid[row][column] = color;
        }

        // Just set all grid elements to black.
        private void reset() {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    grid[i][j] = Color.BLACK;
                }
            }
        }
    }

}