package rars.riscv.hardware;

import rars.Globals;
import rars.util.Binary;
import java.io.InputStream;
import java.util.*;

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

/**
 * Models the collection of MIPS memory configurations.
 * The default configuration is based on SPIM.  Starting with MARS 3.7,
 * the configuration can be changed.
 *
 * @author Pete Sanderson
 * @version August 2009
 */


public class MemoryConfigurations {

    private static ArrayList<MemoryConfiguration> configurations = null;
    private static MemoryConfiguration defaultConfiguration;
    private static MemoryConfiguration currentConfiguration;

    public MemoryConfigurations() {

    }

    public static void buildConfigurationCollection() {
        if (configurations == null) {
            configurations = new ArrayList<>();
            HashMap<String, Range> sections = new HashMap<>();
            // Default configuration comes from SPIM
            sections.put(".text", new Range(  0x400000,0x10000000));
            sections.put(".data", new Range(0x10000000,0x10040000));
            sections.put("heap",  new Range(0x10040000,0x30000000));
            sections.put("stack", new Range(0x60000000,0x80000000));
            sections.put("mmio",  new Range(0xffff0000,0xffffffff));
            configurations.add(new MemoryConfiguration("Default","Default", sections,0x8000,0x10000));

            sections = new HashMap<>();
            // Compact allows 16 bit addressing, data segment starts at 0
            sections.put(".text", new Range(0x3000,0x4000));
            sections.put(".data", new Range(0x0000,0x2000));
            sections.put("heap",  new Range(0x2000,0x2800)); //Heap and stack split in half (ideally they should overlap)
            sections.put("stack", new Range(0x2800,0x3000));
            sections.put("mmio",  new Range(0x7f00,0x8000));
            configurations.add(new MemoryConfiguration("CompactDataAtZero", "Compact, Data at Address 0", sections,0x1800,0x1000));

            sections = new HashMap<>();
            // Compact allows 16 bit addressing, text segment starts at 0
            sections.put(".text", new Range(0x0000,0x1000));
            sections.put(".data", new Range(0x1000,0x3000));
            sections.put("heap",  new Range(0x3000,0x3800)); //Heap and stack split in half (ideally they should overlap)
            sections.put("stack", new Range(0x3800,0x4000));
            sections.put("mmio",  new Range(0x7f00,0x8000));
            configurations.add(new MemoryConfiguration("CompactTextAtZero", "Compact, Text at Address 0", sections,0x800,0x1000));

            defaultConfiguration = configurations.get(0);
            currentConfiguration = defaultConfiguration;
        }
    }

    public static Iterator<MemoryConfiguration> getConfigurationsIterator() {
        if (configurations == null) {
            buildConfigurationCollection();
        }
        return configurations.iterator();

    }

    public static void addNewConfig(MemoryConfiguration mc){
        configurations.add(mc);
    }
    public static MemoryConfiguration loadNewConfig(InputStream is) {
        Properties f = new Properties();
        try {
            f.load(is);
        } catch(Exception e){
          return null;
        }
        Map<String,Range> ranges = new HashMap<>();
        for(String name : f.stringPropertyNames()) {
            String value = f.getProperty(name);
            if (!value.contains("-")) continue;
            String[] ends = value.split("-");
            if (ends.length != 2) continue;
            Integer first = Binary.stringToIntFast(ends[0]), second = Binary.stringToIntFast(ends[1]);
            if (first == null || second == null) continue;
            if (Integer.compareUnsigned(first, second) <= 0) {
                ranges.put(name, new Range(first, second));
            } else {
                ranges.put(name, new Range(second, first));
            }
        }

        String gp = f.getProperty("gp_offset"), extern = f.getProperty("extern_offset");
        if(gp == null || extern == null) return null;


        Integer gpint = Binary.stringToIntFast(gp), externint  = Binary.stringToIntFast(extern);
        if(gpint == null || externint == null) return null;

        String ident = f.getProperty("ident"), name = f.getProperty("name");
        if(ident == null || name == null) return null;
        return new MemoryConfiguration(ident,name,ranges,gpint,externint,false);
    }

    public static MemoryConfiguration getConfigurationByName(String name) {
        Iterator<MemoryConfiguration> configurationsIterator = getConfigurationsIterator();
        while (configurationsIterator.hasNext()) {
            MemoryConfiguration config = configurationsIterator.next();
            if (name.equals(config.getConfigurationIdentifier())) {
                return config;
            }
        }
        return null;
    }


    public static MemoryConfiguration getDefaultConfiguration() {
        if (defaultConfiguration == null) {
            buildConfigurationCollection();
        }
        return defaultConfiguration;
    }

    public static MemoryConfiguration getCurrentConfiguration() {
        if (currentConfiguration == null) {
            buildConfigurationCollection();
        }
        return currentConfiguration;
    }

    public static boolean setCurrentConfiguration(MemoryConfiguration config) {
        if (config == null)
            return false;
        if (config != currentConfiguration) {
            currentConfiguration = config;
            Globals.memory.clear();
            RegisterFile.getRegister("gp").changeResetValue(config.getGlobalPointer());
            RegisterFile.getRegister("sp").changeResetValue(config.getStackBaseAddress());
            RegisterFile.getProgramCounterRegister().changeResetValue(config.getTextBaseAddress());
            RegisterFile.initializeProgramCounter(config.getTextBaseAddress());
            RegisterFile.resetRegisters();
            return true;
        } else {
            return false;
        }
    }
}