package rars.simulator;

import rars.Globals;
import rars.riscv.hardware.AddressErrorException;
import rars.riscv.hardware.Memory;
import rars.riscv.hardware.RegisterFile;

import java.util.ArrayList;
import java.util.StringTokenizer;

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
 * Models Program Arguments, one or more strings provided to the source
 * program at runtime. Equivalent to C's main(int argc, char **argv) or
 * Java's main(String[] args).
 *
 * @author Pete Sanderson
 * @version July 2008
 **/

public class ProgramArgumentList {

    private ArrayList<String> programArgumentList;

    /**
     * Constructor that parses string to produce list.  Delimiters
     * are the default Java StringTokenizer delimiters (space, tab,
     * newline, return, formfeed)
     *
     * @param args String containing delimiter-separated arguments
     */
    public ProgramArgumentList(String args) {
        StringTokenizer st = new StringTokenizer(args);
        programArgumentList = new ArrayList<>(st.countTokens());
        while (st.hasMoreTokens()) {
            programArgumentList.add(st.nextToken());
        }
    }

    /**
     * Constructor that gets list from String array, one argument per element.
     *
     * @param list Array of String, each element containing one argument
     */
    public ProgramArgumentList(String[] list) {
        this(list, 0);
    }

    /**
     * Constructor that gets list from section of String array, one
     * argument per element.
     *
     * @param list          Array of String, each element containing one argument
     * @param startPosition Index of array element containing the first argument; all remaining
     *                      elements are assumed to contain an argument.
     */
    public ProgramArgumentList(String[] list, int startPosition) {
        programArgumentList = new ArrayList<>(list.length - startPosition);
        for (int i = startPosition; i < list.length; i++) {
            programArgumentList.add(list[i]);
        }
    }

    /**
     * Constructor that gets list from ArrayList of String, one argument per element.
     *
     * @param list ArrayList of String, each element containing one argument
     */
    public ProgramArgumentList(ArrayList<String> list) {
        this(list, 0);
    }


    /**
     * Constructor that gets list from section of String ArrayList, one
     * argument per element.
     *
     * @param list          ArrayList of String, each element containing one argument
     * @param startPosition Index of array element containing the first argument; all remaining
     *                      elements are assumed to contain an argument.
     */
    public ProgramArgumentList(ArrayList<String> list, int startPosition) {
        if (list == null || list.size() < startPosition) {
            programArgumentList = new ArrayList<>(0);
        } else {
            programArgumentList = new ArrayList<>(list.size() - startPosition);
            for (int i = startPosition; i < list.size(); i++) {
                programArgumentList.add(list.get(i));
            }
        }
    }


    //////////////////////////////////////////////////////////////////////
    // Place any program arguments into memory and registers
    // Arguments are stored starting at highest word of non-kernel
    // memory and working back toward runtime stack (there is a 4096
    // byte gap in between).  The argument count (argc) and pointers
    // to the arguments are stored on the runtime stack.  The stack
    // pointer register $sp is adjusted accordingly and $a0 is set
    // to the argument count (argc), and $a1 is set to the stack
    // address holding the first argument pointer (argv).
    public void storeProgramArguments() {
        if (programArgumentList == null || programArgumentList.size() == 0) {
            // If there is no input just make argc = 0 and argv = {NULL} by allocating two words on the stack
            RegisterFile.getRegister("sp").setValue(Memory.configuration.getStackBaseAddress()-8);
            return;
        }
        // Runtime stack initialization from stack top-down (each is 4 bytes) :
        //    programArgumentList.size()
        //    address of first character of first program argument
        //    address of first character of second program argument
        //    ....repeat for all program arguments
        //    0x00000000    (null terminator for list of string pointers)
        // $sp will be set to the address holding the arg list size
        // $a0 will be set to the arg list size (argc)
        // $a1 will be set to stack address just "below" arg list size (argv)
        //
        // Each of the arguments themselves will be stored starting at
        // Memory.stackBaseAddress (0x7ffffffc) and working down from there:
        // 0x7ffffffc will contain null terminator for first arg
        // 0x7ffffffb will contain last character of first arg
        // 0x7ffffffa will contain next-to-last character of first arg
        // Etc down to first character of first arg.
        // Previous address will contain null terminator for second arg
        // Previous-to-that contains last character of second arg
        // Etc down to first character of second arg.
        // Follow this pattern for all remaining arguments.


        int highAddress = Memory.configuration.getStackBaseAddress()-1;  // first writeable byte in the stack
        String programArgument;
        int[] argStartAddress = new int[programArgumentList.size()];
        try { // needed for all memory writes
            for (int i = 0; i < programArgumentList.size(); i++) {
                programArgument = programArgumentList.get(i);
                Globals.memory.set(highAddress, 0, 1);  // trailing null byte for each argument
                highAddress--;
                for (int j = programArgument.length() - 1; j >= 0; j--) {
                    Globals.memory.set(highAddress, programArgument.charAt(j), 1);
                    highAddress--;
                }
                argStartAddress[i] = highAddress + 1;
            }

            // Get on a word boundary to start writing argv
            int stackAddress = highAddress - (highAddress % Memory.WORD_LENGTH_BYTES) - Memory.WORD_LENGTH_BYTES;
            
            // now place a null word, the arg starting addresses, and arg count onto stack.
            Globals.memory.set(stackAddress, 0, Memory.WORD_LENGTH_BYTES);  // null word for end of argv array
            stackAddress -= Memory.WORD_LENGTH_BYTES;
            for (int i = argStartAddress.length - 1; i >= 0; i--) {
                Globals.memory.set(stackAddress, argStartAddress[i], Memory.WORD_LENGTH_BYTES);
                stackAddress -= Memory.WORD_LENGTH_BYTES;
            }
            Globals.memory.set(stackAddress, argStartAddress.length, Memory.WORD_LENGTH_BYTES); // argc

            // Need to set $sp register to stack address, $a0 to argc, $a1 to argv
            // Need to by-pass the backstepping mechanism so go directly to Register instead of RegisterFile
            RegisterFile.getRegister("sp").setValue(stackAddress);
            RegisterFile.getRegister("a0").setValue(argStartAddress.length); // argc
            RegisterFile.getRegister("a1").setValue(stackAddress + Memory.WORD_LENGTH_BYTES); // argv
        } catch (AddressErrorException aee) {
            System.out.println("Internal Error: Memory write error occurred while storing program arguments! " + aee);
            System.exit(0);
        }
    }


}
