package rars.riscv.hardware;

import rars.Globals;
import rars.ProgramStatement;
import rars.Settings;
import rars.SimulationException;
import rars.riscv.Instruction;

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
 * Represents memory. Different segments are represented by different data structs.
 *
 * @author Pete Sanderson
 * @version August 2003
 */

public class Memory extends Observable {
    /**
     * Word length in bytes.
     **/
    // NOTE:  Much of the code is hardwired for 4 byte words.  Refactoring this is low priority.
    public static final int WORD_LENGTH_BYTES = 4;

    public static MemoryConfiguration configuration;

    // Memory will maintain a collection of observables.  Each one is associated
    // with a specific memory address or address range, and each will have at least
    // one observer registered with it.  When memory access is made, make sure only
    // observables associated with that address send notices to their observers.
    // This assures that observers are not bombarded with notices from memory
    // addresses they do not care about.
    //
    // Would like a tree-like implementation, but that is complicated by this fact:
    // key for insertion into the tree would be based on Comparable using both low 
    // and high end of address range, but retrieval from the tree has to be based
    // on target address being ANYWHERE IN THE RANGE (not an exact key match).

    private Collection<MemoryObservable> observables = getNewMemoryObserversCollection();

    // The data segment is allocated in blocks of 1024 ints (4096 bytes).  Each block is
    // referenced by a "block table" entry, and the table has 1024 entries.  The capacity
    // is thus 1024 entries * 4096 bytes = 4 MB.  Should be enough to cover most
    // programs!!  Beyond that it would go to an "indirect" block (similar to Unix i-nodes),
    // which is not implemented.
    //
    // Although this scheme is an array of arrays, it is relatively space-efficient since
    // only the table is created initially. A 4096-byte block is not allocated until a value 
    // is written to an address within it.  Thus most small programs will use only 8K bytes 
    // of space (the table plus one block).  The index into both arrays is easily computed 
    // from the address; access time is constant.
    //
    // SPIM stores statically allocated data (following first .data directive) starting
    // at location 0x10010000.  This is the first Data Segment word beyond the reach of $gp
    // used in conjunction with signed 16 bit immediate offset.  $gp has value 0x10008000
    // and with the signed 16 bit offset can reach from 0x10008000 - 0xFFFF = 0x10000000 
    // (Data Segment base) to 0x10008000 + 0x7FFF = 0x1000FFFF (the byte preceding 0x10010000).
    //
    // Using my scheme, 0x10010000 falls at the beginning of the 17'th block -- table entry 16.
    // SPIM uses a heap base address of 0x10040000 which is not part of the MIPS specification.
    // (I don't have a reference for that offhand...)  Using my scheme, 0x10040000 falls at
    // the start of the 65'th block -- table entry 64.  That leaves (1024-64) * 4096 = 3,932,160
    // bytes of space available without going indirect.

    private static final int BLOCK_LENGTH_WORDS = 1024;  // allocated blocksize 1024 ints == 4K bytes
    private static final int BLOCK_TABLE_LENGTH = 1024; // Each entry of table points to a block.
    private int[][] dataBlockTable;

    // The stack is modeled similarly to the data segment.  It cannot share the same
    // data structure because the stack base address is very large.  To store it in the
    // same data structure would require implementation of indirect blocks, which has not
    // been realized.  So the stack gets its own table of blocks using the same dimensions 
    // and allocation scheme used for data segment.
    //
    // The other major difference is the stack grows DOWNWARD from its base address, not
    // upward.  I.e., the stack base is the largest stack address. This turns the whole 
    // scheme for translating memory address to block-offset on its head!  The simplest
    // solution is to calculate relative address (offset from base) by subtracting the 
    // desired address from the stack base address (rather than subtracting base address 
    // from desired address).  Thus as the address gets smaller the offset gets larger.
    // Everything else works the same, so it shares some private helper methods with
    // data segment algorithms.

    private int[][] stackBlockTable;

    // Memory mapped I/O is simulated with a separate table using the same structure and
    // logic as data segment.  Memory is allocated in 4K byte blocks.  But since MMIO
    // address range is limited to 0xffff0000 to 0xfffffffc, there are only 64K bytes 
    // total.  Thus there will be a maximum of 16 blocks, and I suspect never more than
    // one since only the first few addresses are typically used.  The only exception
    // may be a rogue program generating such addresses in a loop.  Note that the
    // MMIO addresses are interpreted by Java as negative numbers since it does not 
    // have unsigned types.  As long as the absolute address is correctly translated
    // into a table offset, this is of no concern.

    private static final int MMIO_TABLE_LENGTH = 16; // Each entry of table points to a 4K block.
    private int[][] memoryMapBlockTable;

    // I use a similar scheme for storing instructions.  MIPS text segment ranges from
    // 0x00400000 all the way to data segment (0x10000000) a range of about 250 MB!  So
    // I'll provide table of blocks with similar capacity.  This differs from data segment
    // somewhat in that the block entries do not contain int's, but instead contain
    // references to ProgramStatement objects.  

    private static final int TEXT_BLOCK_LENGTH_WORDS = 1024;  // allocated blocksize 1024 ints == 4K bytes
    private static final int TEXT_BLOCK_TABLE_LENGTH = 1024; // Each entry of table points to a block.
    private ProgramStatement[][] textBlockTable;

    // Set "top" address boundary to go with each "base" address.  This determines permissable
    // address range for user program.  Currently limit is 4MB, or 1024 * 1024 * 4 bytes based
    // on the table structures described above (except memory mapped IO, limited to 64KB by range).
    // This will be a Singleton class, only one instance is ever created.  Since I know the 
    // Memory object is always needed, I'll go ahead and create it at the time of class loading.
    // (greedy rather than lazy instantiation).  The constructor is private and getInstance()
    // always returns this instance.

    private static Memory uniqueMemoryInstance = new Memory();


    {
        setConfiguration();
    }
    /*
     * Private constructor for Memory.  Separate data structures for text and data segments. 
     **/
    public Memory() {
        initialize();
    }

    public boolean copyFrom(Memory other){
        if(textBlockTable.length != other.textBlockTable.length ||
                dataBlockTable.length != other.dataBlockTable.length ||
                stackBlockTable.length != other.stackBlockTable.length ||
                memoryMapBlockTable.length != other.memoryMapBlockTable.length){
            // The memory configurations don't match up
            return false;
        }

        for(int i = 0; i < textBlockTable.length; i++){
            if(other.textBlockTable[i] != null){
                textBlockTable[i] = other.textBlockTable[i].clone(); // TODO: potentially make ProgramStatement clonable
            }else{
                textBlockTable[i] = null;
            }
        }
        for(int i = 0; i < dataBlockTable.length; i++){
            if(other.dataBlockTable[i] != null){
                dataBlockTable[i] = other.dataBlockTable[i].clone();
            }else{
                dataBlockTable[i] = null;
            }
        }
        for(int i = 0; i < stackBlockTable.length; i++){
            if(other.stackBlockTable[i] != null){
                stackBlockTable[i] = other.stackBlockTable[i].clone();
            }else{
                stackBlockTable[i] = null;
            }
        }
        for(int i = 0; i < memoryMapBlockTable.length; i++){
            if(other.memoryMapBlockTable[i] != null){
                memoryMapBlockTable[i] = other.memoryMapBlockTable[i].clone();
            }else{
                memoryMapBlockTable[i] = null;
            }
        }
        return true;
    }

    public static Memory swapInstance(Memory mem){
        Memory temp = uniqueMemoryInstance;
        uniqueMemoryInstance = mem;
        Globals.memory = mem;
        return temp;
    }

    /**
     * Returns the unique Memory instance, which becomes in essence global.
     */

    public static Memory getInstance() {
        return uniqueMemoryInstance;
    }

    /**
     * Explicitly clear the contents of memory.  Typically done at start of assembly.
     */

    public void clear() {
        setConfiguration();
        initialize();
    }

    /**
     * Sets current memory configuration for simulation. Configuration is
     * collection of memory segment addresses. e.g. text segment starting at
     * address 0x00400000.  Configuration can be modified starting with MARS 3.7.
     */

    public static void setConfiguration() {
        MemoryConfiguration t = MemoryConfigurations.getCurrentConfiguration();
        HashMap<String, Range> sections = new HashMap<>();
        // Default configuration comes from SPIM
        sections.put(".text", t.text.limit(TEXT_BLOCK_LENGTH_WORDS * TEXT_BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES));
        sections.put(".data", t.data.limit(BLOCK_LENGTH_WORDS * BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES));
        sections.put("heap",  t.heap);
        sections.put("stack", t.stack.limitReverse(BLOCK_LENGTH_WORDS * BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES-1));
        sections.put("mmio",  t.mmio.limit(BLOCK_LENGTH_WORDS * MMIO_TABLE_LENGTH * WORD_LENGTH_BYTES));
        configuration =  new MemoryConfiguration(t.getConfigurationIdentifier(),t.getConfigurationName(), sections,t.gp_offset,t.extern_size);


        RegisterFile.getRegister("sp").changeResetValue(configuration.getStackBaseAddress());
        RegisterFile.getRegister("gp").changeResetValue(configuration.getGlobalPointer());
    }

    private void initialize() {
        heapAddress = configuration.getHeapBaseAddress();
        textBlockTable = new ProgramStatement[TEXT_BLOCK_TABLE_LENGTH][];
        dataBlockTable = new int[BLOCK_TABLE_LENGTH][]; // array of null int[] references
        stackBlockTable = new int[BLOCK_TABLE_LENGTH][];
        memoryMapBlockTable = new int[MMIO_TABLE_LENGTH][];
        System.gc(); // call garbage collector on any Table memory just deallocated.
    }


    private int heapAddress;
    // TODO: add some heap managment so programs can malloc and free
    /**
     * Returns the next available word-aligned heap address.  There is no recycling and
     * no heap management!  There is however nearly 4MB of heap space available in Rars.
     *
     * @param numBytes Number of bytes requested.  Should be multiple of 4, otherwise next higher multiple of 4 allocated.
     * @return address of allocated heap storage.
     * @throws IllegalArgumentException if number of requested bytes is negative or exceeds available heap storage
     */
    public int allocateBytesFromHeap(int numBytes) throws IllegalArgumentException {
        int result = heapAddress;
        if (numBytes < 0) {
            throw new IllegalArgumentException("request (" + numBytes + ") is negative heap amount");
        }
        int newHeapAddress = heapAddress + numBytes;
        if (newHeapAddress % 4 != 0) {
            newHeapAddress = newHeapAddress + (4 - newHeapAddress % 4); // next higher multiple of 4
        }
        if (newHeapAddress >= configuration.heap.high) {
            throw new IllegalArgumentException("request (" + numBytes + ") exceeds available heap storage");
        }
        heapAddress = newHeapAddress;
        return result;

    }

   /*  *******************************  THE SETTER METHODS  ******************************/


    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given address, write the given value over the given number of bytes.
     * This one does not check for word boundaries, and copies one byte at a time.
     * If length == 1, takes value from low order byte.  If 2, takes from low order half-word.
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored starting at that address.
     * @param length  Number of bytes to be written.
     * @return old value that was replaced by the set operation
     **/

    // Allocates blocks if necessary.
    public int set(int address, int value, int length) throws AddressErrorException {
        int oldValue = 0;
        if (Globals.debug) System.out.println("memory[" + address + "] set to " + value + "(" + length + " bytes)");
        int relativeByteAddress;
        if (configuration.data.contains(address,length)) {
            // in data segment.  Will write one byte at a time, w/o regard to boundaries.
            relativeByteAddress = address - configuration.data.low; // relative to data segment start, in bytes
            oldValue = storeBytesInTable(dataBlockTable, relativeByteAddress, length, value);
        } else if (configuration.stack.contains(address,length)) {
            // in stack.  Handle similarly to data segment write, except relative byte
            // address calculated "backward" because stack addresses grow down from base.
            relativeByteAddress = configuration.stack.high - address;
            oldValue = storeBytesInTable(stackBlockTable, relativeByteAddress, length, value);
        } else if (configuration.text.contains(address,length)) {
            // Burch Mod (Jan 2013): replace throw with call to setStatement
            // DPS adaptation 5-Jul-2013: either throw or call, depending on setting

            if (Globals.getSettings().getBooleanSetting(Settings.Bool.SELF_MODIFYING_CODE_ENABLED)) {
                if(address%4+length > 4){
                    // TODO: add checks for halfword load not aligned to halfword boundary
                    throw new AddressErrorException(
                            "Load address crosses word boundary",
                            SimulationException.LOAD_ADDRESS_MISALIGNED, address);
                }
                ProgramStatement oldStatement = getStatementNoNotify((address/4)*4);
                if (oldStatement != null) {
                    oldValue = oldStatement.getBinaryStatement();
                }

                // These manipulations set the bits in oldvalue to be like value was placed at address.
                // TODO: like below, make this more clear
                value <<= (address%4)*8;
                int mask = length == 4 ? -1 : ((1<<(8*length))-1);
                mask <<= (address%4)*8;
                value = (value&mask) | (oldValue&~mask);
                oldValue = (oldValue&mask) >> (address%4);
                setStatement((address/4)*4, new ProgramStatement(value, (address/4)*4));
            } else {
                throw new AddressErrorException(
                        "Cannot write directly to text segment!",
                        SimulationException.STORE_ACCESS_FAULT, address);
            }
        } else if (configuration.mmio.contains(address,length)) {
            // memory mapped I/O.
            relativeByteAddress = address - configuration.mmio.low;
            oldValue = storeBytesInTable(memoryMapBlockTable, relativeByteAddress, length, value);
        } else {
            // falls outside addressing range
            throw new AddressErrorException("address out of range ",
                    SimulationException.STORE_ACCESS_FAULT, address);
        }
        notifyAnyObservers(AccessNotice.WRITE, address, length, value);
        return oldValue;
    }

    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given word address, write the given value over 4 bytes (a word).
     * It must be written as is, without adjusting for byte order (little vs big endian).
     * Address must be word-aligned.
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored starting at that address.
     * @return old value that was replaced by the set operation.
     * @throws AddressErrorException If address is not on word boundary.
     **/
    public int setRawWord(int address, int value) throws AddressErrorException {
        return setWord(address,Integer.reverseBytes(value));
    }

    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given word address, write the given value over 4 bytes (a word).
     * The address must be word-aligned.
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored starting at that address.
     * @return old value that was replaced by setWord operation.
     * @throws AddressErrorException If address is not on word boundary.
     **/
    public int setWord(int address, int value) throws AddressErrorException {
        checkStoreWordAligned(address);
        return (Globals.getSettings().getBackSteppingEnabled())
                ? Globals.program.getBackStepper().addMemoryRestoreWord(address, set(address, value, WORD_LENGTH_BYTES))
                : set(address, value, WORD_LENGTH_BYTES);
    }


    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given halfword address, write the lower 16 bits of given value
     * into 2 bytes (a halfword).
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored starting at that address.  Only low order 16 bits used.
     * @return old value that was replaced by setHalf operation.
     * @throws AddressErrorException If address is not on halfword boundary.
     **/
    public int setHalf(int address, int value) throws AddressErrorException {
        if (address % 2 != 0) {
            throw new AddressErrorException("store address not aligned on halfword boundary ",
                    SimulationException.STORE_ADDRESS_MISALIGNED, address);
        }
        return (Globals.getSettings().getBackSteppingEnabled())
                ? Globals.program.getBackStepper().addMemoryRestoreHalf(address, set(address, value, 2))
                : set(address, value, 2);
    }

    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Writes low order 8 bits of given value into specified Memory byte.
     *
     * @param address Address of Memory byte to be set.
     * @param value   Value to be stored at that address.  Only low order 8 bits used.
     * @return old value that was replaced by setByte operation.
     **/

    public int setByte(int address, int value) throws AddressErrorException {
        return (Globals.getSettings().getBackSteppingEnabled())
                ? Globals.program.getBackStepper().addMemoryRestoreByte(address, set(address, value, 1))
                : set(address, value, 1);
    }


    /**
     * Writes 64 bit doubleword value starting at specified Memory address.
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored at that address.
     * @return old value that was replaced by setDouble operation.
     **/
    public long setDoubleWord(int address, long value) throws AddressErrorException {
        int oldHighOrder, oldLowOrder;
        oldHighOrder = set(address + 4, (int) (value >> 32), 4);
        oldLowOrder = set(address, (int) value, 4);
        long old = ((long)oldHighOrder << 32) | (oldLowOrder & 0xFFFFFFFFL);
        return (Globals.getSettings().getBackSteppingEnabled())
                ? Globals.program.getBackStepper().addMemoryRestoreDoubleWord(address, old)
                : old;
    }

    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Writes 64 bit double value starting at specified Memory address.
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored at that address.
     * @return old value that was replaced by setDouble operation.
     **/
    public double setDouble(int address, double value) throws AddressErrorException {
        int oldHighOrder, oldLowOrder;
        long longValue = Double.doubleToLongBits(value);
        return Double.longBitsToDouble(setDoubleWord(address,longValue));
    }


    ////////////////////////////////////////////////////////////////////////////////

    /**
     * Stores ProgramStatement in Text Segment.
     *
     * @param address   Starting address of Memory address to be set.  Must be word boundary.
     * @param statement Machine code to be stored starting at that address -- for simulation
     *                  purposes, actually stores reference to ProgramStatement instead of 32-bit machine code.
     * @throws AddressErrorException If address is not on word boundary or is outside Text Segment.
     * @see ProgramStatement
     **/

    public void setStatement(int address, ProgramStatement statement) throws AddressErrorException {
        checkStoreWordAligned(address);
        if (!inTextSegment(address)) {
            throw new AddressErrorException(
                    "Store address to text segment out of range",
                    SimulationException.STORE_ACCESS_FAULT, address);
        }
        if (Globals.debug) System.out.println("memory[" + address + "] set to " + statement.getBinaryStatement());
        storeProgramStatement(address, statement, configuration.text.low, textBlockTable);
    }


    /********************************  THE GETTER METHODS  ******************************/

    //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given word address, read the given number of bytes (max 4).
     * This one does not check for word boundaries, and copies one byte at a time.
     * If length == 1, puts value in low order byte.  If 2, puts into low order half-word.
     *
     * @param address Starting address of Memory address to be read.
     * @param length  Number of bytes to be read.
     * @return Value stored starting at that address.
     **/

    public int get(int address, int length) throws AddressErrorException {
        return get(address, length, true);
    }

    // Does the real work, but includes option to NOT notify observers.
    private int get(int address, int length, boolean notify) throws AddressErrorException {
        int value = 0;
        int relativeByteAddress;
        if (configuration.data.contains(address,length)) {
            // in data segment.  Will read one byte at a time, w/o regard to boundaries.
            relativeByteAddress = address - configuration.data.low; // relative to data segment start, in bytes
            value = fetchBytesFromTable(dataBlockTable, relativeByteAddress, length);
        } else if (configuration.stack.contains(address,length)) {
            // in stack. Similar to data, except relative address computed "backward"
            relativeByteAddress = configuration.stack.high - address;
            value = fetchBytesFromTable(stackBlockTable, relativeByteAddress, length);
        } else if (configuration.mmio.contains(address,length)) {
            // memory mapped I/O.
            relativeByteAddress = address - configuration.mmio.low;
            value = fetchBytesFromTable(memoryMapBlockTable, relativeByteAddress, length);
        } else if (configuration.text.contains(address,length)) {
            // Burch Mod (Jan 2013): replace throw with calls to getStatementNoNotify & getBinaryStatement
            // DPS adaptation 5-Jul-2013: either throw or call, depending on setting
            if (Globals.getSettings().getBooleanSetting(Settings.Bool.SELF_MODIFYING_CODE_ENABLED)) {
                if(address%4+length > 4){
                    // TODO: add checks for halfword load not aligned to halfword boundary
                    throw new AddressErrorException(
                            "Load address not aligned to word boundary ",
                            SimulationException.LOAD_ADDRESS_MISALIGNED, address);
                }
                ProgramStatement stmt = getStatementNoNotify((address/4)*4);
                // TODO: maybe find a way to make the bit manipulation more clear
                // It just selects the right bytes from the word loaded
                value = stmt == null ? 0 : length == 4 ? stmt.getBinaryStatement() : stmt.getBinaryStatement()>>(8*(address%4))&((1<<length*8)-1);
            } else {
                throw new AddressErrorException(
                        "Cannot read directly from text segment!",
                        SimulationException.LOAD_ACCESS_FAULT, address);
            }
        } else {
            // falls outside addressing range
            throw new AddressErrorException("address out of range ",
                    SimulationException.LOAD_ACCESS_FAULT, address);
        }
        if (notify) notifyAnyObservers(AccessNotice.READ, address, length, value);
        return value;
    }


    /////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given word address, read a 4 byte word as an int.
     * It transfers the 32 bit value "raw" as stored in memory, and does not adjust
     * for byte order (big or little endian).  Address must be word-aligned.
     *
     * @param address Starting address of word to be read.
     * @return Word (4-byte value) stored starting at that address.
     * @throws AddressErrorException If address is not on word boundary.
     **/

    // Note: the logic here is repeated in getRawWordOrNull() below.  Logic is
    // simplified by having this method just call getRawWordOrNull() then 
    // return either the int of its return value, or 0 if it returns null.
    // Doing so would be detrimental to simulation runtime performance, so
    // I decided to keep the duplicate logic.
    public int getRawWord(int address) throws AddressErrorException {
        return Integer.reverseBytes(getWord(address));
    }

    /////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given word address, read a 4 byte word as an int and return Integer.
     * It transfers the 32 bit value "raw" as stored in memory, and does not adjust
     * for byte order (big or little endian).  Address must be word-aligned.
     * <p>
     * Returns null if reading from text segment and there is no instruction at the
     * requested address. Returns null if reading from data segment and this is the
     * first reference to the 4K memory allocation block (i.e., an array to
     * hold the memory has not been allocated).
     * <p>
     * This method was developed by Greg Giberling of UC Berkeley to support the memory
     * dump feature that he implemented in Fall 2007.
     *
     * @param address Starting address of word to be read.
     * @return Word (4-byte value) stored starting at that address as an Integer.  Conditions
     * that cause return value null are described above.
     * @throws AddressErrorException If address is not on word boundary.
     **/

    // See note above, with getRawWord(), concerning duplicated logic.
    public Integer getRawWordOrNull(int address) throws AddressErrorException {
        Integer value = null;
        int relative;
        checkLoadWordAligned(address);
        if (configuration.data.contains(address,4)) {
            // in data segment
            relative = (address - configuration.data.low) >> 2; // convert byte address to words
            value = fetchWordOrNullFromTable(dataBlockTable, relative);
        } else if (configuration.stack.contains(address,4)) {
            // in stack. Similar to data, except relative address computed "backward"
            relative = (configuration.stack.high - address) >> 2; // convert byte address to words
            value = fetchWordOrNullFromTable(stackBlockTable, relative);
        } else if (configuration.text.contains(address,4)) {
            try {
                value = (getStatementNoNotify(address) == null) ? null : getStatementNoNotify(address).getBinaryStatement();
            } catch (AddressErrorException aee) {
                value = null;
            }
        } else {
            // falls outside addressing range
            throw new AddressErrorException("address out of range ", SimulationException.LOAD_ACCESS_FAULT, address);
        }
        // Do not notify observers.  This read operation is initiated by the
        // dump feature, not the executing program.
        return value;
    }

    /**
     * Look for first "null" memory value in an address range.  For text segment (binary code), this
     * represents a word that does not contain an instruction.  Normally use this to find the end of
     * the program.  For data segment, this represents the first block of simulated memory (block length
     * currently 4K words) that has not been referenced by an assembled/executing program.
     *
     * @param baseAddress  lowest address to be searched; the starting point
     * @param limitAddress highest address to be searched
     * @return lowest address within specified range that contains "null" value as described above.
     * @throws AddressErrorException if the base address is not on a word boundary
     */
    public int getAddressOfFirstNull(int baseAddress, int limitAddress) throws AddressErrorException {
        int address = baseAddress;
        for (; address < limitAddress; address += Memory.WORD_LENGTH_BYTES) {
            if (getRawWordOrNull(address) == null) {
                break;
            }
        }
        return address;
    }


    /**
     * Reads 64 bit doubleword value starting at specified Memory address.
     *
     * @param address Starting address of Memory address to be read
     * @return Double Word (8-byte value) stored starting at that address.
     * @throws AddressErrorException If address is not on word boundary.
     **/
    public long getDoubleWord(int address) throws AddressErrorException {
        checkLoadWordAligned(address);
        int oldHighOrder, oldLowOrder;
        oldHighOrder = get(address + 4,4);
        oldLowOrder = get(address,  4);
        return ((long)oldHighOrder << 32) | (oldLowOrder & 0xFFFFFFFFL);
    }
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given word address, read a 4 byte word as an int.
     * Does not use "get()"; we can do it faster here knowing we're working only
     * with full words.
     *
     * @param address Starting address of word to be read.
     * @return Word (4-byte value) stored starting at that address.
     * @throws AddressErrorException If address is not on word boundary.
     **/
    public int getWord(int address) throws AddressErrorException {
        checkLoadWordAligned(address);
        return get(address, WORD_LENGTH_BYTES, true);
    }

    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given word address, read a 4 byte word as an int.
     * Does not use "get()"; we can do it faster here knowing we're working only
     * with full words.  Observers are NOT notified.
     *
     * @param address Starting address of word to be read.
     * @return Word (4-byte value) stored starting at that address.
     * @throws AddressErrorException If address is not on word boundary.
     **/
    public int getWordNoNotify(int address) throws AddressErrorException {
        checkLoadWordAligned(address);
        return get(address, WORD_LENGTH_BYTES, false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starting at the given word address, read a 2 byte word into lower 16 bits of int.
     *
     * @param address Starting address of word to be read.
     * @return Halfword (2-byte value) stored starting at that address, stored in lower 16 bits.
     * @throws AddressErrorException If address is not on halfword boundary.
     **/
    public int getHalf(int address) throws AddressErrorException {
        if (address % 2 != 0) {
            throw new AddressErrorException("Load address not aligned on halfword boundary ",
                    SimulationException.LOAD_ADDRESS_MISALIGNED, address);
        }
        return get(address, 2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Reads specified Memory byte into low order 8 bits of int.
     *
     * @param address Address of Memory byte to be read.
     * @return Value stored at that address.  Only low order 8 bits used.
     **/
    public int getByte(int address) throws AddressErrorException {
        return get(address, 1);
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * Gets ProgramStatement from Text Segment.
     *
     * @param address Starting address of Memory address to be read.  Must be word boundary.
     * @return reference to ProgramStatement object associated with that address, or null if none.
     * @throws AddressErrorException If address is not on word boundary or is outside Text Segment.
     * @see ProgramStatement
     **/
    public ProgramStatement getStatement(int address) throws AddressErrorException {
        return getStatement(address, true);
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * Gets ProgramStatement from Text Segment without notifying observers.
     *
     * @param address Starting address of Memory address to be read.  Must be word boundary.
     * @return reference to ProgramStatement object associated with that address, or null if none.
     * @throws AddressErrorException If address is not on word boundary or is outside Text Segment.
     * @see ProgramStatement
     **/

    public ProgramStatement getStatementNoNotify(int address) throws AddressErrorException {
        return getStatement(address, false);
    }

    //////////

    private ProgramStatement getStatement(int address, boolean notify) throws AddressErrorException {
        checkLoadWordAligned(address);
        if (!Globals.getSettings().getBooleanSetting(Settings.Bool.SELF_MODIFYING_CODE_ENABLED)
                && !inTextSegment(address)) {
            throw new AddressErrorException(
                    "fetch address for text segment out of range ",
                    SimulationException.LOAD_ACCESS_FAULT, address);
        }
        if (inTextSegment(address))
            return readProgramStatement(address, configuration.text.low, textBlockTable, notify);
        else
            return new ProgramStatement(get(address, WORD_LENGTH_BYTES), address);
    }


    /*********************************  THE UTILITIES  *************************************/

    /**
     * Utility to determine if given address is word-aligned.
     *
     * @param address the address to check
     * @return true if address is word-aligned, false otherwise
     */
    public static boolean wordAligned(int address) {
        return (address % WORD_LENGTH_BYTES == 0);
    }

    private static void checkLoadWordAligned(int address) throws AddressErrorException {
        if (!wordAligned(address)) {
            throw new AddressErrorException(
                    "Load address not aligned to word boundary ",
                    SimulationException.LOAD_ADDRESS_MISALIGNED, address);
        }
    }

    private static void checkStoreWordAligned(int address) throws AddressErrorException {
        if (!wordAligned(address)) {
            throw new AddressErrorException(
                    "Store address not aligned to word boundary ",
                    SimulationException.STORE_ADDRESS_MISALIGNED, address);
        }
    }

    /**
     * Utility to determine if given address is doubleword-aligned.
     *
     * @param address the address to check
     * @return true if address is doubleword-aligned, false otherwise
     */
    public static boolean doublewordAligned(int address) {
        return (address % (WORD_LENGTH_BYTES + WORD_LENGTH_BYTES) == 0);
    }

    /**
     * Handy little utility to find out if given address is in the text
     * segment (starts at Memory.configuration.getTextBaseAddress()).
     * Note that RARS does not implement the entire text segment space,
     * but it does implement enough for hundreds of thousands of lines
     * of code.
     *
     * @param address integer memory address
     * @return true if that address is within RARS-defined text segment,
     * false otherwise.
     */
    public static boolean inTextSegment(int address) {
        return configuration.text.contains(address,4);
    }

    /**
     * Handy little utility to find out if given address is in RARS data
     * segment (starts at Memory.configuration.getDataSegmentBaseAddress()).
     *
     * @param address integer memory address
     * @return true if that address is within RARS-defined data segment,
     * false otherwise.
     */
    public static boolean inDataSegment(int address) {
        return configuration.data.contains(address,4);
    }

    /**
     * Handy little utility to find out if given address is in the Memory Map area
     * starts at Memory.configuration.getMemoryMapBaseAddress(), range 0xffff0000 to 0xffffffff.
     *
     * @param address integer memory address
     * @return true if that address is within RARS-defined memory map (MMIO) area,
     * false otherwise.
     */
    public static boolean inMemoryMapSegment(int address) {
        return configuration.mmio.contains(address,4);
    }


    ///////////////////////////////////////////////////////////////////////////
    //  ALL THE OBSERVABLE STUFF GOES HERE.  FOR COMPATIBILITY, Memory IS STILL
    //  EXTENDING OBSERVABLE, BUT WILL NOT USE INHERITED METHODS.  WILL INSTEAD
    //  USE A COLLECTION OF MemoryObserver OBJECTS, EACH OF WHICH IS COMBINATION
    //  OF AN OBSERVER WITH AN ADDRESS RANGE.

    /**
     * Method to accept registration from observer for any memory address.  Overrides
     * inherited method.  Note to observers: this class delegates Observable operations
     * so notices will come from the delegate, not the memory object.
     *
     * @param obs the observer
     */

    public void addObserver(Observer obs) {
        try {  // split so start address always >= end address
            this.addObserver(obs, 0, 0x7ffffffc);
            this.addObserver(obs, 0x80000000, 0xfffffffc);
        } catch (AddressErrorException aee) {
            System.out.println("Internal Error in Memory.addObserver: " + aee);
        }
    }

    /**
     * Method to accept registration from observer for specific address.  This includes
     * the memory word starting at the given address. Note to observers: this class delegates Observable operations
     * so notices will come from the delegate, not the memory object.
     *
     * @param obs  the observer
     * @param addr the memory address which must be on word boundary
     */

    public void addObserver(Observer obs, int addr) throws AddressErrorException {
        this.addObserver(obs, addr, addr);
    }


    /**
     * Method to accept registration from observer for specific address range.  The
     * last byte included in the address range is the last byte of the word specified
     * by the ending address. Note to observers: this class delegates Observable operations
     * so notices will come from the delegate, not the memory object.
     *
     * @param obs       the observer
     * @param startAddr the low end of memory address range, must be on word boundary
     * @param endAddr   the high end of memory address range, must be on word boundary
     */
    public void addObserver(Observer obs, int startAddr, int endAddr) throws AddressErrorException {
        checkLoadWordAligned(startAddr);
        checkLoadWordAligned(endAddr);
        // upper half of address space (above 0x7fffffff) has sign bit 1 thus is seen as
        // negative.
        if (startAddr >= 0 && endAddr < 0) {
            throw new AddressErrorException("range cannot cross 0x8000000; please split it up",
                    SimulationException.LOAD_ACCESS_FAULT, startAddr);
        }
        if (endAddr < startAddr) {
            throw new AddressErrorException("end address of range < start address of range ",
                    SimulationException.LOAD_ACCESS_FAULT, startAddr);
        }
        observables.add(new MemoryObservable(obs, startAddr, endAddr));
    }

    /**
     * Return number of observers
     */
    public int countObservers() {
        return observables.size();
    }

    /**
     * Remove specified memory observers
     *
     * @param obs Observer to be removed
     */
    public void deleteObserver(Observer obs) {
        for (MemoryObservable o : observables) {
            o.deleteObserver(obs);
        }
    }

    /**
     * Remove all memory observers
     */
    public void deleteObservers() {
        // just drop the collection
        observables = getNewMemoryObserversCollection();
    }

    /**
     * Overridden to be unavailable.  The notice that an Observer
     * receives does not come from the memory object itself, but
     * instead from a delegate.
     *
     * @throws UnsupportedOperationException
     */
    public void notifyObservers() {
        throw new UnsupportedOperationException();
    }

    /**
     * Overridden to be unavailable.  The notice that an Observer
     * receives does not come from the memory object itself, but
     * instead from a delegate.
     *
     * @throws UnsupportedOperationException
     */
    public void notifyObservers(Object obj) {
        throw new UnsupportedOperationException();
    }


    private Collection<MemoryObservable> getNewMemoryObserversCollection() {
        return new Vector<>();  // Vectors are thread-safe
    }

    /////////////////////////////////////////////////////////////////////////
    // Private class whose objects will represent an observable-observer pair
    // for a given memory address or range.
    private class MemoryObservable extends Observable implements Comparable<MemoryObservable> {
        private int lowAddress, highAddress;

        public MemoryObservable(Observer obs, int startAddr, int endAddr) {
            lowAddress = startAddr;
            highAddress = endAddr;
            this.addObserver(obs);
        }

        public boolean match(int address) {
            return (address >= lowAddress && address <= highAddress - 1 + WORD_LENGTH_BYTES);
        }

        public void notifyObserver(MemoryAccessNotice notice) {
            this.setChanged();
            this.notifyObservers(notice);
        }

        // Useful to have for future refactoring, if it actually becomes worthwhile to sort
        // these or put 'em in a tree (rather than sequential search through list).
        public int compareTo(MemoryObservable mo) {
            if (this.lowAddress < mo.lowAddress || this.lowAddress == mo.lowAddress && this.highAddress < mo.highAddress) {
                return -1;
            }
            if (this.lowAddress > mo.lowAddress || this.lowAddress == mo.lowAddress && this.highAddress > mo.highAddress) {
                return -1;
            }
            return 0;  // they have to be equal at this point.
        }
    }


    /*********************************  THE HELPERS  *************************************/


    ////////////////////////////////////////////////////////////////////////////////
    //
    // Method to notify any observers of memory operation that has just occurred.
    //
    // The "|| Globals.getGui()==null" is a hack added 19 July 2012 DPS.  IF simulation
    // is from command mode, Globals.program is null but still want ability to observe.
    private void notifyAnyObservers(int type, int address, int length, int value) {
        if ((Globals.program != null || Globals.getGui() == null) && this.observables.size() > 0) {
            for (MemoryObservable mo : observables) {
                if (mo.match(address)) {
                    mo.notifyObserver(new MemoryAccessNotice(type, address, length, value));
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Helper method to store 1, 2 or 4 byte value in table that represents
    // memory. Originally used just for data segment, but now also used for stack.
    // Both use different tables but same storage method and same table size
    // and block size.
    // Modified 29 Dec 2005 to return old value of replaced bytes.
    //
    private static final boolean STORE = true;
    private static final boolean FETCH = false;

    private int storeBytesInTable(int[][] blockTable,
                                  int relativeByteAddress, int length, int value) {
        return storeOrFetchBytesInTable(blockTable, relativeByteAddress, length, value, STORE);
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Helper method to fetch 1, 2 or 4 byte value from table that represents
    // memory.  Originally used just for data segment, but now also used for stack.
    // Both use different tables but same storage method and same table size
    // and block size.
    //

    private int fetchBytesFromTable(int[][] blockTable, int relativeByteAddress, int length) {
        return storeOrFetchBytesInTable(blockTable, relativeByteAddress, length, 0, FETCH);
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // The helper's helper.  Works for either storing or fetching, little or big endian.
    // When storing/fetching bytes, most of the work is calculating the correct array element(s)
    // and element byte(s).  This method performs either store or fetch, as directed by its
    // client using STORE or FETCH in last arg.
    // Modified 29 Dec 2005 to return old value of replaced bytes, for STORE.
    //
    private synchronized int storeOrFetchBytesInTable(int[][] blockTable,
                                                      int relativeByteAddress, int length, int value, boolean op) {
        int relativeWordAddress, block, offset, bytePositionInMemory, bytePositionInValue;
        int oldValue = 0; // for STORE, return old values of replaced bytes
        int loopStopper = 3 - length;
        // IF added DPS 22-Dec-2008. NOTE: has NOT been tested with Big-Endian.
        // Fix provided by Saul Spatz; comments that follow are his.
        // If address in stack segment is 4k + m, with 0 < m < 4, then the
        // relativeByteAddress we want is stackBaseAddress - 4k + m, but the
        // address actually passed in is stackBaseAddress - (4k + m), so we
        // need to add 2m.  Because of the change in sign, we get the
        // expression 4-delta below in place of m.
        if (blockTable == stackBlockTable) {
            int delta = relativeByteAddress % 4;
            if (delta != 0) {
                relativeByteAddress += (4 - delta) << 1;
            }
        }
        for (bytePositionInValue = 3; bytePositionInValue > loopStopper; bytePositionInValue--) {
            bytePositionInMemory = relativeByteAddress % 4;
            relativeWordAddress = relativeByteAddress >> 2;
            block = relativeWordAddress / BLOCK_LENGTH_WORDS;  // Block number
            offset = relativeWordAddress % BLOCK_LENGTH_WORDS; // Word within that block
            if (blockTable[block] == null) {
                if (op == STORE)
                    blockTable[block] = new int[BLOCK_LENGTH_WORDS];
                else
                    return 0;
            }
            bytePositionInMemory = 3 - bytePositionInMemory;
            if (op == STORE) {
                oldValue = replaceByte(blockTable[block][offset], bytePositionInMemory,
                        oldValue, bytePositionInValue);
                blockTable[block][offset] = replaceByte(value, bytePositionInValue,
                        blockTable[block][offset], bytePositionInMemory);
            } else {// op == FETCH
                value = replaceByte(blockTable[block][offset], bytePositionInMemory,
                        value, bytePositionInValue);
            }
            relativeByteAddress++;
        }
        return (op == STORE) ? oldValue : value;
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Helper method to store 4 byte value in table that represents memory.
    // Originally used just for data segment, but now also used for stack.
    // Both use different tables but same storage method and same table size
    // and block size.  Assumes address is word aligned, no endian processing.
    // Modified 29 Dec 2005 to return overwritten value.

    private synchronized int storeWordInTable(int[][] blockTable, int relative, int value) {
        int block, offset, oldValue;
        block = relative / BLOCK_LENGTH_WORDS;
        offset = relative % BLOCK_LENGTH_WORDS;
        if (blockTable[block] == null) {
            // First time writing to this block, so allocate the space.
            blockTable[block] = new int[BLOCK_LENGTH_WORDS];
        }
        oldValue = blockTable[block][offset];
        blockTable[block][offset] = value;
        return oldValue;
    }

    // Same as above, but doesn't set, just gets
    private synchronized int fetchWordFromTable(int[][] blockTable, int relative) {
        int value = 0;
        int block, offset;
        block = relative / BLOCK_LENGTH_WORDS;
        offset = relative % BLOCK_LENGTH_WORDS;
        if (blockTable[block] == null) {
            // first reference to an address in this block.  Assume initialized to 0.
            value = 0;
        } else {
            value = blockTable[block][offset];
        }
        return value;
    }

    // Same as above, but if it hasn't been allocated returns null.
    // Developed by Greg Gibeling of UC Berkeley, fall 2007.
    private synchronized Integer fetchWordOrNullFromTable(int[][] blockTable, int relative) {
        int value = 0;
        int block, offset;
        block = relative / BLOCK_LENGTH_WORDS;
        offset = relative % BLOCK_LENGTH_WORDS;
        if (blockTable[block] == null) {
            // first reference to an address in this block.  Assume initialized to 0.
            return null;
        } else {
            value = blockTable[block][offset];
        }
        return value;
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // Returns result of substituting specified byte of source value into specified byte
    // of destination value. Byte positions are 0-1-2-3, listed from most to least
    // significant.  No endian issues.  This is a private helper method used by get() & set().
    private int replaceByte(int sourceValue, int bytePosInSource, int destValue, int bytePosInDest) {
        return
                // Set source byte value into destination byte position; set other 24 bits to 0's...
                ((sourceValue >> (24 - (bytePosInSource << 3)) & 0xFF)
                        << (24 - (bytePosInDest << 3)))
                        // and bitwise-OR it with...
                        |
                        // Set 8 bits in destination byte position to 0's, other 24 bits are unchanged.
                        (destValue & ~(0xFF << (24 - (bytePosInDest << 3))));
    }

    ///////////////////////////////////////////////////////////////////////
    // Store a program statement at the given address.  Address has already been verified as valid.
    private void storeProgramStatement(int address, ProgramStatement statement,
                                       int baseAddress, ProgramStatement[][] blockTable) {
        int relative = (address - baseAddress) >> 2; // convert byte address to words
        int block = relative / BLOCK_LENGTH_WORDS;
        int offset = relative % BLOCK_LENGTH_WORDS;
        if (block < TEXT_BLOCK_TABLE_LENGTH) {
            if (blockTable[block] == null) {
                // No instructions are stored in this block, so allocate the block.
                blockTable[block] = new ProgramStatement[BLOCK_LENGTH_WORDS];
            }
            blockTable[block][offset] = statement;
        }
    }


    /**
     * Read a program statement from the given address.  Address has already been verified
     * as valid.
     *
     * @param address     the address to read from
     * @param baseAddress the base address for the section being read from (.text)
     * @param blockTable  the internal table of program statements
     * @param notify      whether or not it notifies observers
     * @return associated ProgramStatement or null if none.
     */
    private ProgramStatement readProgramStatement(int address, int baseAddress, ProgramStatement[][] blockTable, boolean notify) {
        int relative = (address - baseAddress) >> 2; // convert byte address to words
        int block = relative / TEXT_BLOCK_LENGTH_WORDS;
        int offset = relative % TEXT_BLOCK_LENGTH_WORDS;
        if (block < TEXT_BLOCK_TABLE_LENGTH) {
            if (blockTable[block] == null || blockTable[block][offset] == null) {
                // No instructions are stored in this block or offset.
                if (notify) notifyAnyObservers(AccessNotice.READ, address, Instruction.INSTRUCTION_LENGTH, 0);
                return null;
            } else {
                if (notify)
                    notifyAnyObservers(AccessNotice.READ, address, Instruction.INSTRUCTION_LENGTH, blockTable[block][offset].getBinaryStatement());
                return blockTable[block][offset];
            }
        }
        if (notify) notifyAnyObservers(AccessNotice.READ, address, Instruction.INSTRUCTION_LENGTH, 0);
        return null;
    }

}
