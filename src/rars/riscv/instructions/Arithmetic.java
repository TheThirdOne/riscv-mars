package rars.riscv.instructions;

import rars.ProgramStatement;
import rars.riscv.InstructionSet;
import rars.riscv.hardware.RegisterFile;
import rars.riscv.BasicInstruction;
import rars.riscv.BasicInstructionFormat;

/*
Copyright (c) 2017,  Benjamin Landers

Developed by Benjamin Landers (benjaminrlanders@gmail.com)

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
 * Base class for all integer instructions using immediates
 *
 * @author Benjamin Landers
 * @version June 2017
 */
public abstract class Arithmetic extends BasicInstruction {

    public Arithmetic(String usage, String description, String funct7, String funct3) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                funct7 + " ttttt sssss " + funct3 + " fffff 0110011");
    }
    public Arithmetic(String usage, String description, String funct7, String funct3, boolean rv64){
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                funct7 + " ttttt sssss " + funct3 + " fffff 0111011",rv64);
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        if (InstructionSet.rv64){
            if(statement.getCurrentHart() == -1)
                RegisterFile.updateRegister(operands[0], compute(RegisterFile.getValueLong(operands[1]),RegisterFile.getValueLong(operands[2])));
            else{
                RegisterFile.updateRegister(operands[0], compute(RegisterFile.getValueLong(operands[1]),RegisterFile.getValueLong(operands[2])), statement.getCurrentHart());
            }
        }
        else {
            
            if(statement.getCurrentHart() == -1)
                RegisterFile.updateRegister(operands[0], computeW(RegisterFile.getValue(operands[1]),RegisterFile.getValue(operands[2])));
            else{
                RegisterFile.updateRegister(operands[0], computeW(RegisterFile.getValue(operands[1]),RegisterFile.getValue(operands[2])), statement.getCurrentHart());
            }
        }
    }

    /**
     * @param value  the value from the first register
     * @param value2 the value from the second register
     * @return the result to be stored from the instruction
     */
    protected abstract long compute(long value, long value2);

    /**
     * A version for rv32 / W instructions in rv64, override if the default behaviour is not correct
     * @param value  the value from the first register truncated to 32 bits
     * @param value2 the value from the second register truncated to 32 bits
     * @return the result to be stored from the instruction
     */
    protected int computeW(int value, int value2){
        return (int) compute(value,value2);
    }
}