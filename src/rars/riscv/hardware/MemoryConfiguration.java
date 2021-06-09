package rars.riscv.hardware;

/*
Copyright (c) 2003-2009,  Pete Sanderson and Kenneth Vollmar

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

import rars.util.Binary;

import java.util.HashMap;
import java.util.Map;

/**
 * Models the memory configuration for the simulated MIPS machine.
 * "configuration" refers to the starting memory addresses for
 * the various memory segments.
 * The default configuration is based on SPIM.  Starting with MARS 3.7,
 * the configuration can be changed.
 *
 * @author Pete Sanderson
 * @version August 2009
 */


public class MemoryConfiguration {
    // Identifier is used for saving setting; name is used for display
    private String configurationIdentifier, configurationName;

    public final Range text, data, heap, stack, mmio, total;
    public final Map<String, Range> sections;
    public final int gp_offset, extern_size;
    public final boolean builtin;

    public MemoryConfiguration(String ident, String name, Map<String, Range> sections, int gp_offset, int extern_size){
        this(ident, name, sections, gp_offset,extern_size,true);
    }
    public MemoryConfiguration(String ident, String name, Map<String, Range> sections, int gp_offset, int extern_size, boolean builtin){
        this.configurationIdentifier = ident;
        this.configurationName = name;
        this.builtin = builtin;
        text  = sections.get(".text");
        data  = sections.get(".data");
        heap = sections.get("heap");
        stack = sections.get("stack");
        mmio  = sections.get("mmio");
        total = sections.values().stream().reduce(text, Range::combine);
        this.sections = new HashMap<>();
        this.sections.putAll(sections);
        this.gp_offset = gp_offset;
        this.extern_size = extern_size;
    }

    public String getConfigurationIdentifier() {
        return configurationIdentifier;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public int getTextBaseAddress() {
        return text.low;
    }

    public int getDataSegmentBaseAddress() {
        return data.low;
    }

    public int getExternBaseAddress() {
        return data.low;
    }

    public int getGlobalPointer() {
        return data.low+gp_offset;
    }

    public int getDataBaseAddress() {
        return data.low+extern_size;
    }

    public int getHeapBaseAddress() {
        return heap.low;
    }

    public int getStackBaseAddress() {
        return stack.high;
    }

    public int getMemoryMapBaseAddress() {
        return mmio.low;
    }

    public int getDataSegmentLimitAddress() {
        return heap.high;
    }

    public int getTextLimitAddress() {
        return text.high;
    }

    public String toPropertiesString(){
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String,Range> entry : sections.entrySet()){
            sb.append(entry.getKey()).append(" = ");
            sb.append(Binary.intToHexString(entry.getValue().low)).append('-');
            sb.append(Binary.intToHexString(entry.getValue().high)).append('\n');
        }
        sb.append("name = ").append(configurationName).append('\n');
        sb.append("ident = ").append(configurationIdentifier).append('\n');
        sb.append("gp_offset = ").append(Binary.intToHexString(gp_offset)).append('\n');
        sb.append("extern_offset = ").append(Binary.intToHexString(extern_size)).append('\n');
        return sb.toString();
    }
}