package rars;

import rars.api.Program;
import rars.riscv.InstructionSet;
import rars.riscv.dump.DumpFormat;
import rars.riscv.dump.DumpFormatLoader;
import rars.riscv.hardware.*;
import rars.simulator.ProgramArgumentList;
import rars.simulator.Simulator;
import rars.util.Binary;
import rars.util.FilenameFinder;
import rars.util.MemoryDump;
import rars.venus.VenusUI;
import rars.api.Options;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

/*
Copyright (c) 2003-2012,  Pete Sanderson and Kenneth Vollmar

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
 * Launch the application
 *
 * @author Pete Sanderson
 * @version December 2009
 **/

public class Launch {

    /**
     * Main takes a number of command line arguments.<br>
     * Usage:  rars  [options] filename<br>
     * Valid options (not case sensitive, separate by spaces) are:<br>
     * a  -- assemble only, do not simulate<br>
     * ad  -- both a and d<br>
     * ae<n>  -- terminate RARS with integer exit code <n> if an assemble error occurs.<br>
     * ascii  -- display memory or register contents interpreted as ASCII
     * b  -- brief - do not display register/memory address along with contents<br>
     * d  -- print debugging statements<br>
     * da  -- both a and d<br>
     * dec  -- display memory or register contents in decimal.<br>
     * dump  -- dump memory contents to file.  Option has 3 arguments, e.g. <br>
     * <tt>dump &lt;segment&gt; &lt;format&gt; &lt;file&gt;</tt>.  Also supports<br>
     * an address range (see <i>m-n</i> below).  Current supported <br>
     * segments are <tt>.text</tt> and <tt>.data</tt>.  Current supported dump formats <br>
     * are <tt>Binary</tt>, <tt>HexText</tt>, <tt>BinaryText</tt>.<br>
     * h  -- display help.  Use by itself and with no filename</br>
     * harts <n> -- the amount of harts to use for running RARS with<br>
     * hex  -- display memory or register contents in hexadecimal (default)<br>
     * ic  -- display count of basic instructions 'executed'");
     * mc  -- set memory configuration.  Option has 1 argument, e.g.<br>
     * <tt>mc &lt;config$gt;</tt>, where &lt;config$gt; is <tt>Default</tt><br>
     * for the RARS default 32-bit address space, <tt>CompactDataAtZero</tt> for<br>
     * a 32KB address space with data segment at address 0, or <tt>CompactTextAtZero</tt><br>
     * for a 32KB address space with text segment at address 0.<br>
     * me  -- display RARS messages to standard err instead of standard out. Can separate via redirection.</br>
     * nc  -- do not display copyright notice (for cleaner redirected/piped output).</br>
     * np  -- No Pseudo-instructions allowed ("ne" will work also).<br>
     * p  -- Project mode - assemble all files in the same directory as given file.<br>
     * se<n>  -- terminate RARS with integer exit code <n> if a simulation (run) error occurs.<br>
     * sm  -- Start execution at Main - Execution will start at program statement globally labeled main.<br>
     * smc  -- Self Modifying Code - Program can write and branch to either text or data segment<br>
     * we  -- assembler Warnings will be considered Errors<br>
     * <n>  -- where <n> is an integer maximum count of steps to simulate.<br>
     * If 0, negative or not specified, there is no maximum.<br>
     * $<reg>  -- where <reg> is number or name (e.g. 5, t3, f10) of register whose <br>
     * content to display at end of run.  Option may be repeated.<br>
     * <reg_name>  -- where <reg_name> is name (e.g. t3, f10) of register whose <br>
     * content to display at end of run.  Option may be repeated. $ not required.<br>
     * <m>-<n>  -- memory address range from <m> to <n> whose contents to<br>
     * display at end of run. <m> and <n> may be hex or decimal,<br>
     * <m> <= <n>, both must be on word boundary.  Option may be repeated.<br>
     * pa  -- Program Arguments follow in a space-separated list.  This<br>
     * option must be placed AFTER ALL FILE NAMES, because everything<br>
     * that follows it is interpreted as a program argument to be<br>
     * made available to the program at runtime.<br>
     **/

    private Options options;
    private boolean simulate;
    private boolean rv64;
    private int displayFormat;
    private boolean verbose;  // display register name or address along with contents
    private boolean assembleProject; // assemble only the given file or all files in its directory
    private boolean countInstructions; // Whether to count and report number of instructions executed
    private static final String rangeSeparator = "-";
    private static final int memoryWordsPerLine = 4; // display 4 memory words, tab separated, per line
    private static final int DECIMAL = 0; // memory and register display format
    private static final int HEXADECIMAL = 1;// memory and register display format
    private static final int ASCII = 2;// memory and register display format
    private ArrayList<String> registerDisplayList;
    private ArrayList<String> memoryDisplayList;
    private ArrayList<String> filenameList;
    private int instructionCount;
    private PrintStream out; // stream for display of command line output
    private ArrayList<String[]> dumpTriples = null; // each element holds 3 arguments for dump option
    private ArrayList<String> programArgumentList; // optional program args for program (becomes argc, argv)
    private int assembleErrorExitCode;  // RARS command exit code to return if assemble error occurs
    private int simulateErrorExitCode;// RARS command exit code to return if simulation error occurs

    public static void main(String[] args){
        new Launch(args);
    }
    private Launch(String[] args) {
        boolean gui = (args.length == 0);
        Globals.initialize(gui);
        if (gui) {
            launchIDE();
        } else { // running from command line.
            // assure command mode works in headless environment (generates exception if not)
            System.setProperty("java.awt.headless", "true");
            options = new Options();
            simulate = true;
            displayFormat = HEXADECIMAL;
            verbose = true;
            assembleProject = false;
            countInstructions = false;
            instructionCount = 0;
            assembleErrorExitCode = 0;
            simulateErrorExitCode = 0;
            registerDisplayList = new ArrayList<>();
            memoryDisplayList = new ArrayList<>();
            filenameList = new ArrayList<>();
            MemoryConfigurations.setCurrentConfiguration(MemoryConfigurations.getDefaultConfiguration());
            out = System.out;
            if (parseCommandArgs(args)) {
                dumpSegments(runCommand());
            }
            System.exit(Globals.exitCode);
        }
    }

    private void displayAllPostMortem(Program program) {
        displayMiscellaneousPostMortem(program);
        displayRegistersPostMortem(program);
        displayMemoryPostMortem(program.getMemory());
    }
    /////////////////////////////////////////////////////////////
    // Perform any specified dump operations.  See "dump" option.
    //

    private void dumpSegments(Program program) {
        if (dumpTriples == null || program == null)
            return;

        for (String[] triple : dumpTriples) {
            File file = new File(triple[2]);
            Integer[] segInfo = MemoryDump.getSegmentBounds(triple[0]);
            // If not segment name, see if it is address range instead.  DPS 14-July-2008
            if (segInfo == null) {
                try {
                    String[] memoryRange = checkMemoryAddressRange(triple[0]);
                    segInfo = new Integer[2];
                    segInfo[0] = Binary.stringToInt(memoryRange[0]); // low end of range
                    segInfo[1] = Binary.stringToInt(memoryRange[1]); // high end of range
                } catch (NumberFormatException nfe) {
                    segInfo = null;
                } catch (NullPointerException npe) {
                    segInfo = null;
                }
            }
            if (segInfo == null) {
                out.println("Error while attempting to save dump, segment/address-range " + triple[0] + " is invalid!");
                continue;
            }
            DumpFormat format = DumpFormatLoader.findDumpFormatGivenCommandDescriptor(triple[1]);
            if (format == null) {
                out.println("Error while attempting to save dump, format " + triple[1] + " was not found!");
                continue;
            }
            try {
                int highAddress = program.getMemory().getAddressOfFirstNull(segInfo[0], segInfo[1]) - Memory.WORD_LENGTH_BYTES;
                if (highAddress < segInfo[0]) {
                    out.println("This segment has not been written to, there is nothing to dump.");
                    continue;
                }
                format.dumpMemoryRange(file, segInfo[0], highAddress, program.getMemory());
            } catch (FileNotFoundException e) {
                out.println("Error while attempting to save dump, file " + file + " was not found!");
            } catch (AddressErrorException e) {
                out.println("Error while attempting to save dump, file " + file + "!  Could not access address: " + e.getAddress() + "!");
            } catch (IOException e) {
                out.println("Error while attempting to save dump, file " + file + "!  Disk IO failed!");
            }
        }
    }


    /////////////////////////////////////////////////////////////////
    // There are no command arguments, so run in interactive mode by
    // launching the GUI-fronted integrated development environment.

    private void launchIDE() {
        // System.setProperty("apple.laf.useScreenMenuBar", "true"); // Puts RARS menu on Mac OS menu bar
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() {
                        //Turn off metal's use of bold fonts
                        //UIManager.put("swing.boldMetal", Boolean.FALSE);
                        new VenusUI("RARS " + Globals.version);
                    }
                });
    }


    //////////////////////////////////////////////////////////////////////
    // Parse command line arguments.  The initial parsing has already been
    // done, since each space-separated argument is already in a String array
    // element.  Here, we check for validity, set switch variables as appropriate
    // and build data structures.  For help option (h), display the help.
    // Returns true if command args parse OK, false otherwise.

    private boolean parseCommandArgs(String[] args) {
        String noCopyrightSwitch = "nc";
        String displayMessagesToErrSwitch = "me";
        boolean argsOK = true;
        boolean inProgramArgumentList = false;
        programArgumentList = null;
        if (args.length == 0)
            return true; // should not get here...
        // If the option to display RARS messages to standard erro is used,
        // it must be processed before any others (since messages may be
        // generated during option parsing).
        processDisplayMessagesToErrSwitch(args, displayMessagesToErrSwitch);
        displayCopyright(args, noCopyrightSwitch);  // ..or not..
        if (args.length == 1 && args[0].equals("h")) {
            displayHelp();
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            // We have seen "pa" switch, so all remaining args are program args
            // that will become "argc" and "argv" for the program.
            if (inProgramArgumentList) {
                if (programArgumentList == null) {
                    programArgumentList = new ArrayList<>();
                }
                programArgumentList.add(args[i]);
                continue;
            }
            // Once we hit "pa", all remaining command args are assumed
            // to be program arguments.
            if (args[i].toLowerCase().equals("pa")) {
                inProgramArgumentList = true;
                continue;
            }
            // messages-to-standard-error switch already processed, so ignore.
            if (args[i].toLowerCase().equals(displayMessagesToErrSwitch)) {
                continue;
            }
            // no-copyright switch already processed, so ignore.
            if (args[i].toLowerCase().equals(noCopyrightSwitch)) {
                continue;
            }
            if (args[i].toLowerCase().equals("dump")) {
                if (args.length <= (i + 3)) {
                    out.println("Dump command line argument requires a segment, format and file name.");
                    argsOK = false;
                } else {
                    if (dumpTriples == null)
                        dumpTriples = new ArrayList<>();
                    dumpTriples.add(new String[]{args[++i], args[++i], args[++i]});
                    //simulate = false;
                }
                continue;
            }
            if (args[i].toLowerCase().equals("mc")) {
                String configName = args[++i];
                MemoryConfiguration config = MemoryConfigurations.getConfigurationByName(configName);
                if (config == null) {
                    out.println("Invalid memory configuration: " + configName);
                    argsOK = false;
                } else {
                    MemoryConfigurations.setCurrentConfiguration(config);
                }
                continue;
            }
            // Set RARS exit code for assemble error
            if (args[i].toLowerCase().indexOf("ae") == 0) {
                String s = args[i].substring(2);
                try {
                    assembleErrorExitCode = Integer.decode(s);
                    continue;
                } catch (NumberFormatException nfe) {
                    // Let it fall thru and get handled by catch-all
                }
            }
            // Set RARS exit code for simulate error
            if (args[i].toLowerCase().indexOf("se") == 0) {
                String s = args[i].substring(2);
                try {
                    simulateErrorExitCode = Integer.decode(s);
                    continue;
                } catch (NumberFormatException nfe) {
                    // Let it fall thru and get handled by catch-all
                }
            }
            if (args[i].toLowerCase().equals("d")) {
                Globals.debug = true;
                continue;
            }
            if (args[i].toLowerCase().equals("a")) {
                simulate = false;
                continue;
            }
            if (args[i].toLowerCase().equals("ad") ||
                    args[i].toLowerCase().equals("da")) {
                Globals.debug = true;
                simulate = false;
                continue;
            }
            if (args[i].toLowerCase().equals("p")) {
                assembleProject = true;
                continue;
            }
            if (args[i].toLowerCase().equals("dec")) {
                displayFormat = DECIMAL;
                continue;
            }
            if (args[i].toLowerCase().equals("hex")) {
                displayFormat = HEXADECIMAL;
                continue;
            }
            if (args[i].toLowerCase().equals("ascii")) {
                displayFormat = ASCII;
                continue;
            }
            if (args[i].toLowerCase().equals("b")) {
                verbose = false;
                continue;
            }
            if (args[i].toLowerCase().equals("np") || args[i].toLowerCase().equals("ne")) {
                options.pseudo = false;
                continue;
            }
            if (args[i].toLowerCase().equals("we")) { // added 14-July-2008 DPS
                options.warningsAreErrors = true;
                continue;
            }
            if (args[i].toLowerCase().equals("sm")) { // added 17-Dec-2009 DPS
                options.startAtMain = true;
                continue;
            }
            if (args[i].toLowerCase().equals("smc")) { // added 5-Jul-2013 DPS
                options.selfModifyingCode = true;
                continue;
            }
            if (args[i].toLowerCase().equals("rv64")) {
                rv64 = true;
                continue;
            }
            if (args[i].toLowerCase().equals("ic")) { // added 19-Jul-2012 DPS
                countInstructions = true;
                continue;
            }
            if (args[i].toLowerCase().equals("harts")) {
                Globals.setHarts(Integer.valueOf(args[++i]));
                continue;
            }
            
            if (new File(args[i]).exists()) {  // is it a file name?
                filenameList.add(args[i]);
                continue;
            }

            if (args[i].indexOf("x") == 0) {
                if (RegisterFile.getRegister(args[i]) == null &&
                        FloatingPointRegisterFile.getRegister(args[i]) == null) {
                    out.println("Invalid Register Name: " + args[i]);
                } else {
                    registerDisplayList.add(args[i]);
                }
                continue;
            }
            // check for register name w/o $.  added 14-July-2008 DPS
            if (RegisterFile.getRegister(args[i]) != null ||
                    FloatingPointRegisterFile.getRegister(args[i]) != null) {
                registerDisplayList.add(args[i]);
                continue;
            }
            // Check for stand-alone integer, which is the max execution steps option
            try {
                Integer.decode(args[i]);
                options.maxSteps = Integer.decode(args[i]); // if we got here, it has to be OK
                continue;
            } catch (NumberFormatException nfe) {
            }
            // Check for integer address range (m-n)
            try {
                String[] memoryRange = checkMemoryAddressRange(args[i]);
                memoryDisplayList.add(memoryRange[0]); // low end of range
                memoryDisplayList.add(memoryRange[1]); // high end of range
                continue;
            } catch (NumberFormatException nfe) {
                out.println("Invalid/unaligned address or invalid range: " + args[i]);
                argsOK = false;
                continue;
            } catch (NullPointerException npe) {
                // Do nothing.  next statement will handle it
            }
            out.println("Invalid Command Argument: " + args[i]);
            argsOK = false;
        }
        return argsOK;
    }


    //////////////////////////////////////////////////////////////////////
    // Carry out the rars command: assemble then optionally run
    // Returns false if no simulation (run) occurs, true otherwise.

    private Program runCommand() {
        if (filenameList.size() == 0) {
            return null;
        }

        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.RV64_ENABLED,rv64);
        InstructionSet.rv64 = rv64;
        Globals.instructionSet.populate();

        File mainFile = new File(filenameList.get(0)).getAbsoluteFile();// First file is "main" file
        ArrayList<String> filesToAssemble;
        if (assembleProject) {
            filesToAssemble = FilenameFinder.getFilenameList(mainFile.getParent(), Globals.fileExtensions);
            if (filenameList.size() > 1) {
                // Using "p" project option PLUS listing more than one filename on command line.
                // Add the additional files, avoiding duplicates.
                filenameList.remove(0); // first one has already been processed
                ArrayList<String> moreFilesToAssemble = FilenameFinder.getFilenameList(filenameList, FilenameFinder.MATCH_ALL_EXTENSIONS);
                // Remove any duplicates then merge the two lists.
                for (int index2 = 0; index2 < moreFilesToAssemble.size(); index2++) {
                    for (int index1 = 0; index1 < filesToAssemble.size(); index1++) {
                        if (filesToAssemble.get(index1).equals(moreFilesToAssemble.get(index2))) {
                            moreFilesToAssemble.remove(index2);
                            index2--; // adjust for left shift in moreFilesToAssemble...
                            break;    // break out of inner loop...
                        }
                    }
                }
                filesToAssemble.addAll(moreFilesToAssemble);
            }
        } else {
            filesToAssemble = FilenameFinder.getFilenameList(filenameList, FilenameFinder.MATCH_ALL_EXTENSIONS);
        }
        Program program = new Program(options);
        try {
            if (Globals.debug) {
                out.println("---  TOKENIZING & ASSEMBLY BEGINS  ---");
            }
            ErrorList warnings = program.assemble(filesToAssemble, mainFile.getAbsolutePath());
            if (warnings != null && warnings.warningsOccurred()) {
                out.println(warnings.generateWarningReport());
            }
        } catch (AssemblyException e) {
            Globals.exitCode = assembleErrorExitCode;
            out.println(e.errors().generateErrorAndWarningReport());
            out.println("Processing terminated due to errors.");
            return null;
        }
        // Setup for program simulation even if just assembling to prepare memory dumps
        program.setup(programArgumentList,null);
        if (simulate) {
            if (Globals.debug) {
                out.println("--------  SIMULATION BEGINS  -----------");
            }
            try {
                while (true) {
                    Simulator.Reason done = program.simulate();
                    if (done == Simulator.Reason.MAX_STEPS) {
                        out.println("\nProgram terminated when maximum step limit " + options.maxSteps + " reached.");
                        break;
                    } else if (done == Simulator.Reason.CLIFF_TERMINATION) {
                        out.println("\nProgram terminated by dropping off the bottom.");
                        break;
                    } else if (done == Simulator.Reason.NORMAL_TERMINATION) {
                        out.println("\nProgram terminated by calling exit");
                        break;
                    }
                    assert done == Simulator.Reason.BREAKPOINT : "Internal error: All cases other than breakpoints should be handled already";
                    displayAllPostMortem(program); // print registers if we hit a breakpoint, then continue
                }

            } catch (SimulationException e) {
                Globals.exitCode = simulateErrorExitCode;
                out.println(e.error().generateReport());
                out.println("Simulation terminated due to errors.");
            }
            displayAllPostMortem(program);
        }
        if (Globals.debug) {
            out.println("\n--------  ALL PROCESSING COMPLETE  -----------");
        }
        return program;
    }


    //////////////////////////////////////////////////////////////////////
    // Check for memory address subrange.  Has to be two integers separated
    // by "-"; no embedded spaces.  e.g. 0x00400000-0x00400010
    // If number is not multiple of 4, will be rounded up to next higher.

    private String[] checkMemoryAddressRange(String arg) throws NumberFormatException {
        String[] memoryRange = null;
        if (arg.indexOf(rangeSeparator) > 0 &&
                arg.indexOf(rangeSeparator) < arg.length() - 1) {
            // assume correct format, two numbers separated by -, no embedded spaces.
            // If that doesn't work it is invalid.
            memoryRange = new String[2];
            memoryRange[0] = arg.substring(0, arg.indexOf(rangeSeparator));
            memoryRange[1] = arg.substring(arg.indexOf(rangeSeparator) + 1);
            // NOTE: I will use homegrown decoder, because Integer.decode will throw
            // exception on address higher than 0x7FFFFFFF (e.g. sign bit is 1).
            if (Binary.stringToInt(memoryRange[0]) > Binary.stringToInt(memoryRange[1]) ||
                    !Memory.wordAligned(Binary.stringToInt(memoryRange[0])) ||
                    !Memory.wordAligned(Binary.stringToInt(memoryRange[1]))) {
                throw new NumberFormatException();
            }
        }
        return memoryRange;
    }

    //////////////////////////////////////////////////////////////////////
    // Displays any specified runtime properties. Initially just instruction count
    // DPS 19 July 2012
    private void displayMiscellaneousPostMortem(Program program) {
        if (countInstructions) {
            out.println("\n" + program.getRegisterValue("cycle"));
        }
    }


    //////////////////////////////////////////////////////////////////////
    // Displays requested register or registers

    private void displayRegistersPostMortem(Program program) {
        // Display requested register contents
        for (String reg : registerDisplayList) {
            if(FloatingPointRegisterFile.getRegister(reg) != null){
                //TODO: do something for double vs float
                // It isn't clear to me what the best behaviour is
                // floating point register
                int ivalue = program.getRegisterValue(reg);
                float fvalue = Float.intBitsToFloat(ivalue);
                if (verbose) {
                    out.print(reg + "\t");
                }
                if (displayFormat == HEXADECIMAL) {
                    // display float (and double, if applicable) in hex
                    out.println(Binary.intToHexString(ivalue));

                } else if (displayFormat == DECIMAL) {
                    // display float (and double, if applicable) in decimal
                    out.println(fvalue);

                } else { // displayFormat == ASCII
                    out.println(Binary.intToAscii(ivalue));
                }
            } else { // Integer register or CSR
                if (verbose)
                    out.print(reg + "\t");
                out.println(formatIntForDisplay((int)RegisterFile.getRegister(reg).getValue()));
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    // Formats int value for display: decimal, hex, ascii
    private String formatIntForDisplay(int value) {
        String strValue;
        switch (displayFormat) {
            case DECIMAL:
                strValue = "" + value;
                break;
            case HEXADECIMAL:
                strValue = Binary.intToHexString(value);
                break;
            case ASCII:
                strValue = Binary.intToAscii(value);
                break;
            default:
                strValue = Binary.intToHexString(value);
        }
        return strValue;
    }

    //////////////////////////////////////////////////////////////////////
    // Displays requested memory range or ranges

    private void displayMemoryPostMortem(Memory memory) {
        int value;
        // Display requested memory range contents
        Iterator<String> memIter = memoryDisplayList.iterator();
        int addressStart = 0, addressEnd = 0;
        while (memIter.hasNext()) {
            try { // This will succeed; error would have been caught during command arg parse
                addressStart = Binary.stringToInt(memIter.next());
                addressEnd = Binary.stringToInt(memIter.next());
            } catch (NumberFormatException nfe) {
            }
            int valuesDisplayed = 0;
            for (int addr = addressStart; addr <= addressEnd; addr += Memory.WORD_LENGTH_BYTES) {
                if (addr < 0 && addressEnd > 0)
                    break;  // happens only if addressEnd is 0x7ffffffc
                if (valuesDisplayed % memoryWordsPerLine == 0) {
                    out.print((valuesDisplayed > 0) ? "\n" : "");
                    if (verbose) {
                        out.print("Mem[" + Binary.intToHexString(addr) + "]\t");
                    }
                }
                try {
                    // Allow display of binary text segment (machine code) DPS 14-July-2008
                    if (Memory.inTextSegment(addr)) {
                        Integer iValue = memory.getRawWordOrNull(addr);
                        value = (iValue == null) ? 0 : iValue;
                    } else {
                        value = memory.getWord(addr);
                    }
                    out.print(formatIntForDisplay(value) + "\t");
                } catch (AddressErrorException aee) {
                    out.print("Invalid address: " + addr + "\t");
                }
                valuesDisplayed++;
            }
            out.println();
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //  If option to display RARS messages to standard err (System.err) is
    //  present, it must be processed before all others.  Since messages may
    //  be output as early as during the command parse.
    private void processDisplayMessagesToErrSwitch(String[] args, String displayMessagesToErrSwitch) {
        for (String arg : args) {
            if (arg.toLowerCase().equals(displayMessagesToErrSwitch)) {
                out = System.err;
                return;
            }
        }
    }
    ///////////////////////////////////////////////////////////////////////
    //  Decide whether copyright should be displayed, and display
    //  if so.

    private void displayCopyright(String[] args, String noCopyrightSwitch) {
        for (String arg : args) {
            if (arg.toLowerCase().equals(noCopyrightSwitch)) {
                return;
            }
        }
        out.println("RARS " + Globals.version + "  Copyright " + Globals.copyrightYears + " " + Globals.copyrightHolders + "\n");
    }


    ///////////////////////////////////////////////////////////////////////
    //  Display command line help text

    private void displayHelp() {
        String[] segmentNames = MemoryDump.getSegmentNames();
        String segments = "";
        for (int i = 0; i < segmentNames.length; i++) {
            segments += segmentNames[i];
            if (i < segmentNames.length - 1) {
                segments += ", ";
            }
        }
        ArrayList<DumpFormat> dumpFormats = DumpFormatLoader.getDumpFormats();
        String formats = "";
        for (int i = 0; i < dumpFormats.size(); i++) {
            formats += dumpFormats.get(i).getCommandDescriptor();
            if (i < dumpFormats.size() - 1) {
                formats += ", ";
            }
        }
        out.println("Usage:  Rars  [options] filename [additional filenames]");
        out.println("  Valid options (not case sensitive, separate by spaces) are:");
        out.println("      a  -- assemble only, do not simulate");
        out.println("  ae<n>  -- terminate RARS with integer exit code <n> if an assemble error occurs.");
        out.println("  ascii  -- display memory or register contents interpreted as ASCII codes.");
        out.println("      b  -- brief - do not display register/memory address along with contents");
        out.println("      d  -- display RARS debugging statements");
        out.println("    dec  -- display memory or register contents in decimal.");
        out.println("   dump <segment> <format> <file> -- memory dump of specified memory segment");
        out.println("            in specified format to specified file.  Option may be repeated.");
        out.println("            Dump occurs at the end of simulation unless 'a' option is used.");
        out.println("            Segment and format are case-sensitive and possible values are:");
        out.println("            <segment> = " + segments+", or a range like 0x400000-0x10000000");
        out.println("            <format> = " + formats);
        out.println("      h  -- display this help.  Use by itself with no filename.");
        out.println("  harts <n> -- the amount of harts to use for running RARS with");
        out.println("    hex  -- display memory or register contents in hexadecimal (default)");
        out.println("     ic  -- display count of basic instructions 'executed'");
        out.println("     mc <config>  -- set memory configuration.  Argument <config> is");
        out.println("            case-sensitive and possible values are: Default for the default");
        out.println("            32-bit address space, CompactDataAtZero for a 32KB memory with");
        out.println("            data segment at address 0, or CompactTextAtZero for a 32KB");
        out.println("            memory with text segment at address 0.");
        out.println("     me  -- display RARS messages to standard err instead of standard out. ");
        out.println("            Can separate messages from program output using redirection");
        out.println("     nc  -- do not display copyright notice (for cleaner redirected/piped output).");
        out.println("     np  -- use of pseudo instructions and formats not permitted");
        out.println("      p  -- Project mode - assemble all files in the same directory as given file.");
        out.println("  se<n>  -- terminate RARS with integer exit code <n> if a simulation (run) error occurs.");
        out.println("     sm  -- start execution at statement with global label main, if defined");
        out.println("    smc  -- Self Modifying Code - Program can write and branch to either text or data segment");
        out.println("    rv64 -- Enables 64 bit assembly and executables (Not fully compatible with rv32)");
        out.println("    <n>  -- where <n> is an integer maximum count of steps to simulate.");
        out.println("            If 0, negative or not specified, there is no maximum.");
        out.println(" x<reg>  -- where <reg> is number or name (e.g. 5, t3, f10) of register whose ");
        out.println("            content to display at end of run.  Option may be repeated.");
        out.println("<reg_name>  -- where <reg_name> is name (e.g. t3, f10) of register whose");
        out.println("            content to display at end of run.  Option may be repeated. ");
        out.println("<m>-<n>  -- memory address range from <m> to <n> whose contents to");
        out.println("            display at end of run. <m> and <n> may be hex or decimal,");
        out.println("            must be on word boundary, <m> <= <n>.  Option may be repeated.");
        out.println("     pa  -- Program Arguments follow in a space-separated list.  This");
        out.println("            option must be placed AFTER ALL FILE NAMES, because everything");
        out.println("            that follows it is interpreted as a program argument to be");
        out.println("            made available to the program at runtime.");
        out.println("If more than one filename is listed, the first is assumed to be the main");
        out.println("unless the global statement label 'main' is defined in one of the files.");
        out.println("Exception handler not automatically assembled.  Add it to the file list.");
        out.println("Options used here do not affect RARS Settings menu values and vice versa.");
    }

}
