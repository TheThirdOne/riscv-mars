package rars.riscv.instructions;

import rars.Globals;
import rars.ProgramStatement;
import rars.SimulationException;
import rars.riscv.InstructionSet;
import rars.riscv.hardware.AddressErrorException;
import rars.riscv.hardware.RegisterFile;
import rars.riscv.hardware.ReservationTable.bitWidth;

/*
Copyright (c) 2021, Giancarlo Pernudi Segura

Developed by Giancarlo Pernudi Segura at the University of Alberta (pernudi@ualberta.ca)

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
 * Base class for all Atomic instructions
 *
 * @author Giancarlo Pernudi Segura
 * @version May 2017
 */
public abstract class AtomicMemoryOperation extends Atomic {
    private bitWidth width;

    public AtomicMemoryOperation(String usage, String description, String funct5) {
        super(usage, description, "010", funct5);
        width = bitWidth.word;
    }

    public AtomicMemoryOperation(String usage, String description, String funct5, boolean rv64) {
        super(usage, description, rv64 ? "011" : "010", funct5, rv64);
        width = rv64 ? bitWidth.doubleWord : bitWidth.word;
    }

    public void simulate(ProgramStatement statement) throws SimulationException {
        int[] operands = statement.getOperands();
        try {
            int rs1Loc = RegisterFile.getValue(operands[2]);
            Globals.reservationTables.unreserveAddress(0, rs1Loc, width);
            long rs1Data = InstructionSet.rv64 ? Globals.memory.getDoubleWord(rs1Loc) : Globals.memory.getWord(rs1Loc);
            long rs2Value = RegisterFile.getValueLong(operands[1]);
            RegisterFile.updateRegister(operands[0], rs1Data);
            rs1Data = binaryOperation(rs1Data, rs2Value);
            if (InstructionSet.rv64) {
                Globals.memory.setDoubleWord(rs1Loc, rs1Data);
            } else {
                Globals.memory.setWord(rs1Loc, (int) rs1Data);
            }
        } catch (AddressErrorException e) {
            throw new SimulationException(statement, e);
        }
    }

    protected abstract long binaryOperation(long value1, long value2);
}
