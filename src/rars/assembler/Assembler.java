package rars.assembler;

import rars.*;
import rars.riscv.hardware.AddressErrorException;
import rars.riscv.hardware.Memory;
import rars.riscv.BasicInstruction;
import rars.riscv.ExtendedInstruction;
import rars.riscv.Instruction;
import rars.util.Binary;
import rars.util.SystemIO;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;


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
 * An Assembler is capable of assembling a RISCV program. It has only one public
 * method, <tt>assemble()</tt>, which implements a two-pass assembler. It
 * translates RISCV source code into binary machine code.
 *
 * @author Pete Sanderson
 * @version August 2003
 **/

public class Assembler {
    private ErrorList errors;
    private boolean inDataSegment; // status maintained by parser
    private boolean inMacroSegment; // status maintained by parser, true if in
    // macro definition segment
    private int externAddress;
    private boolean autoAlign;
    private Directives dataDirective;
    private RISCVprogram fileCurrentlyBeingAssembled;
    private TokenList globalDeclarationList;
    private AddressSpace textAddress;
    private AddressSpace dataAddress;
    private DataSegmentForwardReferences currentFileDataSegmentForwardReferences,
            accumulatedDataSegmentForwardReferences;
    private ArrayList<DataStruct> datalist;
    class DataStruct {
        int address, data, size;
        ErrorMessage iffails;
        DataStruct(int address, int data, int size, ErrorMessage iffails){
            this.address = address;
            this.data = data;
            this.size = size;
            this.iffails = iffails;
        }
    }
    private void setData(int address, int data, int size, ErrorMessage iffails){
        datalist.add(new DataStruct(address,data,size,iffails));
    }
    /**
     * Get list of assembler errors and warnings
     *
     * @return ErrorList of any assembler errors and warnings.
     */
    public ErrorList getErrorList() {
        return errors;
    }

    /**
     * Parse and generate machine code for the given program. All source
     * files must have already been tokenized.
     *
     * @param tokenizedProgramFiles    An ArrayList of RISCVprogram objects, each produced from a
     *                                 different source code file, representing the program source.
     * @param extendedAssemblerEnabled A boolean value that if true permits use of extended (pseudo)
     *                                 instructions in the source code. If false, these are flagged
     *                                 as errors.
     * @param warningsAreErrors        A boolean value - true means assembler warnings will be
     *                                 considered errors and terminate the assemble; false means the
     *                                 assembler will produce warning message but otherwise ignore
     *                                 warnings.
     * @return An ArrayList representing the assembled program. Each member of
     * the list is a ProgramStatement object containing the source,
     * intermediate, and machine binary representations of a program
     * statement. Returns null if incoming array list is null or empty.
     * @see ProgramStatement
     **/
    public ArrayList<ProgramStatement> assemble(ArrayList<RISCVprogram> tokenizedProgramFiles, boolean extendedAssemblerEnabled,
                                                boolean warningsAreErrors) throws AssemblyException {

        if (tokenizedProgramFiles == null || tokenizedProgramFiles.size() == 0)
            return null;
        textAddress = new AddressSpace(Memory.configuration.getTextBaseAddress());
        dataAddress = new AddressSpace(Memory.configuration.getDataBaseAddress());
        externAddress = Memory.configuration.getExternBaseAddress();
        currentFileDataSegmentForwardReferences = new DataSegmentForwardReferences();
        accumulatedDataSegmentForwardReferences = new DataSegmentForwardReferences();
        Globals.symbolTable.clear();
        Globals.memory.clear();
        ArrayList<ProgramStatement> machineList = new ArrayList<>();
        datalist = new ArrayList<>();
        this.errors = new ErrorList();
        if (Globals.debug)
            System.out.println("Assembler first pass begins:");
        // PROCESS THE FIRST ASSEMBLY PASS FOR ALL SOURCE FILES BEFORE PROCEEDING
        // TO SECOND PASS. THIS ASSURES ALL SYMBOL TABLES ARE CORRECTLY BUILT.
        // THERE IS ONE GLOBAL SYMBOL TABLE (for identifiers declared .globl) PLUS
        // ONE LOCAL SYMBOL TABLE FOR EACH SOURCE FILE.
        for (RISCVprogram program : tokenizedProgramFiles) {
            if (errors.errorLimitExceeded())
                break;
            this.fileCurrentlyBeingAssembled = program;
            // List of labels declared ".globl". new list for each file assembled
            this.globalDeclarationList = new TokenList();
            // Parser begins by default in text segment until directed otherwise.
            this.inDataSegment = false;
            // Macro segment will be started by .macro directive
            this.inMacroSegment = false;
            // Default is to align data from directives on appropriate boundary (word, half, byte)
            // This can be turned off for remainder of current data segment with ".align 0"
            this.autoAlign = true;
            // Default data directive is .word for 4 byte data items
            this.dataDirective = Directives.WORD;
            // Clear out (initialize) symbol table related structures.
            fileCurrentlyBeingAssembled.getLocalSymbolTable().clear();
            currentFileDataSegmentForwardReferences.clear();
            // sourceList is an ArrayList of String objects, one per source line.
            // tokenList is an ArrayList of TokenList objects, one per source line;
            // each ArrayList in tokenList consists of Token objects.
            ArrayList<SourceLine> sourceLineList = fileCurrentlyBeingAssembled.getSourceLineList();
            ArrayList<TokenList> tokenList = fileCurrentlyBeingAssembled.getTokenList();
            ArrayList<ProgramStatement> parsedList = fileCurrentlyBeingAssembled.createParsedList();
            // each file keeps its own macro definitions
            MacroPool macroPool = fileCurrentlyBeingAssembled.createMacroPool();
            // FIRST PASS OF ASSEMBLER VERIFIES SYNTAX, GENERATES SYMBOL TABLE,
            // INITIALIZES DATA SEGMENT
            ArrayList<ProgramStatement> statements;
            for (int i = 0; i < tokenList.size(); i++) {
                if (errors.errorLimitExceeded())
                    break;
                for (Token t : tokenList.get(i)) {
                    // record this token's original source program and line #. Differs from final, if .include used
                    t.setOriginal(sourceLineList.get(i).getRISCVprogram(), sourceLineList.get(i).getLineNumber());
                }
                statements = this.parseLine(tokenList.get(i),
                        sourceLineList.get(i).getSource(),
                        sourceLineList.get(i).getLineNumber(),
                        extendedAssemblerEnabled);
                if (statements != null) {
                    parsedList.addAll(statements);
                }
            }
            if (inMacroSegment) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled,
                        fileCurrentlyBeingAssembled.getLocalMacroPool().getCurrent().getFromLine(),
                        0, "Macro started but not ended (no .end_macro directive)"));
            }
            // move ".globl" symbols from local symtab to global
            this.transferGlobals();
            // Attempt to resolve forward label references that were discovered in operand fields
            // of data segment directives in current file. Those that are not resolved after this
            // call are either references to global labels not seen yet, or are undefined.
            // Cannot determine which until all files are parsed, so copy unresolved entries
            // into accumulated list and clear out this one for re-use with the next source file.
            currentFileDataSegmentForwardReferences.resolve(fileCurrentlyBeingAssembled
                    .getLocalSymbolTable());
            accumulatedDataSegmentForwardReferences.add(currentFileDataSegmentForwardReferences);
            currentFileDataSegmentForwardReferences.clear();
        } // end of first-pass loop for each RISCVprogram


        // Have processed all source files. Attempt to resolve any remaining forward label
        // references from global symbol table. Those that remain unresolved are undefined
        // and require error message.
        accumulatedDataSegmentForwardReferences.resolve(Globals.symbolTable);
        accumulatedDataSegmentForwardReferences.generateErrorMessages(errors);

        // Throw collection of errors accumulated through the first pass.
        if (errors.errorsOccurred()) {
            throw new AssemblyException(errors);
        }
        if (Globals.debug)
            System.out.println("Assembler second pass begins");
        // SECOND PASS OF ASSEMBLER GENERATES BASIC ASSEMBLER THEN MACHINE CODE.
        // Generates basic assembler statements...
        for (RISCVprogram program : tokenizedProgramFiles) {
            if (errors.errorLimitExceeded())
                break;
            this.fileCurrentlyBeingAssembled = program;
            ArrayList<ProgramStatement> parsedList = fileCurrentlyBeingAssembled.getParsedList();
            for (ProgramStatement statement : parsedList) {
                statement.buildBasicStatementFromBasicInstruction(errors);
                if (errors.errorsOccurred()) {
                    throw new AssemblyException(errors);
                }
                if (statement.getInstruction() instanceof BasicInstruction) {
                    machineList.add(statement);
                } else {
                    // It is a pseudo-instruction:
                    // 1. Fetch its basic instruction template list
                    // 2. For each template in the list,
                    // 2a. substitute operands from source statement
                    // 2b. tokenize the statement generated by 2a.
                    // 2d. call parseLine() to generate basic instrction
                    // 2e. add returned programStatement to the list
                    // The templates, and the instructions generated by filling
                    // in the templates, are specified
                    // in basic format (e.g. mnemonic register reference zero
                    // already translated to x0).
                    // So the values substituted into the templates need to be
                    // in this format. Since those
                    // values come from the original source statement, they need
                    // to be translated before
                    // substituting. The next method call will perform this
                    // translation on the original
                    // source statement. Despite the fact that the original
                    // statement is a pseudo
                    // instruction, this method performs the necessary
                    // translation correctly.
                    // TODO: consider making this recursive
                    ExtendedInstruction inst = (ExtendedInstruction) statement.getInstruction();
                    String basicAssembly = statement.getBasicAssemblyStatement();
                    int sourceLine = statement.getSourceLine();
                    TokenList theTokenList = new Tokenizer().tokenizeLine(sourceLine,
                            basicAssembly, errors, false);

                    // ////////////////////////////////////////////////////////////////////////////
                    // If we are using compact memory config and there is a compact expansion, use it
                    ArrayList<String> templateList;
                    templateList = inst.getBasicIntructionTemplateList();


                    // subsequent ProgramStatement constructor needs the correct text segment address.
                    textAddress.set(statement.getAddress());
                    // Will generate one basic instruction for each template in the list.
                    int PC = textAddress.get(); // Save the starting PC so that it can be used for PC relative stuff
                    for (int instrNumber = 0; instrNumber < templateList.size(); instrNumber++) {
                        String instruction = ExtendedInstruction.makeTemplateSubstitutions(
                                this.fileCurrentlyBeingAssembled,
                                templateList.get(instrNumber), theTokenList, PC);

                        // All substitutions have been made so we have generated
                        // a valid basic instruction!
                        if (Globals.debug)
                            System.out.println("PSEUDO generated: " + instruction);
                        // For generated instruction: tokenize, build program
                        // statement, add to list.
                        TokenList newTokenList = new Tokenizer().tokenizeLine(sourceLine,
                                instruction, errors, false);
                        ArrayList<Instruction> instrMatches = this.matchInstruction(newTokenList.get(0));
                        Instruction instr = OperandFormat.bestOperandMatch(newTokenList,
                                instrMatches);
                        // Only first generated instruction is linked to original source
                        ProgramStatement ps = new ProgramStatement(
                                this.fileCurrentlyBeingAssembled,
                                (instrNumber == 0) ? statement.getSource() : "", newTokenList,
                                newTokenList, instr, textAddress.get(), statement.getSourceLine());
                        textAddress.increment(Instruction.INSTRUCTION_LENGTH);
                        ps.buildBasicStatementFromBasicInstruction(errors);
                        machineList.add(ps);
                    } // end of FOR loop, repeated for each template in list.
                } // end of ELSE part for extended instruction.

            } // end of assembler second pass.
        }
        if (Globals.debug)
            System.out.println("Code generation begins");
        ///////////// THIRD MAJOR STEP IS PRODUCE MACHINE CODE FROM ASSEMBLY //////////
        // Generates machine code statements from the list of basic assembler statements
        // and writes the statement to memory.

        for (ProgramStatement statement : machineList) {
            if (errors.errorLimitExceeded())
                break;
            statement.buildMachineStatementFromBasicStatement(errors);
            if (Globals.debug)
                System.out.println(statement);
            try {
                Globals.memory.setStatement(statement.getAddress(), statement);
            } catch (AddressErrorException e) {
                Token t = statement.getOriginalTokenList().get(0);
                errors.add(new ErrorMessage(t.getSourceProgram(), t.getSourceLine(), t
                        .getStartPos(), "Invalid address for text segment: " + statement.getAddress()));
            }
        }

        for (DataStruct ds : datalist) {
            try {
                Globals.memory.set(ds.address, ds.data, ds.size);
            } catch (AddressErrorException e) {
                if(ds.iffails != null) errors.add(ds.iffails);
            }
        }

        // Aug. 24, 2005 Ken Vollmar
        // Ensure that I/O "file descriptors" are initialized for a new program run
        SystemIO.resetFiles();
        // DPS 6 Dec 2006:
        // We will now sort the ArrayList of ProgramStatements by getAddress() value.
        // This is for display purposes, since they have already been stored to Memory.
        // Use of .ktext and .text with address operands has two implications:
        // (1) the addresses may not be ordered at this point. Requires unsigned int
        // sort because kernel addresses are negative. See special Comparator.
        // (2) It is possible for two instructions to be placed at the same address.
        // Such occurances will be flagged as errors.
        // Yes, I would not have to sort here if I used SortedSet rather than ArrayList
        // but in case of duplicate I like having both statements handy for error message.
        Collections.sort(machineList);
        catchDuplicateAddresses(machineList, errors);
        if (errors.errorsOccurred() || errors.warningsOccurred() && warningsAreErrors) {
            throw new AssemblyException(errors);
        }
        return machineList;
    } // assemble()

    // //////////////////////////////////////////////////////////////////////
    // Will check for duplicate text addresses, which can happen inadvertantly when using
    // operand on .text directive. Will generate error message for each one that occurs.
    private void catchDuplicateAddresses(ArrayList<ProgramStatement> instructions, ErrorList errors) {
        for (int i = 0; i < instructions.size() - 1; i++) {
            ProgramStatement ps1 = instructions.get(i);
            ProgramStatement ps2 = instructions.get(i + 1);
            if (ps1.getAddress() == ps2.getAddress()) {
                errors.add(new ErrorMessage(ps2.getSourceProgram(), ps2.getSourceLine(), 0,
                        "Duplicate text segment address: "
                                + rars.venus.NumberDisplayBaseChooser.formatUnsignedInteger(ps2
                                .getAddress(), (Globals.getSettings()
                                .getBooleanSetting(Settings.Bool.DISPLAY_ADDRESSES_IN_HEX)) ? 16 : 10)
                                + " already occupied by " + ps1.getSourceFile() + " line "
                                + ps1.getSourceLine() + " (caused by use of "
                                + ((Memory.inTextSegment(ps2.getAddress())) ? ".text" : ".ktext")
                                + " operand)"));
            }
        }
    }

    /**
     * This method parses one line of RISCV source code. It works with the list
     * of tokens, but original source is also provided. It also carries out
     * directives, which includes initializing the data segment. This method is
     * invoked in the assembler first pass.
     *
     * @param tokenList
     * @param source
     * @param sourceLineNumber
     * @param extendedAssemblerEnabled
     * @return ArrayList of ProgramStatements because parsing a macro expansion
     * request will return a list of ProgramStatements expanded
     */
    private ArrayList<ProgramStatement> parseLine(TokenList tokenList, String source,
                                                  int sourceLineNumber, boolean extendedAssemblerEnabled) {

        ArrayList<ProgramStatement> ret = new ArrayList<>();

        ProgramStatement programStatement;
        TokenList tokens = this.stripComment(tokenList);

        // Labels should not be processed in macro definition segment.
        MacroPool macroPool = fileCurrentlyBeingAssembled.getLocalMacroPool();
        if (inMacroSegment) {
            detectLabels(tokens, macroPool.getCurrent());
        } else {
            stripLabels(tokens);
        }
        if (tokens.isEmpty())
            return null;
        // Grab first (operator) token...
        Token token = tokens.get(0);
        TokenTypes tokenType = token.getType();

        // Let's handle the directives here...
        if (tokenType == TokenTypes.DIRECTIVE) {
            this.executeDirective(tokens);
            return null;
        }

        // don't parse if in macro segment
        if (inMacroSegment)
            return null;

        // SPIM-style macro calling:
        TokenList parenFreeTokens = tokens;
        if (tokens.size() > 2 && tokens.get(1).getType() == TokenTypes.LEFT_PAREN
                && tokens.get(tokens.size() - 1).getType() == TokenTypes.RIGHT_PAREN) {
            parenFreeTokens = (TokenList) tokens.clone();
            parenFreeTokens.remove(tokens.size() - 1);
            parenFreeTokens.remove(1);
        }
        Macro macro = macroPool.getMatchingMacro(parenFreeTokens, sourceLineNumber);//parenFreeTokens.get(0).getSourceLine());

        // expand macro if this line is a macro expansion call
        if (macro != null) {
            tokens = parenFreeTokens;
            // get unique id for this expansion
            int counter = macroPool.getNextCounter();
            if (macroPool.pushOnCallStack(token)) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, tokens.get(0)
                        .getSourceLine(), 0, "Detected a macro expansion loop (recursive reference). "));
            } else {
                //                for (int i = macro.getFromLine() + 1; i < macro.getToLine(); i++) {
                //                   String substituted = macro.getSubstitutedLine(i, tokens, counter, errors);
                //                   TokenList tokenList2 = fileCurrentlyBeingAssembled.getTokenizer().tokenizeLine(
                //                      i, substituted, errors);
                //                   // If token list getProcessedLine() is not empty, then .eqv was performed and it contains the modified source.
                //                	// Put it into the line to be parsed, so it will be displayed properly in text segment display. DPS 23 Jan 2013
                //                   if (tokenList2.getProcessedLine().length() > 0)
                //                      substituted = tokenList2.getProcessedLine();
                //                   // recursively parse lines of expanded macro
                //                   ArrayList<ProgramStatement> statements = parseLine(tokenList2, "<" + (i-macro.getFromLine()+macro.getOriginalFromLine()) + "> "
                //                      + substituted.trim(), sourceLineNumber, extendedAssemblerEnabled);
                //                   if (statements != null)
                //                      ret.addAll(statements);
                //                }
                for (int i = macro.getFromLine() + 1; i < macro.getToLine(); i++) {

                    String substituted = macro.getSubstitutedLine(i, tokens, counter, errors);
                    TokenList tokenList2 = fileCurrentlyBeingAssembled.getTokenizer().tokenizeLine(
                            i, substituted, errors);

                    // If token list getProcessedLine() is not empty, then .eqv was performed and it contains the modified source.
                    // Put it into the line to be parsed, so it will be displayed properly in text segment display. DPS 23 Jan 2013
                    if (tokenList2.getProcessedLine().length() > 0)
                        substituted = tokenList2.getProcessedLine();

                    // recursively parse lines of expanded macro
                    ArrayList<ProgramStatement> statements = parseLine(tokenList2, "<" + (i - macro.getFromLine() + macro.getOriginalFromLine()) + "> "
                            + substituted.trim(), sourceLineNumber, extendedAssemblerEnabled);
                    if (statements != null)
                        ret.addAll(statements);
                }
                macroPool.popFromCallStack();
            }
            return ret;
        }

        // TODO: check what gcc and clang generated assembly looks like currently
        // DPS 14-July-2008
        // Yet Another Hack: detect unrecognized directive. MARS recognizes the same directives
        // as SPIM but other MIPS assemblers recognize additional directives. Compilers such
        // as MIPS-directed GCC generate assembly code containing these directives. We'd like
        // the opportunity to ignore them and continue. Tokenizer would categorize an unrecognized
        // directive as an TokenTypes.IDENTIFIER because it would not be matched as a directive and
        // MIPS labels can start with '.' NOTE: this can also be handled by including the
        // ignored directive in the Directives.java list. There is already a mechanism in place
        // for generating a warning there. But I cannot anticipate the names of all directives
        // so this will catch anything, including a misspelling of a valid directive (which is
        // a nice thing to do).
        if (tokenType == TokenTypes.IDENTIFIER && token.getValue().charAt(0) == '.') {
            errors.add(new ErrorMessage(ErrorMessage.WARNING, token.getSourceProgram(), token
                    .getSourceLine(), token.getStartPos(), "RARS does not recognize the "
                    + token.getValue() + " directive.  Ignored."));
            return null;
        }

        // The directives with lists (.byte, .double, .float, .half, .word, .ascii, .asciiz)
        // should be able to extend the list over several lines. Since this method assembles
        // only one source line, state information must be stored from one invocation to
        // the next, to sense the context of this continuation line. That state information
        // is contained in this.dataDirective (the current data directive).
        //
        if (this.inDataSegment && // 30-Dec-09 DPS Added data segment guard...
                (tokenType == TokenTypes.PLUS
                        || // because invalid instructions were being caught...
                        tokenType == TokenTypes.MINUS
                        || // here and reported as a directive in text segment!
                        tokenType == TokenTypes.QUOTED_STRING || tokenType == TokenTypes.IDENTIFIER
                        || TokenTypes.isIntegerTokenType(tokenType) || TokenTypes
                        .isFloatingTokenType(tokenType))) {
            this.executeDirectiveContinuation(tokens);
            return null;
        }

        // If we are in the text segment, the variable "token" must now refer to
        // an OPERATOR
        // token. If not, it is either a syntax error or the specified operator
        // is not
        // yet implemented.
        if (!this.inDataSegment) {
            ArrayList<Instruction> instrMatches = this.matchInstruction(token);
            if (instrMatches == null)
                return ret;
            // OK, we've got an operator match, let's check the operands.
            Instruction inst = OperandFormat.bestOperandMatch(tokens, instrMatches);
            // Here's the place to flag use of extended (pseudo) instructions
            // when setting disabled.
            if (inst instanceof ExtendedInstruction && !extendedAssemblerEnabled) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(),
                        "Extended (pseudo) instruction or format not permitted.  See Settings."));
            }
            if (OperandFormat.tokenOperandMatch(tokens, inst, errors)) {
                programStatement = new ProgramStatement(this.fileCurrentlyBeingAssembled, source,
                        tokenList, tokens, inst, textAddress.get(), sourceLineNumber);
                // instruction length is 4 for all basic instruction, varies for extended instruction
                // Modified to permit use of compact expansion if address fits
                // in 15 bits. DPS 4-Aug-2009
                int instLength = inst.getInstructionLength();
                textAddress.increment(instLength);
                ret.add(programStatement);
                return ret;
            }
        }
        return null;
    } // parseLine()

    private void detectLabels(TokenList tokens, Macro current) {
        if (tokenListBeginsWithLabel(tokens))
            current.addLabel(tokens.get(0).getValue());
    }

    // //////////////////////////////////////////////////////////////////////////////////
    // Pre-process the token list for a statement by stripping off any comment.
    // NOTE: the ArrayList parameter is not modified; a new one is cloned and
    // returned.
    private TokenList stripComment(TokenList tokenList) {
        if (tokenList.isEmpty())
            return tokenList;
        TokenList tokens = (TokenList) tokenList.clone();
        // If there is a comment, strip it off.
        int last = tokens.size() - 1;
        if (tokens.get(last).getType() == TokenTypes.COMMENT) {
            tokens.remove(last);
        }
        return tokens;
    } // stripComment()

    /**
     * Pre-process the token list for a statement by stripping off any label, if
     * either are present. Any label definition will be recorded in the symbol
     * table. NOTE: the ArrayList parameter will be modified.
     */
    private void stripLabels(TokenList tokens) {
        // If there is a label, handle it here and strip it off.
        boolean thereWasLabel = this.parseAndRecordLabel(tokens);
        if (thereWasLabel) {
            tokens.remove(0); // Remove the IDENTIFIER.
            tokens.remove(0); // Remove the COLON, shifted to 0 by previous remove
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////
    // Parse and record label, if there is one. Note the identifier and its colon are
    // two separate tokens, since they may be separated by spaces in source code.
    private boolean parseAndRecordLabel(TokenList tokens) {
        if (tokens.size() < 2) {
            return false;
        } else {
            Token token = tokens.get(0);
            if (tokenListBeginsWithLabel(tokens)) {
                if (token.getType() == TokenTypes.OPERATOR) {
                    // an instruction name was used as label (e.g. lw:), so change its token type
                    token.setType(TokenTypes.IDENTIFIER);
                }
                fileCurrentlyBeingAssembled.getLocalSymbolTable().addSymbol(token,
                        (this.inDataSegment) ? dataAddress.get() : textAddress.get(),
                        this.inDataSegment, this.errors);
                return true;
            } else {
                return false;
            }
        }
    } // parseLabel()

    private boolean tokenListBeginsWithLabel(TokenList tokens) {
        // 2-July-2010. DPS. Remove prohibition of operator names as labels
        if (tokens.size() < 2)
            return false;
        return (tokens.get(0).getType() == TokenTypes.IDENTIFIER || tokens.get(0).getType() == TokenTypes.OPERATOR)
                && tokens.get(1).getType() == TokenTypes.COLON;
    }

    // /////////////////////////////////////////////////////////////////////////////
    // This source code line is a directive, not a instruction. Let's carry it out.
    private void executeDirective(TokenList tokens) {
        Token token = tokens.get(0);
        Directives direct = Directives.matchDirective(token.getValue());
        if (Globals.debug)
            System.out.println("line " + token.getSourceLine() + " is directive " + direct);
        if (direct == null) {
            errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(), token
                    .getStartPos(), "\"" + token.getValue()
                    + "\" directive is invalid or not implemented in RARS"));
            return;
        } else if (direct == Directives.EQV) { /* EQV added by DPS 11 July 2012 */
            // Do nothing.  This was vetted and processed during tokenizing.
        } else if (direct == Directives.MACRO) {
            if (tokens.size() < 2) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "\"" + token.getValue()
                        + "\" directive requires at least one argument."));
                return;
            }
            if (tokens.get(1).getType() != TokenTypes.IDENTIFIER) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        tokens.get(1).getStartPos(), "Invalid Macro name \""
                        + tokens.get(1).getValue() + "\""));
                return;
            }
            if (inMacroSegment) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "Nested macros are not allowed"));
                return;
            }
            inMacroSegment = true;
            MacroPool pool = fileCurrentlyBeingAssembled.getLocalMacroPool();
            pool.beginMacro(tokens.get(1));
            for (int i = 2; i < tokens.size(); i++) {
                Token arg = tokens.get(i);
                if (arg.getType() == TokenTypes.RIGHT_PAREN
                        || arg.getType() == TokenTypes.LEFT_PAREN)
                    continue;
                if (!Macro.tokenIsMacroParameter(arg.getValue(), true)) {
                    errors.add(new ErrorMessage(arg.getSourceProgram(), arg.getSourceLine(),
                            arg.getStartPos(), "Invalid macro argument '" + arg.getValue() + "'"));
                    return;
                }
                pool.getCurrent().addArg(arg.getValue());
            }
        } else if (direct == Directives.END_MACRO) {
            if (tokens.size() > 1) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "invalid text after .END_MACRO"));
                return;
            }
            if (!inMacroSegment) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), ".END_MACRO without .MACRO"));
                return;
            }
            inMacroSegment = false;
            fileCurrentlyBeingAssembled.getLocalMacroPool().commitMacro(token);
        } else if (inMacroSegment) {
            // should not parse lines even directives in macro segment
            return;
        } else if (direct == Directives.DATA) {
            this.inDataSegment = true;
            this.autoAlign = true;
            if (tokens.size() > 1 && TokenTypes.isIntegerTokenType(tokens.get(1).getType())) {
                this.dataAddress.set(Binary.stringToInt(tokens.get(1).getValue())); // KENV 1/6/05
            }
        } else if (direct == Directives.TEXT) {
            this.inDataSegment = false;
            if (tokens.size() > 1 && TokenTypes.isIntegerTokenType(tokens.get(1).getType())) {
                this.textAddress.set(Binary.stringToInt(tokens.get(1).getValue())); // KENV 1/6/05
            }
        } else if (direct == Directives.SECTION){
            if(tokens.size() >= 2){
                Token section = tokens.get(1);
                if(section.getType() == TokenTypes.QUOTED_STRING || section.getType() == TokenTypes.IDENTIFIER){
                    String str = section.getValue();
                    if(str.startsWith(".data") || str.startsWith(".rodata") || str.startsWith(".sdata")){
                        this.inDataSegment = true;
                    }else if(str.startsWith(".text")){
                        this.inDataSegment = false;
                    }else{
                        errors.add(new ErrorMessage(true,token.getSourceProgram(),token.getSourceLine(),token.getStartPos(),
                                "section name \""+str+"\" is ignored"));
                    }
                } else {
                    errors.add(new ErrorMessage(token.getSourceProgram(),token.getSourceLine(),token.getStartPos(),
                            ".section must be followed by a section name "));
                }
            }else{
                errors.add(new ErrorMessage(true,token.getSourceProgram(),token.getSourceLine(),token.getStartPos(),
                        ".section without arguments is ignored"));
            }
        } else if (direct == Directives.WORD || direct == Directives.HALF
                || direct == Directives.BYTE || direct == Directives.FLOAT
                || direct == Directives.DOUBLE || direct == Directives.DWORD ) {
            this.dataDirective = direct;
            if (passesDataSegmentCheck(token) && tokens.size() > 1) { // DPS
                // 11/20/06, added text segment prohibition
                storeNumeric(tokens, direct, errors);
            }
        } else if (direct == Directives.ASCII || direct == Directives.ASCIZ || direct == Directives.STRING) {
            this.dataDirective = direct;
            if (passesDataSegmentCheck(token)) {
                storeStrings(tokens, direct, errors);
            }
        } else if (direct == Directives.ALIGN) {
            if (tokens.size() != 2) {
                errors.add(new ErrorMessage(token.getSourceProgram(),
                        token.getSourceLine(), token.getStartPos(), "\"" + token.getValue()
                        + "\" requires one operand"));
                return;
            }
            if (!TokenTypes.isIntegerTokenType(tokens.get(1).getType())
                    || Binary.stringToInt(tokens.get(1).getValue()) < 0) {
                errors.add(new ErrorMessage(token.getSourceProgram(),
                        token.getSourceLine(), token.getStartPos(), "\"" + token.getValue()
                        + "\" requires a non-negative integer"));
                return;
            }
            int value = Binary.stringToInt(tokens.get(1).getValue()); // KENV 1/6/05
            if(value < 2 && !this.inDataSegment) {
                errors.add(new ErrorMessage(true,token.getSourceProgram(),token.getSourceLine(),token.getStartPos(),
                        "Alignments less than 4 bytes are not supported in the text section. The alignment has been rounded up to 4 bytes."));
                this.dataAddress.set(this.alignToBoundary(this.dataAddress.get(),4));
            } else if (value == 0) {
                this.autoAlign = false;
            } else {
                this.dataAddress.set(this.alignToBoundary(this.dataAddress.get(),(int)Math.pow(2,value)));
            }
        } else if (direct == Directives.SPACE) {
            // TODO: add a fill type option
            // .space 90, 1 should fill memory with 90 bytes with the values 1
            if (passesDataSegmentCheck(token)) {
                if (tokens.size() != 2) {
                    errors.add(new ErrorMessage(token.getSourceProgram(),
                            token.getSourceLine(), token.getStartPos(), "\"" + token.getValue()
                            + "\" requires one operand"));
                    return;
                }
                if (!TokenTypes.isIntegerTokenType(tokens.get(1).getType())
                        || Binary.stringToInt(tokens.get(1).getValue()) < 0) {
                    errors.add(new ErrorMessage(token.getSourceProgram(),
                            token.getSourceLine(), token.getStartPos(), "\"" + token.getValue()
                            + "\" requires a non-negative integer"));
                    return;
                }
                int value = Binary.stringToInt(tokens.get(1).getValue()); // KENV 1/6/05
                this.dataAddress.increment(value);
            }
        } else if (direct == Directives.EXTERN) {
            if (tokens.size() != 3) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "\"" + token.getValue()
                        + "\" directive requires two operands (label and size)."));
                return;
            }
            if (!TokenTypes.isIntegerTokenType(tokens.get(2).getType())
                    || Binary.stringToInt(tokens.get(2).getValue()) < 0) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "\"" + token.getValue()
                        + "\" requires a non-negative integer size"));
                return;
            }
            int size = Binary.stringToInt(tokens.get(2).getValue());
            // If label already in global symtab, do nothing. If not, add it right now.
            if (Globals.symbolTable.getAddress(tokens.get(1).getValue()) == SymbolTable.NOT_FOUND) {
                Globals.symbolTable.addSymbol(tokens.get(1), this.externAddress,
                        true, errors);
                this.externAddress += size;
            }
        } else if (direct == Directives.GLOBL) {
            if (tokens.size() < 2) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "\"" + token.getValue()
                        + "\" directive requires at least one argument."));
                return;
            }
            // SPIM limits .globl list to one label, why not extend it to a list?
            for (int i = 1; i < tokens.size(); i++) {
                // Add it to a list of labels to be processed at the end of the
                // pass. At that point, transfer matching symbol definitions from
                // local symbol table to global symbol table.
                Token label = tokens.get(i);
                if (label.getType() != TokenTypes.IDENTIFIER) {
                    errors.add(new ErrorMessage(token.getSourceProgram(),
                            token.getSourceLine(), token.getStartPos(), "\"" + token.getValue()
                            + "\" directive argument must be label."));
                    return;
                }
                globalDeclarationList.add(label);
            }
        } else {
            errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(), token
                    .getStartPos(), "\"" + token.getValue()
                    + "\" directive recognized but not yet implemented."));
        }
    } // executeDirective()

    // //////////////////////////////////////////////////////////////////////////////
    // Process the list of .globl labels, if any, declared and defined in this file.
    // We'll just move their symbol table entries from local symbol table to global
    // symbol table at the end of the first assembly pass.
    private void transferGlobals() {
        for (int i = 0; i < globalDeclarationList.size(); i++) {
            Token label = globalDeclarationList.get(i);
            Symbol symtabEntry = fileCurrentlyBeingAssembled.getLocalSymbolTable().getSymbol(
                    label.getValue());
            if (symtabEntry == null) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, label.getSourceLine(),
                        label.getStartPos(), "\"" + label.getValue()
                        + "\" declared global label but not defined."));
                // TODO: allow this case, but check later to see if all requested globals are actually implemented in other files
                // GCC outputs assembly that uses this
            } else {
                if (Globals.symbolTable.getAddress(label.getValue()) != SymbolTable.NOT_FOUND) {
                    errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, label.getSourceLine(),
                            label.getStartPos(), "\"" + label.getValue()
                            + "\" already defined as global in a different file."));
                } else {
                    fileCurrentlyBeingAssembled.getLocalSymbolTable().removeSymbol(label);
                    Globals.symbolTable.addSymbol(label, symtabEntry.getAddress(),
                            symtabEntry.getType(), errors);
                }
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////
    // This source code line, if syntactically correct, is a continuation of a
    // directive list begun on on previous line.
    private void executeDirectiveContinuation(TokenList tokens) {
        Directives direct = this.dataDirective;
        if (direct == Directives.WORD || direct == Directives.HALF || direct == Directives.BYTE
                || direct == Directives.FLOAT || direct == Directives.DOUBLE || direct == Directives.DWORD ) {
            if (tokens.size() > 0) {
                storeNumeric(tokens, direct, errors);
            }
        } else if (direct == Directives.ASCII || direct == Directives.ASCIZ || direct == Directives.STRING) {
            if (passesDataSegmentCheck(tokens.get(0))) {
                storeStrings(tokens, direct, errors);
            }
        }
    } // executeDirectiveContinuation()

    // //////////////////////////////////////////////////////////////////////////////////
    // Given token, find the corresponding Instruction object. If token was not
    // recognized as OPERATOR, there is a problem.
    private ArrayList<Instruction> matchInstruction(Token token) {
        if (token.getType() != TokenTypes.OPERATOR) {
            if (token.getSourceProgram().getLocalMacroPool()
                    .matchesAnyMacroName(token.getValue()))
                this.errors.add(new ErrorMessage(token.getSourceProgram(), token
                        .getSourceLine(), token.getStartPos(), "forward reference or invalid parameters for macro \""
                        + token.getValue() + "\""));
            else
                this.errors.add(new ErrorMessage(token.getSourceProgram(), token
                        .getSourceLine(), token.getStartPos(), "\"" + token.getValue()
                        + "\" is not a recognized operator"));
            return null;
        }
        ArrayList<Instruction> inst = Globals.instructionSet.matchOperator(token.getValue());
        if (inst == null) { // This should NEVER happen...
            this.errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                    token.getStartPos(), "Internal Assembler error: \"" + token.getValue()
                    + "\" tokenized OPERATOR then not recognized"));
        }
        return inst;
    } // matchInstruction()

    // //////////////////////////////////////////////////////////////////////////////////
    // Processes the .word/.half/.byte/.float/.double directive.
    // Can also handle "directive continuations", e.g. second or subsequent line
    // of a multiline list, which does not contain the directive token. Just pass the
    // current directive as argument.
    private void storeNumeric(TokenList tokens, Directives directive, ErrorList errors) {
        Token token = tokens.get(0);
        // A double-check; should have already been caught...removed ".word" exemption 11/20/06
        assert passesDataSegmentCheck(token);
        // Correctly handles case where this is a "directive continuation" line.
        int tokenStart = 0;
        if (token.getType() == TokenTypes.DIRECTIVE)
            tokenStart = 1;

        // Set byte length in memory of each number (e.g. WORD is 4, BYTE is 1, etc)
        int lengthInBytes = DataTypes.getLengthInBytes(directive);

        // Handle the "value : n" format, which replicates the value "n" times.
        if (tokens.size() == 4 && tokens.get(2).getType() == TokenTypes.COLON) {
            Token valueToken = tokens.get(1);
            Token repetitionsToken = tokens.get(3);
            // DPS 15-jul-08, allow ":" for repetition for all numeric
            // directives (originally just .word)
            // Conditions for correctly-formed replication:
            // (integer directive AND integer value OR floating directive AND
            // (integer value OR floating value))
            // AND integer repetition value
            if (!(Directives.isIntegerDirective(directive)
                    && TokenTypes.isIntegerTokenType(valueToken.getType()) || Directives
                    .isFloatingDirective(directive)
                    && (TokenTypes.isIntegerTokenType(valueToken.getType()) || TokenTypes
                    .isFloatingTokenType(valueToken.getType())))
                    || !TokenTypes.isIntegerTokenType(repetitionsToken.getType())) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled,
                        valueToken.getSourceLine(), valueToken.getStartPos(),
                        "malformed expression"));
                return;
            }
            int repetitions = Binary.stringToInt(repetitionsToken.getValue()); // KENV 1/6/05
            if (repetitions <= 0) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, repetitionsToken
                        .getSourceLine(), repetitionsToken.getStartPos(),
                        "repetition factor must be positive"));
                return;
            }
            if (this.inDataSegment) {
                if (this.autoAlign) {
                    this.dataAddress
                            .set(this.alignToBoundary(this.dataAddress.get(), lengthInBytes));
                }
                for (int i = 0; i < repetitions; i++) {
                    if (Directives.isIntegerDirective(directive)) {
                        storeInteger(valueToken, directive, errors);
                    } else {
                        storeRealNumber(valueToken, directive, errors);
                    }
                }
            }
            return;
        }

        // if not in ".word w : n" format, must just be list of one or more values.
        for (int i = tokenStart; i < tokens.size(); i++) {
            token = tokens.get(i);
            if (Directives.isIntegerDirective(directive)) {
                storeInteger(token, directive, errors);
            }
            if (Directives.isFloatingDirective(directive)) {
                storeRealNumber(token, directive, errors);
            }
        }
    } // storeNumeric()

    // //////////////////////////////////////////////////////////////////////////////
    // Store integer value given integer (word, half, byte) directive.
    // Called by storeNumeric()
    // NOTE: The token itself may be a label, in which case the correct action is
    // to store the address of that label (into however many bytes specified).
    private void storeInteger(Token token, Directives directive, ErrorList errors) {
        int lengthInBytes = DataTypes.getLengthInBytes(directive);
        if (TokenTypes.isIntegerTokenType(token.getType())) {
            int value;
            long longvalue;
            if (TokenTypes.INTEGER_64 == token.getType()) {
                longvalue = Binary.stringToLong(token.getValue());
                value = (int)longvalue;
                if (directive != Directives.DWORD){
                    errors.add(new ErrorMessage(ErrorMessage.WARNING, token.getSourceProgram(), token.getSourceLine(),
                            token.getStartPos(), "value " + Binary.longToHexString(longvalue)
                            + " is out-of-range and truncated to " + Binary.intToHexString(value)));
                }
            }else{
                value = Binary.stringToInt(token.getValue());
                longvalue = value;
            }

            if (directive == Directives.DWORD){
                writeToDataSegment((int)longvalue, 4, token, errors);
                writeToDataSegment((int)(longvalue>>32), 4, token, errors);
                return;
            }


            int fullvalue = value;
            // DPS 4-Jan-2013.  Overriding 6-Jan-2005 KENV changes.
            // If value is out of range for the directive, will simply truncate
            // the leading bits (includes sign bits). This is what SPIM does.
            // But will issue a warning (not error) which SPIM does not do.
            if (directive == Directives.BYTE) {
                value = value & 0x000000FF;
            } else if (directive == Directives.HALF) {
                value = value & 0x0000FFFF;
            }

            if (DataTypes.outOfRange(directive, fullvalue)) {
                errors.add(new ErrorMessage(ErrorMessage.WARNING, token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "value " + Binary.intToHexString(fullvalue)
                        + " is out-of-range and truncated to " + Binary.intToHexString(value)));
            }
            if (this.inDataSegment) {
                writeToDataSegment(value, lengthInBytes, token, errors);
            }
            /*
             * NOTE of 11/20/06. "try" below will always throw exception b/c you
             * cannot use Memory.set() with text segment addresses and the
             * "not valid address" produced here is misleading. Added data
             * segment check prior to this point, so this "else" will never be
             * executed. I'm leaving it in just in case MARS in the future adds
             * capability of writing to the text segment (e.g. ability to
             * de-assemble a binary value into its corresponding MIPS
             * instruction)
             */
            else {
                setData(this.textAddress.get(),value,lengthInBytes,new ErrorMessage(token.getSourceProgram(),
                        token.getSourceLine(), token.getStartPos(), "\""
                        + this.textAddress.get()
                        + "\" is not a valid text segment address"));
                this.textAddress.increment(lengthInBytes);
            }
        } // end of "if integer token type"
        else if (token.getType() == TokenTypes.IDENTIFIER) {
            if (this.inDataSegment) {
                int value = fileCurrentlyBeingAssembled.getLocalSymbolTable()
                        .getAddressLocalOrGlobal(token.getValue());
                if (value == SymbolTable.NOT_FOUND) {
                    // Record value 0 for now, then set up backpatch entry
                    int dataAddress = writeToDataSegment(0, lengthInBytes, token, errors);
                    currentFileDataSegmentForwardReferences.add(dataAddress, lengthInBytes, token);
                } else { // label already defined, so write its address
                    writeToDataSegment(value, lengthInBytes, token, errors);
                }
            } // Data segment check done previously, so this "else" will not be.
            // See 11/20/06 note above.
            else {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "\"" + token.getValue()
                        + "\" label as directive operand not permitted in text segment"));
            }
        } // end of "if label"
        else {
            errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(), token
                    .getStartPos(), "\"" + token.getValue()
                    + "\" is not a valid integer constant or label"));
        }
    }// storeInteger

    // //////////////////////////////////////////////////////////////////////////////
    // Store real (fixed or floating point) value given floating (float, double) directive.
    // Called by storeNumeric()
    private void storeRealNumber(Token token, Directives directive, ErrorList errors) {
        int lengthInBytes = DataTypes.getLengthInBytes(directive);
        double value;

        if (token.getValue().equals("Inf")) {
            value = Float.POSITIVE_INFINITY;
        } else if (token.getValue().equals("-Inf")) {
            value = Float.NEGATIVE_INFINITY;
        } else if (TokenTypes.isIntegerTokenType(token.getType())
                || TokenTypes.isFloatingTokenType(token.getType())) {

            try {
                value = Double.parseDouble(token.getValue());
            } catch (NumberFormatException nfe) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "\"" + token.getValue()
                        + "\" is not a valid floating point constant"));
                return;
            }
            if (DataTypes.outOfRange(directive, value)) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "\"" + token.getValue()
                        + "\" is an out-of-range value"));
                return;
            }
        } else {
            errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(), token
                    .getStartPos(), "\"" + token.getValue()
                    + "\" is not a valid floating point constant"));
            return;
        }

        // Value has been validated; let's store it.

        if (directive == Directives.FLOAT) {
            writeToDataSegment(Float.floatToIntBits((float) value), lengthInBytes, token, errors);
        }
        if (directive == Directives.DOUBLE) {
            writeDoubleToDataSegment(value, token, errors);
        }

    } // storeRealNumber

    // //////////////////////////////////////////////////////////////////////////////////
    // Use directive argument to distinguish between ASCII and ASCIZ. The
    // latter stores a terminating null byte. Can handle a list of one or more
    // strings on a single line.
    private void storeStrings(TokenList tokens, Directives direct, ErrorList errors) {
        Token token;
        // Correctly handles case where this is a "directive continuation" line.
        int tokenStart = 0;
        if (tokens.get(0).getType() == TokenTypes.DIRECTIVE) {
            tokenStart = 1;
        }
        for (int i = tokenStart; i < tokens.size(); i++) {
            token = tokens.get(i);
            if (token.getType() != TokenTypes.QUOTED_STRING) {
                errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(),
                        token.getStartPos(), "\"" + token.getValue()
                        + "\" is not a valid character string"));
            } else {
                String quote = token.getValue();
                char theChar;
                for (int j = 1; j < quote.length() - 1; j++) {
                    theChar = quote.charAt(j);
                    if (theChar == '\\') {
                        theChar = quote.charAt(++j);
                        switch (theChar) {
                            case 'n':
                                theChar = '\n';
                                break;
                            case 't':
                                theChar = '\t';
                                break;
                            case 'r':
                                theChar = '\r';
                                break;
                            case '\\':
                                theChar = '\\';
                                break;
                            case '\'':
                                theChar = '\'';
                                break;
                            case '"':
                                theChar = '"';
                                break;
                            case 'b':
                                theChar = '\b';
                                break;
                            case 'f':
                                theChar = '\f';
                                break;
                            case '0':
                                theChar = '\0';
                                break;
                            case 'u':
                                String codePoint = "";
                                try{
                                    codePoint = quote.substring(j+1, j+5); //get the UTF-8 codepoint following the unicode escape sequence
                                    theChar = Character.toChars(Integer.parseInt(codePoint, 16))[0]; //converts the codepoint to single character
                                } catch(StringIndexOutOfBoundsException e){
                                    String invalidCodePoint = quote.substring(j+1);
                                    errors.add(new ErrorMessage(token.getSourceProgram(), token
                                        .getSourceLine(), token.getStartPos(), "unicode escape \"\\u" +
                                            invalidCodePoint + "\" is incomplete. Only escapes with 4 digits are valid."));
                                } catch(NumberFormatException e){
                                    errors.add(new ErrorMessage(token.getSourceProgram(), token
                                            .getSourceLine(), token.getStartPos(), "illegal unicode escape: \"\\u" + codePoint + "\""));
                                }
                                j = j + 4; //skip past the codepoint for next iteration
                                break;

                            // Not implemented: \ n = octal character (n is number)
                            // \ x n = hex character (n is number)
                            // There are of course no spaces in these escape
                            // codes...
                        }
                    }
                    byte[] bytesOfChar = String.valueOf(theChar).getBytes(StandardCharsets.UTF_8);
                    ErrorMessage e = new ErrorMessage(token.getSourceProgram(), token
                            .getSourceLine(), token.getStartPos(), "\""
                            + this.dataAddress.get() + "\" is not a valid data segment address");
                    for (byte b : bytesOfChar) {
                        setData(this.dataAddress.get(),b,DataTypes.CHAR_SIZE, e);
                        this.dataAddress.increment(DataTypes.CHAR_SIZE);
                    }
                }
                if (direct == Directives.ASCIZ || direct == Directives.STRING) {
                    ErrorMessage e = new ErrorMessage(token.getSourceProgram(), token
                            .getSourceLine(), token.getStartPos(), "\""
                            + this.dataAddress.get() + "\" is not a valid data segment address");
                    setData(this.dataAddress.get(),0,DataTypes.CHAR_SIZE, e);
                    this.dataAddress.increment(DataTypes.CHAR_SIZE);
                }
            }
        }
    } // storeStrings()

    // //////////////////////////////////////////////////////////////////////////////////
    // Simply check to see if we are in data segment. Generate error if not.
    private boolean passesDataSegmentCheck(Token token) {
        if (!this.inDataSegment) {
            errors.add(new ErrorMessage(token.getSourceProgram(), token.getSourceLine(), token
                    .getStartPos(), "\"" + token.getValue()
                    + "\" directive cannot appear in text segment"));
            return false;
        } else {
            return true;
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////
    // Writes the given int value into current data segment address. Works for
    // all the integer types plus float (caller is responsible for doing floatToIntBits).
    // Returns address at which the value was stored.
    private int writeToDataSegment(int value, int lengthInBytes, Token token, ErrorList errors) {
        if (this.autoAlign) {
            this.dataAddress.set(this.alignToBoundary(this.dataAddress.get(), lengthInBytes));
        }
        ErrorMessage e = new ErrorMessage(token.getSourceProgram(), token
                .getSourceLine(), token.getStartPos(), "\""
                + this.dataAddress.get() + "\" is not a valid data segment address");
        setData(this.dataAddress.get(),value,lengthInBytes, e);
        int address = this.dataAddress.get();
        this.dataAddress.increment(lengthInBytes);
        return address;
    }

    // //////////////////////////////////////////////////////////////////////////////////
    // Writes the given double value into current data segment address. Works
    // only for DOUBLE floating
    // point values -- Memory class doesn't have method for writing 8 bytes, so
    // use setWord twice.
    private void writeDoubleToDataSegment(double value, Token token, ErrorList errors) {
        int lengthInBytes = DataTypes.DOUBLE_SIZE;
        if (this.autoAlign) {
            this.dataAddress.set(this.alignToBoundary(this.dataAddress.get(), lengthInBytes));
        }
        ErrorMessage e = new ErrorMessage(token.getSourceProgram(), token.getSourceLine(), token
                .getStartPos(), "\"" + this.dataAddress.get()
                + "\" is not a valid data segment address");
        long longValue = Double.doubleToLongBits(value);
        setData(this.dataAddress.get(),(int)longValue, DataTypes.WORD_SIZE,e);
        setData(this.dataAddress.get()+DataTypes.WORD_SIZE,(int)(longValue>>32), DataTypes.WORD_SIZE,e);
        this.dataAddress.increment(lengthInBytes);
    }

    // //////////////////////////////////////////////////////////////////////////////////
    // If address is multiple of byte boundary, returns address. Otherwise, returns address
    // which is next higher multiple of the byte boundary. Used for aligning data segment.
    // For instance if args are 6 and 4, returns 8 (next multiple of 4 higher than 6).
    // NOTE: it will fix any symbol table entries for this address too. See else part.
    private int alignToBoundary(int address, int byteBoundary) {
        int remainder = address % byteBoundary;
        if (remainder == 0) {
            return address;
        } else {
            int alignedAddress = address + byteBoundary - remainder;
            fileCurrentlyBeingAssembled.getLocalSymbolTable().fixSymbolTableAddress(address,
                    alignedAddress);
            return alignedAddress;
        }
    }

    // Private wrapper around an int; used to be more complicated
    // TODO: evaluate if it makes sense to keep this
    private class AddressSpace {
        int address;

        private AddressSpace(int userBase) {
            address = userBase;
        }

        private int get() {
            return address;
        }

        private void set(int value) {
            address = value;
        }

        private void increment(int increment) {
            address += increment;
        }
    }

    // //////////////////////////////////////////////////////////////////////////
    // Handy class to handle forward label references appearing as data
    // segment operands. This is needed because the data segment is comletely
    // processed by the end of the first assembly pass, and its directives may
    // contain labels as operands. When this occurs, the label's associated
    // address becomes the operand value. If it is a forward reference, we will
    // save the necessary information in this object for finding and patching in
    // the correct address at the end of the first pass (for this file or for all
    // files if more than one).
    //
    // If such a parsed label refers to a local or global label not defined yet,
    // pertinent information is added to this object:
    // - memory address that needs the label's address,
    // - number of bytes (addresses are 4 bytes but may be used with any of
    // the integer directives: .word, .half, .byte)
    // - the label's token. Normally need only the name but error message needs more.
    private class DataSegmentForwardReferences {
        private ArrayList<DataSegmentForwardReference> forwardReferenceList;

        private DataSegmentForwardReferences() {
            forwardReferenceList = new ArrayList<>();
        }

        private int size() {
            return forwardReferenceList.size();
        }

        // Add a new forward reference entry. Client must supply the following:
        // - memory address to receive the label's address once resolved
        // - number of address bytes to store (1 for .byte, 2 for .half, 4 for .word)
        // - the label's token. All its information will be needed if error message generated.
        private void add(int patchAddress, int length, Token token) {
            forwardReferenceList.add(new DataSegmentForwardReference(patchAddress, length, token));
        }

        // Add the entries of another DataSegmentForwardReferences object to this one.
        // Can be used at the end of each source file to dump all unresolved references
        // into a common list to be processed after all source files parsed.
        private void add(DataSegmentForwardReferences another) {
            forwardReferenceList.addAll(another.forwardReferenceList);
        }

        // Clear out the list. Allows you to re-use it.
        private void clear() {
            forwardReferenceList.clear();
        }

        // Will traverse the list of forward references, attempting to resolve them.
        // For each entry it will first search the provided local symbol table and
        // failing that, the global one. If passed the global symbol table, it will
        // perform a second, redundant, search. If search is successful, the patch
        // is applied and the forward reference removed. If search is not successful,
        // the forward reference remains (it is either undefined or a global label
        // defined in a file not yet parsed).
        private void resolve(SymbolTable localSymtab) {
            int labelAddress;
            DataSegmentForwardReference entry;
            for (int i = 0; i < forwardReferenceList.size(); i++) {
                entry = forwardReferenceList.get(i);
                labelAddress = localSymtab.getAddressLocalOrGlobal(entry.token.getValue());
                if (labelAddress != SymbolTable.NOT_FOUND) {
                    // patch address has to be valid b/c we already stored there...
                    setData(entry.patchAddress,labelAddress,entry.length, null);
                    forwardReferenceList.remove(i);
                    i--; // needed because removal shifted the remaining list indices down
                }
            }
        }

        // Call this when you are confident that remaining list entries are to
        // undefined labels.
        private void generateErrorMessages(ErrorList errors) {
            for (DataSegmentForwardReference entry : forwardReferenceList) {
                errors.add(new ErrorMessage(entry.token.getSourceProgram(), entry.token
                        .getSourceLine(), entry.token.getStartPos(), "Symbol \""
                        + entry.token.getValue() + "\" not found in symbol table."));
            }
        }

        // inner-inner class to hold each entry of the forward reference list.
        private class DataSegmentForwardReference {
            int patchAddress;
            int length;
            Token token;

            DataSegmentForwardReference(int patchAddress, int length, Token token) {
                this.patchAddress = patchAddress;
                this.length = length;
                this.token = token;
            }
        }

    }
}
