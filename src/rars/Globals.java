package rars;

import rars.assembler.SymbolTable;
import rars.riscv.hardware.Memory;
import rars.riscv.hardware.ReservationTables;
import rars.riscv.InstructionSet;
import rars.riscv.SyscallNumberOverride;
import rars.util.PropertiesFile;
import rars.venus.VenusUI;
import rars.venus.GeneralVenusUI;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.locks.ReentrantLock;

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
 * Collection of globally-available data structures.
 *
 * @author Pete Sanderson
 * @version August 2003
 */
public class Globals {
    // List these first because they are referenced by methods called at initialization.
    private static String configPropertiesFile = "Config";
    private static String syscallPropertiesFile = "Syscall";

    /**
     * The set of implemented instructions.
     **/
    public static InstructionSet instructionSet;
    /**
     * the program currently being worked with.  Used by GUI only, not command line.
     **/
    public static RISCVprogram program;

    public static ArrayList<RISCVprogram> gPrograms;
    /**
     * Symbol table for file currently being assembled.
     **/
    public static SymbolTable symbolTable;
    /**
     * Simulated memory component.
     **/
    public static Memory memory;
    /**
     * Simulated reservation tables component.
     **/
    public static ReservationTables reservationTables;
    /**
     * Lock variable used at head of synchronized block to guard memory and registers
     **/
    public static final ReentrantLock memoryAndRegistersLock = new ReentrantLock();
    /**
     * Flag to determine whether or not to produce internal debugging information.
     **/
    public static boolean debug = false;
    /**
     * Object that contains various settings that can be accessed modified internally.
     **/
    static Settings settings;
    /**
     * String to GUI's RunI/O text area when echoing user input from pop-up dialog.
     */
    public static String userInputAlert = "**** user input : ";
    /**
     * Path to folder that contains images
     */
    // The leading "/" in filepath prevents package name from being pre-pended.
    public static final String imagesPath = "/images/";
    /**
     * Path to folder that contains help text
     */
    public static final String helpPath = "/help/";
    /* Flag that indicates whether or not instructionSet has been initialized. */
    private static boolean initialized = false;
    /* The GUI being used (if any) with this simulator. */
    static VenusUI gui = null;
    /**
     * The current version number. Can't wait for "initialize()" call to get it.
     */
    public static final String version = "1.5";
    /**
     * List of accepted file extensions for RISCV assembly source files.
     */
    public static final ArrayList<String> fileExtensions = getFileExtensions();
    /**
     * Maximum length of scrolled message window (RARS Messages and Run I/O)
     */
    public static final int maximumMessageCharacters = getMessageLimit();
    /**
     * Maximum number of assembler errors produced by one assemble operation
     */
    public static final int maximumErrorMessages = getErrorLimit();
    /**
     * Maximum number of back-step operations to buffer
     */
    public static final int maximumBacksteps = getBackstepLimit();
    /**
     * Copyright years
     */
    public static final String copyrightYears = getCopyrightYears();
    /**
     * Copyright holders
     */
    public static final String copyrightHolders = getCopyrightHolders();
    /**
     * Placeholder for non-printable ASCII codes
     */
    public static final String ASCII_NON_PRINT = getAsciiNonPrint();
    /**
     * Array of strings to display for ASCII codes in ASCII display of data segment. ASCII code 0-255 is array index.
     */
    public static final String[] ASCII_TABLE = getAsciiStrings();
    /**
     * Exit code -- useful with SYSCALL 17 when running from command line (not GUI)
     */
    public static int exitCode = 0;

    public static boolean runSpeedPanelExists = false;

    private static int harts = 1;

    private static ArrayList<GeneralVenusUI> hartWindows =  new ArrayList<>();

    public static void setHartWindows(){
        for (int i = 1; i < Globals.getHarts(); i++) {
            GeneralVenusUI temp= new GeneralVenusUI(String.format("Hart %d", i));
            hartWindows.add(temp);
        }
    }

    public static ArrayList<GeneralVenusUI> getHartWindows(){
        return hartWindows;
    }

    public static int getHarts() {
        return harts;
    }

    public static void setHarts(int i) {
        if ((harts == 1 && i < 0) || (harts == 7 && i > 0)) {
            return;
        } if (i > 0) {
            reservationTables = new ReservationTables(++harts);
        } else {
            reservationTables = new ReservationTables(--harts);
        }
    }

    private static String getCopyrightYears() {
        return "2003-2019";
    }

    private static String getCopyrightHolders() {
        return "Pete Sanderson and Kenneth Vollmar";
    }

    public static void setGui(VenusUI g) {
        gui = g;
    }

    public static VenusUI getGui() {
        return gui;
    }

    public static Settings getSettings() {
        return settings;
    }

    /**
     * Method called once upon system initialization to create the global data structures.
     **/

    public static void initialize(boolean gui) {
        if (!initialized) {
            memory = Memory.getInstance(); //clients can use Memory.getInstance instead of Globals.memory
            reservationTables = new ReservationTables(harts);
            symbolTable = new SymbolTable("global");
            settings = new Settings(gui);
            instructionSet = new InstructionSet();
            instructionSet.populate();
            initialized = true;
            debug = false;
            memory.clear(); // will establish memory configuration from setting
        }
    }

    // Read byte limit of Run I/O or RARS Messages text to buffer.
    private static int getMessageLimit() {
        return getIntegerProperty(configPropertiesFile, "MessageLimit", 1000000);
    }

    // Read limit on number of error messages produced by one assemble operation.
    private static int getErrorLimit() {
        return getIntegerProperty(configPropertiesFile, "ErrorLimit", 200);
    }

    // Read backstep limit (number of operations to buffer) from properties file.
    private static int getBackstepLimit() {
        return getIntegerProperty(configPropertiesFile, "BackstepLimit", 1000);
    }

    // Read ASCII default display character for non-printing characters, from properties file.
    public static String getAsciiNonPrint() {
        String anp = getPropertyEntry(configPropertiesFile, "AsciiNonPrint");
        return (anp == null) ? "." : ((anp.equals("space")) ? " " : anp);
    }

    // Read ASCII strings for codes 0-255, from properties file. If string
    // value is "null", substitute value of ASCII_NON_PRINT.  If string is
    // "space", substitute string containing one space character.
    public static String[] getAsciiStrings() {
        String let = getPropertyEntry(configPropertiesFile, "AsciiTable");
        String placeHolder = getAsciiNonPrint();
        if(let == null){
            // If config isn't loaded, give a decent default value.
            String[] table = new String[((int) '~' )+ 1];
            for (int i = 0; i < table.length; i++) {
                if (i == 0) table[i] = "\0";
                else if (i == '\n') table[i] = "\n";
                else if (i < ' ') table[i] = placeHolder;
                else table[i] = " "+(char) i;
            }
            return table;
        }else {
            String[] lets = let.split(" +");
            int maxLength = 0;
            for (int i = 0; i < lets.length; i++) {
                if (lets[i].equals("null")) lets[i] = placeHolder;
                if (lets[i].equals("space")) lets[i] = " ";
                if (lets[i].length() > maxLength) maxLength = lets[i].length();
            }
            String padding = "        ";
            maxLength++;
            for (int i = 0; i < lets.length; i++) {
                lets[i] = padding.substring(0, maxLength - lets[i].length()) + lets[i];
            }
            return lets;
        }
    }

    // Read and return integer property value for given file and property name.
    // Default value is returned if property file or name not found.
    private static int getIntegerProperty(String propertiesFile, String propertyName, int defaultValue) {
        int limit = defaultValue;  // just in case no entry is found
        Properties properties = PropertiesFile.loadPropertiesFromFile(propertiesFile);
        try {
            limit = Integer.parseInt(properties.getProperty(propertyName, Integer.toString(defaultValue)));
        } catch (NumberFormatException nfe) {
        } // do nothing, I already have a default
        return limit;
    }


    // Read assembly language file extensions from properties file.  Resulting
    // string is tokenized into array list (assume StringTokenizer default delimiters).
    private static ArrayList<String> getFileExtensions() {
        ArrayList<String> extensionsList = new ArrayList<>();
        String extensions = getPropertyEntry(configPropertiesFile, "Extensions");
        if (extensions != null) {
            StringTokenizer st = new StringTokenizer(extensions);
            while (st.hasMoreTokens()) {
                extensionsList.add(st.nextToken());
            }
        }
        return extensionsList;
    }

    /**
     * Read and return property file value (if any) for requested property.
     *
     * @param propertiesFile name of properties file (do NOT include filename extension,
     *                       which is assumed to be ".properties")
     * @param propertyName   String containing desired property name
     * @return String containing associated value; null if property not found
     */
    public static String getPropertyEntry(String propertiesFile, String propertyName) {
        return PropertiesFile.loadPropertiesFromFile(propertiesFile).getProperty(propertyName);
    }

    /**
     * Read any syscall number assignment overrides from config file.
     *
     * @return ArrayList of SyscallNumberOverride objects
     */
    public ArrayList<SyscallNumberOverride> getSyscallOverrides() {
        ArrayList<SyscallNumberOverride> overrides = new ArrayList<>();
        Properties properties = PropertiesFile.loadPropertiesFromFile(syscallPropertiesFile);
        Enumeration keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            overrides.add(new SyscallNumberOverride(key, properties.getProperty(key)));
        }
        return overrides;
    }

}
