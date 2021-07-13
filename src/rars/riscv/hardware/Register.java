package rars.riscv.hardware;

import java.util.Observable;

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
 * Abstraction to represent a register of a MIPS Assembler.
 *
 * @author Jason Bumgarner, Jason Shrewsbury, Ben Sherman
 * @version June 2003
 **/

public class Register extends Observable {
    private String name;
    private int number;
    private long resetValue;
    private int hart;
    // volatile should be enough to allow safe multi-threaded access
    // w/o the use of synchronized methods.  getValue and setValue
    // are the only methods here used by the register collection
    // (RegisterFile, ControlAndStatusRegisterFile, FloatingPointRegisterFile) methods.
    private volatile long value;

    /**
     * Creates a new register with specified name, number, and value.
     *
     * @param n   The name of the register.
     * @param num The number of the register.
     * @param val The inital (and reset) value of the register.
     */

    public Register(String n, int num, long val) {
        name = n;
        number = num;
        value = val;
        resetValue = val;
        hart = -1;
    }
    public Register(String n, int num, long val, int hart) {
        name = n;
        number = num;
        value = val;
        resetValue = val;
        this.hart = hart;
    }
    /**
     * Returns the name of the Register.
     *
     * @return name The name of the Register.
     */

    public String getName() {
        return name;
    }

    /**
     * Returns the value of the Register.  Observers are notified
     * of the READ operation.
     *
     * @return value The value of the Register.
     */

    public synchronized long getValue() {
        notifyAnyObservers(AccessNotice.READ);
        return value;
    }


    /**
     * Returns the value of the Register.  Observers are not notified.
     * Added for release 3.8.
     *
     * @return value The value of the Register.
     */

    public synchronized long getValueNoNotify() {
        return value;
    }


    /**
     * Returns the reset value of the Register.
     *
     * @return The reset (initial) value of the Register.
     */

    public long getResetValue() {
        return resetValue;
    }

    /**
     * Returns the number of the Register.
     *
     * @return number The number of the Register.
     */

    public int getNumber() {
        return number;
    }

    /**
     * Sets the value of the register to the val passed to it.
     * Observers are notified of the WRITE operation.
     *
     * @param val Value to set the Register to.
     * @return previous value of register
     */

    public synchronized long setValue(long val) {
        long old = value;
        value = val;
        notifyAnyObservers(AccessNotice.WRITE);
        return old;
    }

    /**
     * Sets the value of the register to the val passed to it. This should only
     * be used to update registers not related to the current instruction.
     *
     * @param val Value to set the Register to.
     * @return previous value of register
     */

    public synchronized long setValueBackdoor(long val) {
        long old = value;
        value = val;
        return old;
    }


    /**
     * Resets the value of the register to the value it was constructed with.
     * Observers are not notified.
     */

    public synchronized void resetValue() {
        value = resetValue;
    }

    /**
     * Change the register's reset value; the value to which it will be
     * set when <tt>resetValue()</tt> is called.
     */

    public synchronized void changeResetValue(long reset) {
        resetValue = reset;
    }

    //
    // Method to notify any observers of register operation that has just occurred.
    //
    private void notifyAnyObservers(int type) {
        if (this.countObservers() > 0) {// && Globals.program != null) && Globals.program.inSteppedExecution()) {
            this.setChanged();
            if(this.hart == -1)
                this.notifyObservers(new RegisterAccessNotice(type, this.name));
            else
                this.notifyObservers(new RegisterAccessNotice(type, this.name, hart));
        }
    }


}
