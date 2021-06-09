package rars.riscv.hardware;

import rars.Globals;
import rars.Settings;

import java.util.Observer;

/*
Copyright (c) 2017-2019,  Benjamin Landers

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
 * Represents the implemented control and status registers. The main classes are fcsr (for floating point errors),
 * timers, and interrupt handling.
 *
 * @author Benjamin Landers
 * @version August 2017
 **/

public class ControlAndStatusRegisterFile {
    public static final int EXTERNAL_INTERRUPT = 0x100;
    public static final int TIMER_INTERRUPT = 0x10;
    public static final int SOFTWARE_INTERRUPT = 0x1;


    public static final int INTERRUPT_ENABLE = 0x1;
    
    private static final RegisterBlock instance;

    static {
        // TODO: consider making time, cycle and instret 64 bit registers which then are linked to by *h
        // Remember to update the window tooltips when adding a CSR
        Register[] tmp = {
                new MaskedRegister("ustatus", 0x000, 0,~0x11),
                null, // fflags
                null, // frm
                new MaskedRegister("fcsr", 0x003, 0, ~0xFF),
                new Register("uie", 0x004, 0),
                new Register("utvec", 0x005, 0),
                new Register("uscratch", 0x040, 0),
                new Register("uepc", 0x041, 0),
                new Register("ucause", 0x042, 0),
                new Register("utval", 0x043, 0),
                new Register("uip", 0x044, 0),
                new ReadOnlyRegister("cycle", 0xC00, 0),
                new ReadOnlyRegister("time", 0xC01, 0),
                new ReadOnlyRegister("instret",0xC02, 0),
                null, // cycleh
                null, // timeh
                null, // instreth
                new ReadOnlyRegister("medeleg", 0x302, 0),
        };
        tmp[1] = new LinkedRegister("fflags", 0x001, tmp[3], 0x1F);
        tmp[2] = new LinkedRegister("frm", 0x002, tmp[3], 0xE0);

        tmp[14] = new LinkedRegister("cycleh", 0xC80,tmp[11], 0xFFFFFFFF_00000000L);
        tmp[15] = new LinkedRegister("timeh", 0xC81, tmp[12],0xFFFFFFFF_00000000L);
        tmp[16] = new LinkedRegister("instreth",0xC82, tmp[13],0xFFFFFFFF_00000000L);
        instance = new RegisterBlock('_', tmp); // prefix not used
    }

    /**
     * This method updates the register value
     *
     * @param num Number of register to set the value of.
     * @param val The desired value for the register.
     * @return old value in register prior to update
     **/
    public static boolean updateRegister(int num, long val) {
        if (instance.getRegister(num) instanceof ReadOnlyRegister) {
            return true;
        }
        // TODO: do something to better handle the h csrs
        if (num >= 0xC80 && num <= 0xC82) {
            return true;
        }
        if ((Globals.getSettings().getBackSteppingEnabled())) {
            Globals.program.getBackStepper().addControlAndStatusRestore(num, instance.updateRegister(num, val));
        } else {
            instance.updateRegister(num, val);
        }
        return false;
    }

    /**
     * This method updates the register value
     *
     * @param name Name of register to set the value of.
     * @param val  The desired value for the register.
     * @return old value in register prior to update
     **/
    public static void updateRegister(String name, long val) {
        updateRegister(instance.getRegister(name).getNumber(), val);
    }

    /**
     * This method updates the register value silently and bypasses read only
     *
     * @param num Number of register to set the value of.
     * @param val  The desired value for the register.
     * @return old value in register prior to update
     **/
    public static void updateRegisterBackdoor(int num, long val) {
        if ((Globals.getSettings().getBackSteppingEnabled())) {
            Globals.program.getBackStepper().addControlAndStatusBackdoor(num, instance.getRegister(num).setValueBackdoor(val));
        } else {
            instance.getRegister(num).setValueBackdoor(val);
        }
    }

    /**
     * This method updates the register value silently and bypasses read only
     *
     * @param name Name of register to set the value of.
     * @param val  The desired value for the register.
     * @return old value in register prior to update
     **/
    public static void updateRegisterBackdoor(String name, long val) {
        updateRegisterBackdoor(instance.getRegister(name).getNumber(), val);
    }

    /**
     * ORs a register with a value
     *
     * @param num Number of register to change
     * @param val The value to OR with
     **/
    public static boolean orRegister(int num, long val) {
        return updateRegister(num, instance.getValue(num) | val);
    }

    /**
     * ORs a register with a value
     *
     * @param name Name of register to change
     * @param val  The value to OR with
     **/
    public static void orRegister(String name, long val) {
        updateRegister(name, instance.getValue(name) | val);
    }

    /**
     * Clears bits from a register according to a value
     *
     * @param num Number of register to change
     * @param val The value to clear by
     **/
    public static boolean clearRegister(int num, long val) {
        return updateRegister(num, instance.getValue(num) & ~val);
    }

    /**
     * Clears bits from a register according to a value
     *
     * @param name Name of register to change
     * @param val  The value to clear by
     **/
    public static void clearRegister(String name, long val) {
        updateRegister(name, instance.getValue(name) & ~val);
    }

    /**
     * Returns the value of the register
     *
     * @param num The register number.
     * @return The value of the given register.  0 for non-implemented registers
     **/

    public static int getValue(int num) {
        return (int)instance.getValue(num);
    }

    /**
     * Returns the full value of the register
     *
     * @param num The register number.
     * @return The value of the given register.  0 for non-implemented registers
     **/

    public static long getValueLong(int num) {
        return instance.getValue(num);
    }
    /**
     * Returns the value of the register
     *
     * @param name The register's name
     * @return The value of the given register.  0 for non-implemented registers
     **/

    public static int getValue(String name) {
        return (int)instance.getValue(name);
    }

    /**
     * Returns the value of the register without notifying observers
     *
     * @param name The register's name
     * @return The value of the given register.  0 for non-implemented registers
     **/

    public static long getValueNoNotify(String name) {
        return instance.getRegister(name).getValueNoNotify();
    }

    /**
     * For returning the set of registers.
     *
     * @return The set of registers.
     **/

    public static Register[] getRegisters() {
        return instance.getRegisters();
    }


    /**
     * ControlAndStatusRegisterFile implements a wide range of register numbers that don't math the position in the underlying array
     *
     * @param r the CSR
     * @return the list position of given register, -1 if not found.
     **/

    public static int getRegisterPosition(Register r) {
        Register[] registers = instance.getRegisters();
        for (int i = 0; i < registers.length; i++) {
            if (registers[i] == r) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Method to reinitialize the values of the registers.
     **/

    public static void resetRegisters() {
        instance.resetRegisters();

        if (Globals.getSettings().getBooleanSetting(Settings.Bool.DELEGATE_EXCEPTIONS)) {
            updateRegisterBackdoor("medeleg", 0xffff);
        }
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method
     * will add the given Observer to each one.
     */
    public static void addRegistersObserver(Observer observer) {
        instance.addRegistersObserver(observer);
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method
     * will delete the given Observer from each one.
     */
    public static void deleteRegistersObserver(Observer observer) {
        instance.deleteRegistersObserver(observer);
    }

}
