package rars.venus.run;

import rars.*;
import rars.Globals;
import rars.riscv.hardware.*;
import rars.util.FilenameFinder;
import rars.util.SystemIO;
import rars.venus.*;
import rars.venus.registers.RegistersPane;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;

/*
Copyright (c) 2003-2010,  Pete Sanderson and Kenneth Vollmar

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
 * Action class for the Run -> Assemble menu item (and toolbar icon)
 */
public class RunAssembleAction extends GuiAction {

    private static ArrayList<RISCVprogram> programsToAssemble;
    private static boolean extendedAssemblerEnabled;
    private static boolean warningsAreErrors;
    // Threshold for adding filename to printed message of files being assembled.
    private static final int LINE_LENGTH_LIMIT = 60;
    private VenusUI mainUI;

    public RunAssembleAction(String name, Icon icon, String descrip, Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel);
        mainUI = gui;
    }

    // These are both used by RunResetAction to re-assemble under identical
    // conditions.
    public static ArrayList<RISCVprogram> getProgramsToAssemble() {
        return programsToAssemble;
    }

    static boolean getExtendedAssemblerEnabled() {
        return extendedAssemblerEnabled;
    }

    static boolean getWarningsAreErrors() {
        return warningsAreErrors;
    }

    public void actionPerformed(ActionEvent e) {
        String name = this.getValue(Action.NAME).toString();
        MessagesPane messagesPane = mainUI.getMessagesPane();
        ExecutePane executePane = mainUI.getMainPane().getExecutePane();
        RegistersPane registersPane = mainUI.getRegistersPane();

        extendedAssemblerEnabled = Globals.getSettings().getBooleanSetting(Settings.Bool.EXTENDED_ASSEMBLER_ENABLED);
        warningsAreErrors = Globals.getSettings().getBooleanSetting(Settings.Bool.WARNINGS_ARE_ERRORS);
        if (FileStatus.getFile() != null) {
            if (FileStatus.get() == FileStatus.EDITED) {
                mainUI.getEditor().save();
            }
            try {
                Globals.program = new RISCVprogram();
                ArrayList<String> filesToAssemble;
                if (Globals.getSettings().getBooleanSetting(Settings.Bool.ASSEMBLE_ALL)) {// setting calls for multiple
                                                                                          // file assembly
                    filesToAssemble = FilenameFinder.getFilenameList(new File(FileStatus.getName()).getParent(),
                            Globals.fileExtensions);
                } else {
                    filesToAssemble = new ArrayList<>();
                    filesToAssemble.add(FileStatus.getName());
                }
                if (Globals.getSettings().getBooleanSetting(Settings.Bool.ASSEMBLE_OPEN)) {
                    mainUI.getEditor().saveAll();
                    String[] paths = mainUI.getEditor().getOpenFilePaths();
                    for (String path : paths) {
                        if (!filesToAssemble.contains(path)) {
                            filesToAssemble.add(path);
                        }
                    }
                }
                String exceptionHandler = null;
                if (Globals.getSettings().getBooleanSetting(Settings.Bool.EXCEPTION_HANDLER_ENABLED)
                        && Globals.getSettings().getExceptionHandler() != null
                        && Globals.getSettings().getExceptionHandler().length() > 0) {
                    exceptionHandler = Globals.getSettings().getExceptionHandler();
                }
                programsToAssemble = Globals.program.prepareFilesForAssembly(filesToAssemble,
                        FileStatus.getFile().getPath(), exceptionHandler);
                messagesPane.postMessage(buildFileNameList(name + ": assembling ", programsToAssemble));
                // added logic to receive any warnings and output them.... DPS 11/28/06
                ErrorList warnings = Globals.program.assemble(programsToAssemble, extendedAssemblerEnabled,
                        warningsAreErrors);
                if (warnings.warningsOccurred()) {
                    messagesPane.postMessage(warnings.generateWarningReport());
                }
                messagesPane.postMessage(name + ": operation completed successfully.\n\n");
                FileStatus.setAssembled(true);
                FileStatus.set(FileStatus.RUNNABLE);
                //TODO
                RegisterFile.resetRegisters();
                FloatingPointRegisterFile.resetRegisters();
                ControlAndStatusRegisterFile.resetRegisters();
                InterruptController.reset();
                Globals.reservationTables.reset();
                executePane.getTextSegmentWindow().setupTable();
                executePane.getDataSegmentWindow().setupTable();
                executePane.getDataSegmentWindow().highlightCellForAddress(Memory.dataBaseAddress);
                executePane.getDataSegmentWindow().clearHighlighting();
                executePane.getLabelsWindow().setupTable();
                executePane.getTextSegmentWindow().setCodeHighlighting(true);
                executePane.getTextSegmentWindow().highlightStepAtPC();
                registersPane.getRegistersWindow().clearWindow();
                registersPane.getFloatingPointWindow().clearWindow();
                registersPane.getControlAndStatusWindow().clearWindow();
                mainUI.setReset(true);
                mainUI.setStarted(false);
                mainUI.getMainPane().setSelectedComponent(executePane);

                // Aug. 24, 2005 Ken Vollmar
                SystemIO.resetFiles(); // Ensure that I/O "file descriptors" are initialized for a new program run

            } catch (AssemblyException pe) {
                String errorReport = pe.errors().generateErrorAndWarningReport();
                messagesPane.postMessage(errorReport);
                messagesPane.postMessage(name + ": operation completed with errors.\n\n");
                // Select editor line containing first error, and corresponding error message.
                ArrayList<ErrorMessage> errorMessages = pe.errors().getErrorMessages();
                for (ErrorMessage em : errorMessages) {
                    // No line or position may mean File Not Found (e.g. exception file). Don't try
                    // to open. DPS 3-Oct-2010
                    if (em.getLine() == 0 && em.getPosition() == 0) {
                        continue;
                    }
                    if (!em.isWarning() || warningsAreErrors) {
                        Globals.getGui().getMessagesPane().selectErrorMessage(em.getFilename(), em.getLine(),
                                em.getPosition());
                        // Bug workaround: Line selection does not work correctly for the JEditTextArea
                        // editor
                        // when the file is opened then automatically assembled (assemble-on-open
                        // setting).
                        // Automatic assemble happens in EditTabbedPane's openFile() method, by invoking
                        // this method (actionPerformed) explicitly with null argument. Thus e!=null
                        // test.
                        // DPS 9-Aug-2010
                        if (e != null) {
                            Globals.getGui().getMessagesPane().selectEditorTextLine(em.getFilename(), em.getLine(),
                                    em.getPosition());
                        }
                        break;
                    }
                }
                FileStatus.setAssembled(false);
                FileStatus.set(FileStatus.NOT_EDITED);
            }
        }
    }

    // Handy little utility for building comma-separated list of filenames
    // while not letting line length get out of hand.
    private String buildFileNameList(String preamble, ArrayList<RISCVprogram> programList) {
        String result = preamble;
        int lineLength = result.length();
        for (int i = 0; i < programList.size(); i++) {
            String filename = programList.get(i).getFilename();
            result += filename + ((i < programList.size() - 1) ? ", " : "");
            lineLength += filename.length();
            if (lineLength > LINE_LENGTH_LIMIT) {
                result += "\n";
                lineLength = 0;
            }
        }
        return result + ((lineLength == 0) ? "" : "\n") + "\n";
    }
}
