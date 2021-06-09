package rars.api;

import rars.*;
import rars.riscv.hardware.*;
import rars.simulator.ProgramArgumentList;
import rars.simulator.Simulator;
import rars.util.SystemIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/**
 * <p>
 * This is most of the public API for running RARS programs. It wraps internal
 * APIs to provide a base for making applications to simulate many programs.
 * </p>
 *
 * The order you are expected to run the methods is:
 * <ol>
 * <li> assemble(...)
 * <li> setup(...)
 * <li> get/set for any specific setup
 * <li> simulate()
 * <li> get/set to check output
 * <li> repeat 3-5 if simulation hasn't terminated
 * <li> repeat 2-6 for multiple inputs as needed
 * </ol>
 *
 * <p>
 * Importantly, only one instance of Program can be setup at a time (this may
 * change in the future). Only the most recent program to be setup is valid to
 * call simulate on. Additionally, reading registers or memory is also only valid
 * once setup has been called and before another setup is called.
 * </p>
 *
 * <p>
 * Also, it is not threadsafe, calling assemble in another thread could invalidate
 * a concurrent simulation.
 * </p>
 */
public class Program {

    private Options set;
    private RISCVprogram code;
    private SystemIO.Data fds;
    private ByteArrayOutputStream stdout, stderr;
    private Memory assembled, simulation;
    private int startPC, exitCode;

    public Program() {
        this(new Options());
    }

    public Program(Options set){
        Globals.initialize(false);
        this.set = set;
        code = new RISCVprogram();
        assembled = new Memory();
        simulation = new Memory();
    }

    /**
     * Assembles from a list of files
     *
     * @param files A list of files to assemble
     * @param main Which file should be considered the main file; it should be in files
     * @return A list of warnings generated if Options.warningsAreErrors is true, this will be empty
     * @throws AssemblyException thrown if any errors are found in the code
     */
    public ErrorList assemble(ArrayList<String> files, String main) throws AssemblyException {
        ArrayList<RISCVprogram> programs = code.prepareFilesForAssembly(files,main, null);
        return assemble(programs);
    }

    /**
     * Assembles a single file
     *
     * @param file path to the file to assemble
     * @return A list of warnings generated if Options.warningsAreErrors is true, this will be empty
     * @throws AssemblyException thrown if any errors are found in the code
     */
    public ErrorList assemble(String file) throws AssemblyException {
        // TODO: potentially inline prepareForAssembly
        ArrayList<String> files = new ArrayList<>();
        files.add(file);
        ArrayList<RISCVprogram> programs = code.prepareFilesForAssembly(files,file, null);
        return assemble(programs);
    }

    /**
     * Assembles a string as RISC-V source code
     *
     * @param source the code to assemble
     * @return A list of warnings generated if Options.warningsAreErrors is true, this will be empty
     * @throws AssemblyException thrown if any errors are found in the code
     */
    public ErrorList assembleString(String source) throws AssemblyException {
        ArrayList<RISCVprogram> programs = new ArrayList<>();
        code.fromString(source);
        code.tokenize();
        programs.add(code);
        return assemble(programs);
    }

    private ErrorList assemble(ArrayList<RISCVprogram> programs) throws AssemblyException {
        Memory temp = Memory.swapInstance(assembled); // Assembling changes memory so we need to swap to capture that.
        ErrorList warnings = null;
        AssemblyException e = null;
        try {
            warnings = code.assemble(programs,set.pseudo,set.warningsAreErrors);
        }catch(AssemblyException ae){
            e = ae;
        }
        Memory.swapInstance(temp);
        if(e != null)throw e;

        RegisterFile.initializeProgramCounter(set.startAtMain);
        startPC = RegisterFile.getProgramCounter();

        return warnings;
    }

    /**
     * Prepares the simulator for execution. Clears registers, loads arguments
     * into memory and initializes the String backed STDIO
     *
     * @param args Just like the args to a Java main, but an ArrayList.
     * @param STDIN A string that can be read in the program like its stdin or null to allow IO passthrough
     */
    public void setup(ArrayList<String> args, String STDIN){
        RegisterFile.resetRegisters();
        FloatingPointRegisterFile.resetRegisters();
        ControlAndStatusRegisterFile.resetRegisters();
        InterruptController.reset();
        RegisterFile.initializeProgramCounter(startPC);
        Globals.exitCode = 0;

        // Copy in assembled code and arguments
        simulation.copyFrom(assembled);
        Memory tmpMem = Memory.swapInstance(simulation);
        new ProgramArgumentList(args).storeProgramArguments();
        Memory.swapInstance(tmpMem);

        // To capture the IO we need to replace stdin and friends
	if (STDIN!=null){
		stdout=new ByteArrayOutputStream();
		stderr=new ByteArrayOutputStream();
		fds=new SystemIO.Data(
				new ByteArrayInputStream(STDIN.getBytes()),stdout,stderr
			);
	} else {
		fds=new SystemIO.Data(true);
	}
    }

    /**
     * Simulates a processor executing the machine code.
     *
     * @return the reason why simulation was paused or terminated.
     *         Possible values are: <ul>
     *              <li> BREAKPOINT (caused by ebreak instruction),
     *              <li> MAX_STEPS (caused by simulating Options.maxSteps instructions),
     *              <li> NORMAL_TERMINATION (caused by executing the exit system call)
     *              <li> CLIFF_TERMINATION (caused by the program overflowing the written code). </ul>
     *         Only BREAKPOINT and MAX_STEPS can be simulated further.
     * @throws SimulationException thrown if there is an uncaught interrupt. The program cannot be simulated further.
     */
    public Simulator.Reason simulate() throws SimulationException {
        Simulator.Reason ret = null;
        SimulationException e = null;

        // Swap out global state for local state.
        boolean selfMod = Globals.getSettings().getBooleanSetting(Settings.Bool.SELF_MODIFYING_CODE_ENABLED);
        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.SELF_MODIFYING_CODE_ENABLED, set.selfModifyingCode);
        SystemIO.Data tmpFiles = SystemIO.swapData(fds);
        Memory tmpMem = Memory.swapInstance(simulation);

        try {
            ret = code.simulate(set.maxSteps);
        }catch(SimulationException se){
            e = se;
        }
        exitCode = Globals.exitCode;

        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.SELF_MODIFYING_CODE_ENABLED, selfMod);
        SystemIO.swapData(tmpFiles);
        Memory.swapInstance(tmpMem);

        if(e != null)throw e;
        return ret;
    }

    /**
     * @return converts the bytes sent to stdout into a string (resets to "" when setup is called)
     */
    public String getSTDOUT(){
        return stdout.toString();
    }

    /**
     * @return converts the bytes sent to stderr into a string (resets to "" when setup is called)
     */
    public String getSTDERR(){
        return stderr.toString();
    }

    /**
     * Gets the value of a normal, floating-point or control and status register.
     *
     * @param name Either the common usage (t0, a0, ft0), explicit numbering (x2, x3, f0), or CSR name (ustatus)
     * @return The value of the register as an int (floats are encoded as IEEE-754)
     * @throws NullPointerException if name is invalid; only needs to be checked if code accesses arbitrary names
     */
    public int getRegisterValue(String name){
        Register r = RegisterFile.getRegister(name);
        if(r == null){
            r = FloatingPointRegisterFile.getRegister(name);
        }
        if(r == null){
            return ControlAndStatusRegisterFile.getValue(name);
        }else{
            return (int)r.getValue();
        }
    }

    /**
     * Sets the value of a normal, floating-point or control and status register.
     *
     * @param name Either the common usage (t0, a0, ft0), explicit numbering (x2, x3, f0), or CSR name (ustatus)
     * @param value The value of the register as an int (floats are encoded as IEEE-754)
     * @throws NullPointerException if name is invalid; only needs to be checked if code accesses arbitrary names
     */
    public void setRegisterValue(String name, int value){
        Register r = RegisterFile.getRegister(name);
        if(r == null){
            r = FloatingPointRegisterFile.getRegister(name);
        }
        if(r == null){
            ControlAndStatusRegisterFile.updateRegister(name,value);
        }else{
            r.setValue(value);
        }
    }

    /**
     * Returns the exit code passed to the exit syscall if it was called, otherwise returns 0
     */
    public int getExitCode(){
        return exitCode;
    }

    /**
     * Gets the instance of memory the program is using.
     *
     * This is only valid when setup has been called.
     */
    public Memory getMemory(){
        return simulation;
    }
}
