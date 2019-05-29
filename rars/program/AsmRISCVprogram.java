package rars.program;

import rars.AssemblyException;
import rars.ErrorList;
import rars.AsmErrorMessage;
import rars.ProgramStatement;
import rars.SimulationException;
import rars.assembler.*;
import rars.riscv.hardware.RegisterFile;
import rars.simulator.Simulator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

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
 * Internal representations of the program.  Connects source, tokens and machine code.  Having
 * all these structures available facilitates construction of good messages,
 * debugging, and easy simulation.
 *
 * @author Pete Sanderson
 * @version August 2003
 **/

public class AsmRISCVprogram extends RISCVprogram {

	// Currently unused:
    // See explanation of method inSteppedExecution() below.
    // private boolean steppedExecution = false;

	private ArrayList<String> sourceList;
    private ArrayList<TokenList> tokenList;
    private ArrayList<ProgramStatement> parsedList;
    private MacroPool macroPool;
    private ArrayList<SourceLine> sourceLineList;
    private Tokenizer tokenizer;
    
    private boolean extendedAssemblerEnabled;
    
    /**
     * Produces list of source statements that comprise the program.
     *
     * @return ArrayList of String.  Each String is one line of RISCV source code.
     **/

    public ArrayList<String> getSourceList() {
        return sourceList;
    }

    /**
     * Set list of source statements that comprise the program.
     *
     * @param sourceLineList ArrayList of SourceLine.
     *                       Each SourceLine represents one line of RISCV source code.
     **/

    public void setSourceLineList(ArrayList<SourceLine> sourceLineList) {
        this.sourceLineList = sourceLineList;
        sourceList = new ArrayList<>();
        for (SourceLine sl : sourceLineList) {
            sourceList.add(sl.getSource());
        }
    }

    /**
     * Retrieve list of source statements that comprise the program.
     *
     * @return ArrayList of SourceLine.
     * Each SourceLine represents one line of RISCV source code
     **/

    public ArrayList<SourceLine> getSourceLineList() {
        return this.sourceLineList;
    }

    /**
     * Produces list of tokens that comprise the program.
     *
     * @return ArrayList of TokenList.  Each TokenList is list of tokens generated by
     * corresponding line of RISCV source code.
     * @see TokenList
     **/

    public ArrayList<TokenList> getTokenList() {
        return tokenList;
    }

    /**
     * Retrieves Tokenizer for this program
     *
     * @return Tokenizer
     **/

    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    /**
     * Produces new empty list to hold parsed source code statements.
     *
     * @return ArrayList of ProgramStatement.  Each ProgramStatement represents a parsed RISCV statement.
     * @see ProgramStatement
     **/

    public ArrayList<ProgramStatement> createParsedList() {
        parsedList = new ArrayList<>();
        return parsedList;
    }

    /**
     * Produces existing list of parsed source code statements.
     *
     * @return ArrayList of ProgramStatement.  Each ProgramStatement represents a parsed RISCV statement.
     * @see ProgramStatement
     **/

    public ArrayList<ProgramStatement> getParsedList() {
        return parsedList;
    }

    /**
     * Produces specified line of RISCV source program.
     *
     * @param i Line number of RISCV source program to get.  Line 1 is first line.
     * @return Returns specified line of RISCV source.  If outside the line range,
     * it returns null.  Line 1 is first line.
     **/

    public String getSourceLine(int i) {
        if ((i >= 1) && (i <= sourceList.size()))
            return sourceList.get(i - 1);
        else
            return null;
    }

    /**
     * Tokenizes the RISCV source program. Program must have already been read from file.
     *
     * @throws AssemblyException Will throw exception if errors occured while tokenizing.
     **/

    public void tokenize() throws AssemblyException {
        this.tokenizer = new Tokenizer();
        this.tokenList = tokenizer.tokenize(this);
        super.setLocalSymbolTable(new SymbolTable(super.getFilename())); // prepare for assembly
    }

    /**
     * Prepares the given list of files for assembly.  This involves
     * reading and tokenizing all the source files.  There may be only one.
     *
     * @param filenames        ArrayList containing the source file name(s) in no particular order
     * @param leadFilename     String containing name of source file that needs to go first and
     *                         will be represented by "this" RISCVprogram object.
     * @param exceptionHandler String containing name of source file containing exception
     *                         handler.  This will be assembled first, even ahead of leadFilename, to allow it to
     *                         include "startup" instructions loaded beginning at 0x00400000.  Specify null or
     *                         empty String to indicate there is no such designated exception handler.
     * @return ArrayList containing one RISCVprogram object for each file to assemble.
     * objects for any additional files (send ArrayList to assembler)
     * @throws AssemblyException Will throw exception if errors occured while reading or tokenizing.
     **/

    public ArrayList<AsmRISCVprogram> prepareFilesForAssembly(ArrayList<String> filenames, String leadFilename, String exceptionHandler) throws AssemblyException {
        ArrayList<AsmRISCVprogram> programsToAssemble = new ArrayList<>();
        int leadFilePosition = 0;
        if (exceptionHandler != null && exceptionHandler.length() > 0) {
            filenames.add(0, exceptionHandler);
            leadFilePosition = 1;
        }
        for (String filename : filenames) {
            AsmRISCVprogram preparee = (filename.equals(leadFilename)) ? this : new AsmRISCVprogram();
            preparee.readSource(filename);
            preparee.tokenize();
            // I want "this" RISCVprogram to be the first in the list...except for exception handler
            if (preparee == this && programsToAssemble.size() > 0) {
                programsToAssemble.add(leadFilePosition, preparee);
            } else {
                programsToAssemble.add(preparee);
            }
        }
        return programsToAssemble;
    }

    /**
     * 
     * @param extendedAssemblerEnabled true to enable the extended assembler
     */
    public void setExtendedAssembler(boolean extendedAssemblerEnabled) {
    	this.extendedAssemblerEnabled = extendedAssemblerEnabled;
    }

    /**
     * Simulates execution of the program (in this thread). Program must have already been assembled.
     * Begins simulation at current program counter address and continues until stopped,
     * paused, maximum steps exceeded, or exception occurs.
     *
     * @param maxSteps the maximum maximum number of steps to simulate.
     * @return true if execution completed and false otherwise
     * @throws SimulationException Will throw exception if errors occured while simulating.
     */
    public Simulator.Reason simulate(int maxSteps) throws SimulationException {
        Simulator sim = Simulator.getInstance();
        return sim.simulate(RegisterFile.getProgramCounter(), maxSteps, null);
    }

    /**
     * Simulates execution of the program (in a new thread). Program must have already been assembled.
     * Begins simulation at current program counter address and continues until stopped,
     * paused, maximum steps exceeded, or exception occurs.
     *
     * @param maxSteps    maximum number of instruction executions.  Default -1 means no maximum.
     * @param breakPoints int array of breakpoints (PC addresses).  Can be null.
     **/
    public void startSimulation(int maxSteps, int[] breakPoints) {
        Simulator sim = Simulator.getInstance();
        sim.startSimulation(RegisterFile.getProgramCounter(), maxSteps, breakPoints);
    }

    /**
     * Instantiates a new {@link MacroPool} and sends reference of this
     * {@link AsmRISCVprogram} to it
     *
     * @return instatiated MacroPool
     * @author M.H.Sekhavat <sekhavat17@gmail.com>
     */
    public MacroPool createMacroPool() {
        macroPool = new MacroPool(this);
        return macroPool;
    }

    /**
     * Gets local macro pool {@link MacroPool} for this program
     *
     * @return MacroPool
     * @author M.H.Sekhavat <sekhavat17@gmail.com>
     */
    public MacroPool getLocalMacroPool() {
        return macroPool;
    }

    /**
     * Sets local macro pool {@link MacroPool} for this program
     *
     * @param macroPool reference to MacroPool
     * @author M.H.Sekhavat <sekhavat17@gmail.com>
     */
    public void setLocalMacroPool(MacroPool macroPool) {
        this.macroPool = macroPool;
    }
    
    
    
    /* OVERRIDES **********************/
    
    @Override
    public void readSourceHelper() throws AssemblyException {
        this.sourceList = new ArrayList<>();
        ErrorList errors;
        BufferedReader inputFile;
        String line;
        try {
            inputFile = new BufferedReader(new FileReader(super.getFilename()));
            line = inputFile.readLine();
            while (line != null) {
                sourceList.add(line);
                line = inputFile.readLine();
            }
            inputFile.close();
        } catch (Exception e) {
            errors = new ErrorList();
            errors.add(new AsmErrorMessage((AsmRISCVprogram) null, 0, 0, e.toString()));
            throw new AssemblyException(errors);
        }
    }
    
    @Override
    public ErrorList assembleHelper(ArrayList<? extends RISCVprogram> programsToAssemble, boolean warningsAreErrors) 
    		throws AssemblyException {
    	
    	// Cast files to assembly programs
    	ArrayList<AsmRISCVprogram> src = new ArrayList<AsmRISCVprogram>();
    	for(RISCVprogram rp : programsToAssemble) {
    		if(rp instanceof AsmRISCVprogram)
    			src.add((AsmRISCVprogram)rp);
    		else
    			throw new RuntimeException("Attempting to assemble illegal source file.");
    	}
    	
        Assembler asm = new Assembler();
        super.setMachineList(asm.assemble(src, this.extendedAssemblerEnabled, warningsAreErrors));
        return asm.getErrorList();
    }

}  // RISCVprogram
