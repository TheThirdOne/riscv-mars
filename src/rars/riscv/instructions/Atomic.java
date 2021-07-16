package rars.riscv.instructions;

import rars.riscv.BasicInstruction;
import rars.riscv.BasicInstructionFormat;

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
public abstract class Atomic extends BasicInstruction {
    public Atomic(String usage, String description, String funct3, String funct5) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                funct5 + " 00sssss ttttt " + funct3 + " fffff 0101111");
    }

    public Atomic(String usage, String description, String funct3, String funct5, boolean rv64) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                funct5 + " 00sssss ttttt " + funct3 + " fffff 0101111", rv64);
    }
}
