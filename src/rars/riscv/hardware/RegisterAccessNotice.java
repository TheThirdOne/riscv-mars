package rars.riscv.hardware;

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
 * Object provided to Observers of runtime access to register.
 * Observer can get the access type (R/W) and register number.
 *
 * @author Pete Sanderson
 * @version July 2005
 */

public class RegisterAccessNotice extends AccessNotice {
    private String registerName;
    private int hart;
    /**
     * Constructor will be called only within this package, so assume
     * register number is in valid range.
     */
    RegisterAccessNotice(int type, String registerName, int hart) {
        super(type);
        this.registerName = registerName;
        this.hart = hart;
    }
    RegisterAccessNotice(int type, String registerName) {
        super(type);
        this.registerName = registerName;
        hart = -1;
    }
    /**
     * Fetch the register number of register accessed.
     */
    public String getRegisterName() {
        return registerName;
    }
    public int getHart(){
        return hart;
    }
    /**
     * String representation indicates access type and which register
     */
    public String toString() {
        return ((this.getAccessType() == AccessNotice.READ) ? "R " : "W ") +
                "Reg " + registerName;
    }

}