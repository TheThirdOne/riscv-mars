package rars.util;

import rars.Globals;
import rars.Settings;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

	/*
Copyright (c) 2003-2013,  Pete Sanderson and Kenneth Vollmar

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
 * Provides standard i/o services needed to simulate the RISCV syscall
 * routines.  These methods will detect whether the simulator is being
 * run from the command line or through the GUI, then do I/O to
 * System.in and System.out in the former situation, and interact with
 * the GUI in the latter.
 *
 * @author Pete Sanderson and Ken Vollmar
 * @version August 2003-2005
 */
public class SystemIO {
    /**
     * Buffer size for syscalls for file I/O
     */
    public static final int SYSCALL_BUFSIZE = 128;
    /**
     * Maximum number of files that can be open
     */
    public static final int SYSCALL_MAXFILES = 32;
    /**
     * String used for description of file error
     */
    public static String fileErrorString = new String("File operation OK");

    private static final int O_RDONLY = 0x00000000;
    private static final int O_WRONLY = 0x00000001;
    private static final int O_RDWR = 0x00000002;
    private static final int O_APPEND = 0x00000008;
    private static final int O_CREAT = 0x00000200; // 512
    private static final int O_TRUNC = 0x00000400; // 1024
    private static final int O_EXCL = 0x00000800; // 2048

    private static final int SEEK_SET = 0;
    private static final int SEEK_CUR = 1;
    private static final int SEEK_END = 2;

    // standard I/O channels
    private static final int STDIN = 0;
    private static final int STDOUT = 1;
    private static final int STDERR = 2;

    // Will use one buffered reader for all keyboard/redirected/piped input.
    // Added by DPS 28 Feb 2008.  See getInputReader() below.
    private static BufferedReader inputReader = null;

    /**
     * Implements syscall to read an integer value.
     * Client is responsible for catching NumberFormatException.
     *
     * @param serviceNumber the number assigned to Read Int syscall (default 5)
     * @return int value corresponding to user input
     */

    public static int readInteger(int serviceNumber) {
        String input = readStringInternal("0", "Enter an integer value (syscall " + serviceNumber + ")", -1);
        // Client is responsible for catching NumberFormatException
        return Integer.parseInt(input.trim());
    }

    private static String readStringInternal(String init, String prompt, int maxlength) {
        String input = init;
        if (Globals.getGui() == null) {
            try {
                input = getInputReader().readLine();
            } catch (IOException e) {
            }
        } else {
            if (Globals.getSettings().getBooleanSetting(Settings.Bool.POPUP_SYSCALL_INPUT)) {
                input = Globals.getGui().getMessagesPane().getInputString(prompt);
            } else {
                input = Globals.getGui().getMessagesPane().getInputString(maxlength);
            }
        }
        return input;
    }

    /**
     * Implements syscall to read a float value.
     * Client is responsible for catching NumberFormatException.
     *
     * @param serviceNumber the number assigned to Read Float syscall (default 6)
     * @return float value corresponding to user input
     * Feb 14 2005 Ken Vollmar
     */
    public static float readFloat(int serviceNumber) {
        String input = readStringInternal("0", "Enter a float value (syscall " + serviceNumber + ")", -1);
        return Float.parseFloat(input.trim());
    }

    /**
     * Implements syscall to read a double value.
     * Client is responsible for catching NumberFormatException.
     *
     * @param serviceNumber the number assigned to Read Duoble syscall (default 7)
     * @return double value corresponding to user input
     * Feb 14 2005 Ken Vollmar
     */
    public static double readDouble(int serviceNumber) {
        String input = readStringInternal("0", "Enter a Double value (syscall " + serviceNumber + ")", -1);
        return Double.parseDouble(input.trim());
    }

    /**
     * Implements syscall having 4 in $v0, to print a string.
     */
    public static void printString(String string) {
        if (Globals.getGui() == null) {
            System.out.print(string);
        } else {
            print2Gui(string);
        }

    }


    /**
     * Implements syscall to read a string.
     *
     * @param serviceNumber the number assigned to Read String syscall (default 8)
     * @param maxLength     the maximum string length
     * @return the entered string, truncated to maximum length if necessary
     */
    public static String readString(int serviceNumber, int maxLength) {
        String input = readStringInternal("", "Enter a string of maximum length " + maxLength
                + " (syscall " + serviceNumber + ")", maxLength);
        if (input.endsWith("\n")) {
            input = input.substring(0, input.length() - 1);
        }
        if (input.length() > maxLength) {
            // Modified DPS 13-July-2011.  Originally: return input.substring(0, maxLength);
            return (maxLength <= 0) ? "" : input.substring(0, maxLength);
        } else {
            return input;
        }
    }


    /**
     * Implements syscall having 12 in $v0, to read a char value.
     *
     * @param serviceNumber the number assigned to Read Char syscall (default 12)
     * @return int value with lowest byte corresponding to user input
     */
    public static int readChar(int serviceNumber) {
        int returnValue = 0;

        String input = readStringInternal("0", "Enter a character value (syscall " + serviceNumber + ")", 1);
        // The whole try-catch is not really necessary in this case since I'm
        // just propagating the runtime exception (the default behavior), but
        // I want to make it explicit.  The client needs to catch it.
        try {
            returnValue = (int) (input.charAt(0)); // first character input
        } catch (IndexOutOfBoundsException e) // no chars present
        {
            throw e;  // was: returnValue = 0;
        }

        return returnValue;

    }


    /**
     * Write bytes to file.
     *
     * @param fd              file descriptor
     * @param myBuffer        byte array containing characters to write
     * @param lengthRequested number of bytes to write
     * @return number of bytes written, or -1 on error
     */

    public static int writeToFile(int fd, byte[] myBuffer, int lengthRequested) {
        /////////////// DPS 8-Jan-2013  ////////////////////////////////////////////////////
        /// Write to STDOUT or STDERR file descriptor while using IDE - write to Messages pane.
        if ((fd == STDOUT || fd == STDERR) && Globals.getGui() != null) {
            String data = new String(myBuffer, StandardCharsets.UTF_8); //decode the bytes using UTF-8 charset
            print2Gui(data);
            return myBuffer.length; // data.length would not count multi-byte characters
        }
        ///////////////////////////////////////////////////////////////////////////////////
        //// When running in command mode, code below works for either regular file or STDOUT/STDERR

        if (!FileIOData.fdInUse(fd, 1)) // Check the existence of the "write" fd
        {
            fileErrorString = "File descriptor " + fd + " is not open for writing";
            return -1;
        }
        // retrieve FileOutputStream from storage
        OutputStream outputStream = (OutputStream) FileIOData.getStreamInUse(fd);
        try {
            // Oct. 9 2005 Ken Vollmar
            // Observation: made a call to outputStream.write(myBuffer, 0, lengthRequested)
            //     with myBuffer containing 6(ten) 32-bit-words <---> 24(ten) bytes, where the
            //     words are MIPS integers with values such that many of the bytes are ZEROES.
            //     The effect is apparently that the write stops after encountering a zero-valued
            //     byte. (The method write does not return a value and so this can't be verified
            //     by the return value.)
            // Writes up to lengthRequested bytes of data to this output stream from an array of bytes.
            // outputStream.write(myBuffer, 0, lengthRequested); // write is a void method -- no verification value returned

            // Oct. 9 2005 Ken Vollmar  Force the write statement to write exactly
            // the number of bytes requested, even though those bytes include many ZERO values.
            for (int ii = 0; ii < lengthRequested; ii++) {
                outputStream.write(myBuffer[ii]);
            }
            outputStream.flush();// DPS 7-Jan-2013
        } catch (IOException e) {
            fileErrorString = "IO Exception on write of file with fd " + fd;
            return -1;
        } catch (IndexOutOfBoundsException e) {
            fileErrorString = "IndexOutOfBoundsException on write of file with fd" + fd;
            return -1;
        }

        return lengthRequested;

    } // end writeToFile


    /**
     * Read bytes from file.
     *
     * @param fd              file descriptor
     * @param myBuffer        byte array to contain bytes read
     * @param lengthRequested number of bytes to read
     * @return number of bytes read, 0 on EOF, or -1 on error
     */
    public static int readFromFile(int fd, byte[] myBuffer, int lengthRequested) {
        int retValue = -1;
        /////////////// DPS 8-Jan-2013  //////////////////////////////////////////////////
        /// Read from STDIN file descriptor while using IDE - get input from Messages pane.
        if (fd == STDIN && Globals.getGui() != null) {
            String input = Globals.getGui().getMessagesPane().getInputString(lengthRequested);
            byte[] bytesRead = input.getBytes();

            for (int i = 0; i < myBuffer.length; i++) {
                myBuffer[i] = (i < bytesRead.length) ? bytesRead[i] : 0;
            }
            return Math.min(myBuffer.length, bytesRead.length);
        }
        ////////////////////////////////////////////////////////////////////////////////////
        //// When running in command mode, code below works for either regular file or STDIN

        if (!FileIOData.fdInUse(fd, 0)) // Check the existence of the "read" fd
        {
            fileErrorString = "File descriptor " + fd + " is not open for reading";
            return -1;
        }
        // retrieve FileInputStream from storage
        InputStream InputStream = (InputStream) FileIOData.getStreamInUse(fd);
        try {
            // Reads up to lengthRequested bytes of data from this Input stream into an array of bytes.
            retValue = InputStream.read(myBuffer, 0, lengthRequested);
            // This method will return -1 upon EOF, but our spec says that negative
            // value represents an error, so we return 0 for EOF.  DPS 10-July-2008.
            if (retValue == -1) {
                retValue = 0;
            }
        } catch (IOException e) {
            fileErrorString = "IO Exception on read of file with fd " + fd;
            return -1;
        } catch (IndexOutOfBoundsException e) {
            fileErrorString = "IndexOutOfBoundsException on read of file with fd" + fd;
            return -1;
        }
        return retValue;

    } // end readFromFile


    /**
     * Read bytes from file.
     *
     * @param fd     file descriptor
     * @param offset where in the file to seek to
     * @param base   the point to reference 0 for start of file, 1 for current position, 2 for end of the file
     * @return -1 on error
     */
    public static int seek(int fd, int offset, int base) {
        if (!FileIOData.fdInUse(fd, 0)) // Check the existence of the "read" fd
        {
            fileErrorString = "File descriptor " + fd + " is not open for reading";
            return -1;
        }
        if (fd < 0 || fd >= SYSCALL_MAXFILES) return -1;
        Object stream = FileIOData.getStreamInUse(fd);
        if (stream == null) return -1;
        FileChannel channel;
        try {
            if (stream instanceof FileInputStream) {
                channel = ((FileInputStream) stream).getChannel();
            } else if (stream instanceof FileOutputStream) {
                channel = ((FileOutputStream) stream).getChannel();
            } else {
                return -1;
            }

            if (base == SEEK_SET) {
                offset += 0;
            } else if (base == SEEK_CUR) {
                offset += channel.position();
            } else if (base == SEEK_END) {
                offset += channel.size();
            } else {
                return -1;
            }
            if (offset < 0) {
                return -1;
            }
            channel.position(offset);
            return offset;
        } catch (IOException io) {
            return -1;
        }
    }

    /**
     * Open a file for either reading or writing. Note that read/write flag is NOT
     * IMPLEMENTED.  Also note that file permission modes are also NOT IMPLEMENTED.
     *
     * @param filename string containing filename
     * @param flags    0 for read, 1 for write
     * @return file descriptor in the range 0 to SYSCALL_MAXFILES-1, or -1 if error
     * @author Ken Vollmar
     */
    public static int openFile(String filename, int flags) {
        // Internally, a "file descriptor" is an index into a table
        // of the filename, flag, and the File???putStream associated with
        // that file descriptor.

        int retValue = -1;
        FileInputStream inputStream;
        FileOutputStream outputStream;
        int fdToUse;

        // Check internal plausibility of opening this file
        fdToUse = FileIOData.nowOpening(filename, flags);
        retValue = fdToUse; // return value is the fd
        if (fdToUse < 0) {
            return -1;
        }   // fileErrorString would have been set

        File filepath = new File(filename);
        if (!filepath.isAbsolute() && Globals.program != null && Globals.getSettings()
                .getBooleanSetting(Settings.Bool.DERIVE_CURRENT_WORKING_DIRECTORY)) {
            String parent = new File(Globals.program.getFilename()).getParent();
            filepath = new File(parent, filename);
        }
        if (flags == O_RDONLY) // Open for reading only
        {
            try {
                // Set up input stream from disk file
                inputStream = new FileInputStream(filepath);
                FileIOData.setStreamInUse(fdToUse, inputStream); // Save stream for later use
            } catch (FileNotFoundException e) {
                fileErrorString = "File " + filename + " not found, open for input.";
                retValue = -1;
            }
        } else if ((flags & O_WRONLY) != 0) // Open for writing only
        {
            // Set up output stream to disk file
            try {
                outputStream = new FileOutputStream(filepath, ((flags & O_APPEND) != 0));
                FileIOData.setStreamInUse(fdToUse, outputStream); // Save stream for later use
            } catch (FileNotFoundException e) {
                fileErrorString = "File " + filename + " not found, open for output.";
                retValue = -1;
            }
        }
        return retValue; // return the "file descriptor"

    }

    /**
     * Close the file with specified file descriptor
     *
     * @param fd the file descriptor of an open file
     */
    public static void closeFile(int fd) {
        FileIOData.close(fd);
    }

    /**
     * Reset all files -- clears out the file descriptor table.
     */
    public static void resetFiles() {
        FileIOData.resetFiles();
    }

    /**
     * Retrieve file operation or error message
     *
     * @return string containing message
     */
    public static String getFileErrorMessage() {
        return fileErrorString;
    }

    ///////////////////////////////////////////////////////////////////////
    // Private method to simply return the BufferedReader used for
    // keyboard input, redirected input, or piped input.
    // These are all equivalent in the eyes of the program because they are
    // transparent to it.  Lazy instantiation.  DPS.  28 Feb 2008

    private static BufferedReader getInputReader() {
        if (inputReader == null) {
            inputReader = new BufferedReader(new InputStreamReader(System.in));
        }
        return inputReader;
    }

    // The GUI doesn't handle lots of small messages well so I added this hacky way of buffering
    // Currently it checks to flush every instruction run
    private static String buffer = "";
    private static long lasttime = 0;
    private static void print2Gui(String output){
        long time = System.currentTimeMillis();
        if (time > lasttime) {
            Globals.getGui().getMessagesPane().postRunMessage(buffer+output);
            buffer = "";
            lasttime = time + 100;
        } else {
            buffer += output;
        }
    }
    /**
     * Flush stdout cache
     * Makes sure that messages don't get stuck in the print2Gui buffer for too long.
     */
    public static void flush(boolean force) {
        long time = System.currentTimeMillis();
        if (buffer != "" && (force || time > lasttime)){
            Globals.getGui().getMessagesPane().postRunMessage(buffer);
            buffer = "";
            lasttime = time + 100;
        }
    }


    public static Data swapData(Data in){
        Data temp = new Data(false);
        temp.fileNames = FileIOData.fileNames;
        temp.fileFlags = FileIOData.fileFlags;
        temp.streams = FileIOData.streams;
        FileIOData.fileNames = in.fileNames;
        FileIOData.fileFlags = in.fileFlags;
        FileIOData.streams = in.streams;
        return temp;
    }

    public static class Data {
        private String[] fileNames; // The filenames in use. Null if file descriptor i is not in use.
        private int[] fileFlags; // The flags of this file, 0=READ, 1=WRITE. Invalid if this file descriptor is not in use.
        public Closeable[] streams;
        public Data(boolean generate){
            if(generate) {
                fileNames = new String[SYSCALL_MAXFILES];
                fileFlags = new int[SYSCALL_MAXFILES];
                streams = new Closeable[SYSCALL_MAXFILES];
                fileNames[STDIN] = "STDIN";
                fileNames[STDOUT] = "STDOUT";
                fileNames[STDERR] = "STDERR";
                fileFlags[STDIN] = SystemIO.O_RDONLY;
                fileFlags[STDOUT] = SystemIO.O_WRONLY;
                fileFlags[STDERR] = SystemIO.O_WRONLY;
                streams[STDIN] = System.in;
                streams[STDOUT] = System.out;
                streams[STDERR] = System.err;
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Maintain information on files in use. The index to the arrays is the "file descriptor."
    // Ken Vollmar, August 2005

    private static class FileIOData {
        private static String[] fileNames = new String[SYSCALL_MAXFILES]; // The filenames in use. Null if file descriptor i is not in use.
        private static int[] fileFlags = new int[SYSCALL_MAXFILES]; // The flags of this file, 0=READ, 1=WRITE. Invalid if this file descriptor is not in use.
        private static Closeable[] streams = new Closeable[SYSCALL_MAXFILES]; // The streams in use, associated with the filenames


        // Reset all file information. Closes any open files and resets the arrays
        private static void resetFiles() {
            for (int i = 0; i < SYSCALL_MAXFILES; i++) {
                close(i);
            }
            setupStdio();
        }

        // DPS 8-Jan-2013
        private static void setupStdio() {
            fileNames[STDIN] = "STDIN";
            fileNames[STDOUT] = "STDOUT";
            fileNames[STDERR] = "STDERR";
            fileFlags[STDIN] = SystemIO.O_RDONLY;
            fileFlags[STDOUT] = SystemIO.O_WRONLY;
            fileFlags[STDERR] = SystemIO.O_WRONLY;
            streams[STDIN] = System.in;
            streams[STDOUT] = System.out;
            streams[STDERR] = System.err;
            System.out.flush();
            System.err.flush();
        }

        // Preserve a stream that is in use
        private static void setStreamInUse(int fd, Closeable s) {
            streams[fd] = s;

        }

        // Retrieve a stream for use
        private static Closeable getStreamInUse(int fd) {
            return streams[fd];

        }

        // Determine whether a given filename is already in use.
        private static boolean filenameInUse(String requestedFilename) {
            for (int i = 0; i < SYSCALL_MAXFILES; i++) {
                if (fileNames[i] != null
                        && fileNames[i].equals(requestedFilename)) {
                    return true;
                }
            }

            return false;

        }

        // Determine whether a given fd is already in use with the given flag.
        private static boolean fdInUse(int fd, int flag) {
            if (fd < 0 || fd >= SYSCALL_MAXFILES) {
                return false;
            } else if (fileNames[fd] != null && fileFlags[fd] == 0 && flag == 0) {  // O_RDONLY read-only
                return true;
            } else if (fileNames[fd] != null && ((fileFlags[fd] & flag & O_WRONLY) == O_WRONLY)) {  // O_WRONLY write-only
                return true;
            }
            return false;

        }

        // Close the file with file descriptor fd. No errors are recoverable -- if the user's
        // made an error in the call, it will come back to him.
        private static void close(int fd) {
            // Can't close STDIN, STDOUT, STDERR, or invalid fd
            if (fd <= STDERR || fd >= SYSCALL_MAXFILES)
                return;

            fileNames[fd] = null;
            // All this code will be executed only if the descriptor is open.
            if (streams[fd] != null) {
                int keepFlag = fileFlags[fd];
                Object keepStream = streams[fd];
                fileFlags[fd] = -1;
                streams[fd] = null;
                try {
                    if (keepFlag == O_RDONLY)
                        ((FileInputStream) keepStream).close();
                    else
                        ((FileOutputStream) keepStream).close();
                } catch (IOException ioe) {
                    // not concerned with this exception
                }
            } else {
                fileFlags[fd] = -1; // just to be sure... streams[fd] known to be null
            }
        }

        // Attempt to open a new file with the given flag, using the lowest available file descriptor.
        // Check that filename is not in use, flag is reasonable, and there is an available file descriptor.
        // Return: file descriptor in 0...(SYSCALL_MAXFILES-1), or -1 if error
        private static int nowOpening(String filename, int flag) {
            int i = 0;
            if (filenameInUse(filename)) {
                fileErrorString = "File name " + filename + " is already open.";
                return -1;
            }

            if (flag != O_RDONLY && flag != O_WRONLY && flag != (O_WRONLY | O_APPEND)) // Only read and write are implemented
            {
                fileErrorString = "File name " + filename + " has unknown requested opening flag";
                return -1;
            }

            while (fileNames[i] != null && i < SYSCALL_MAXFILES) {
                i++;
            } // Attempt to find available file descriptor

            if (i >= SYSCALL_MAXFILES) // no available file descriptors
            {
                fileErrorString = "File name " + filename + " exceeds maximum open file limit of " + SYSCALL_MAXFILES;
                return -1;
            }

            // Must be OK -- put filename in table
            fileNames[i] = new String(filename); // our table has its own copy of filename
            fileFlags[i] = flag;
            fileErrorString = new String("File operation OK");
            return i;
        }

    } // end private class FileIOData
}
