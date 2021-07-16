package rars.riscv;

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
 * These are seven RISCV-defined formats of basic machine instructions:
 * R, R4, I, S, B, U, and J. Examples of each respectively would be add, fmadd, addi, sw, beq, lui, and jal.
 *
 * @author Benjamin Landers
 * @version April 2019
 */
public enum BasicInstructionFormat {
    R_FORMAT, // 3 register instructions
    R4_FORMAT,// 4 registers instructions
    I_FORMAT, // 1 dst and 1 src register + small immediate
    S_FORMAT, // 2 src registers + small immediate
    B_FORMAT, // 2 src registers + small immediate shifted left
    U_FORMAT, // 1 dst register  + large immediate
    J_FORMAT, // 1 dst register  + large immediate for jumping
}
