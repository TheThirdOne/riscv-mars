package rars.riscv.instructions;

import rars.Globals;
import rars.riscv.hardware.AddressErrorException;
import rars.ProgramStatement;
import rars.SimulationException;
import rars.riscv.hardware.RegisterFile;
import rars.riscv.hardware.ReservationTable.bitWidth;

public class SCW extends Atomic {
    public SCW() {
        super("sc.w t0, t1, (t2)", "Try to store t1 to contents of effective memory word address, sets t0 to store attempt result", "010", "00011");
    }

    public void simulate(ProgramStatement statement) throws SimulationException {
        int[] operands = statement.getOperands();
        try {
            int result = store(RegisterFile.getValue(operands[2]), RegisterFile.getValue(operands[1]));
            RegisterFile.updateRegister(operands[0], result);
        } catch (AddressErrorException e) {
            throw new SimulationException(statement, e);
        }
    }

    private int store(int address, int value) throws AddressErrorException {
        if (Globals.reservationTables.unreserveAddress(0, address, bitWidth.word)) {
            Globals.memory.setWord(address, value);
            return 0;
        }
        return 1;
    }
}
