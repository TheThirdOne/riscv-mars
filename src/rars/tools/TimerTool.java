/*
Developed by Zachary Selk at the University of Alberta (zrselk@gmail.com)

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

package rars.tools;

import rars.Globals;
import rars.riscv.hardware.AccessNotice;
import rars.riscv.hardware.MemoryAccessNotice;
import rars.riscv.hardware.Memory;
import rars.riscv.hardware.InterruptController;
import rars.riscv.hardware.ControlAndStatusRegisterFile;
import rars.riscv.hardware.AddressErrorException;

import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;


/**
 * A RARS tool used to implement a timing module and timer inturrpts.
 **/
public class TimerTool extends AbstractToolAndApplication {
    private static String heading = "Timer Tool";
    private static String version = "Version 1.0 (Zachary Selk)";
    private static final int TIME_ADDRESS = Memory.configuration.getMemoryMapBaseAddress() + 0x18;
    private static final int TIME_CMP_ADDRESS = Memory.configuration.getMemoryMapBaseAddress() + 0x20;

    // GUI window sections
    private static JPanel panelTools;
    private TimePanel timePanel;

    // Internal time values
    private static long time = 0L;      // The current time of the program (starting from 0)
    private static long startTime = 0L; // Tmp unix time used to keep track of how much time has passed
    private static long savedTime = 0L; // Accumulates time as we pause/play the timer

    // Timing threads
    private static TimeCmpDaemon timeCmp = null; // Watches for changes made to timecmp
    private Timer timer = new Timer();
    private Tick tick = new Tick(); // Runs every millisecond to decide if a timer inturrupt should be raised

    // Internal timing flags
    private static boolean updateTime = false;    // Controls when time progresses (for pausing)
    private static boolean running = false;       // true while tick thread is running

    public TimerTool() {
        super(heading + ", " + version, heading);
        startTimeCmpDaemon();
    }

    public TimerTool(String title, String heading) {
        super(title, heading);
        startTimeCmpDaemon();
    }

    public static void main(String[] args) {
        new TimerTool(heading + ", " + version, heading);
    }

    @Override
    public String getName() {
        return "Timer Tool";
    }

    // Set up the tools interface
    protected JComponent buildMainDisplayArea() {
        JPanel panelTools = new JPanel(new GridLayout(1, 2));
        timePanel = new TimePanel();

        // Adds a play button to start/resume time
        JButton playButton = new JButton("Play");
        playButton.setToolTipText("Starts the counter");
        playButton.addActionListener(
                    new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            play();
                        }
                    });
        playButton.addKeyListener(new EnterKeyListener(playButton));

        // Adds a pause button to pause time
        JButton pauseButton = new JButton("Pause");
        pauseButton.setToolTipText("Pauses the counter");
        pauseButton.addActionListener(
                     new ActionListener() {
                         public void actionPerformed(ActionEvent e) {
                             pause();
                         }
                     });
        pauseButton.addKeyListener(new EnterKeyListener(pauseButton));

        timePanel.add(playButton);
        timePanel.add(pauseButton);
        panelTools.add(timePanel);
        return panelTools;
    }

    // A daemon that watches the timecmp MMIO for any changes
    private void startTimeCmpDaemon() {
        if (timeCmp == null) {
            timeCmp = new TimeCmpDaemon();
        }
    }

    // Overwrites the empty parent method, called when the tool is closed
    protected void performSpecialClosingDuties() {
        stop();
    }


    /***************************  Timer controls  *****************************/

    public void start() {
        if (!running) {
            // Start a timer that checks to see if a timer interupt needs to be raised
            // every millisecond
            timer.schedule(tick, 0, 1);
            running = true;
        }
    }

    public void play() {
        // Gaurd against multiple plays
        if (!updateTime) {
            updateTime = true;
            startTime = System.currentTimeMillis();
        }

    }

    public void pause() {
        // Gaurd against multiple pauses
        if (updateTime) {
            updateTime = false;
            time = savedTime + System.currentTimeMillis() - startTime;
            savedTime = time;
        }
    }

    // Reset all of our counters to their default values
    protected void reset() {
        time = 0L;
        savedTime = 0L;
        startTime = System.currentTimeMillis();
        tick.updateTimecmp = true;
        timePanel.updateTime();
        tick.reset();
    }

    // Shutdown the timer (note that we keep the TimeCmpDaemon running)
    public void stop() {
        updateTime = false;
        timer.cancel();
        running = false;
        reset();
    }


    /*****************************  Timer Classes  *****************************/

    // Watches for changes made to the timecmp MMIO
    public class TimeCmpDaemon implements Observer {
        public boolean postInterrupt = false;
        public long value = 0L; // Holds the most recent value of timecmp writen to the MMIO

        public TimeCmpDaemon() {
            addAsObserver();
        }

        public void addAsObserver() {
            try {
                Globals.memory.addObserver(this, TIME_CMP_ADDRESS, TIME_CMP_ADDRESS+8);
            } catch (AddressErrorException aee) {
                System.out.println("Error while adding observer in Timer Tool");
                System.exit(0);
            }
        }

        public void update(Observable ressource, Object accessNotice) {
            MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;
            int accessType = ((AccessNotice)accessNotice).getAccessType();
            // If is was a WRITE operation
            if (accessType == 1) {
                int address = notice.getAddress();
                int value = notice.getValue();

                // Check what word was changed, then update the corrisponding information
                if (address == TIME_CMP_ADDRESS) {
                    this.value = ((this.value >> 32) << 32) + value;
                    postInterrupt = true; // timecmp was writen to
                }
                else if (address == TIME_CMP_ADDRESS+4) {
                    this.value = (this.value & 0xFFFFFFFF) + (((long)value) << 32);
                    postInterrupt = true; // timecmp was writen to
                }
            }
        }
    }

    // Runs every millisecond to decide if a timer inturrupt should be raised
    private class Tick extends TimerTask {
        public volatile boolean updateTimecmp = true;

        public void run() {
            // Check to see if the tool is connected
            // Note: "connectButton != null" short circuits the expression when null
            if (connectButton != null && connectButton.isConnected()) {
                // If the tool is not paused
                if (updateTime) {
                    // time is the difference between the last time we started the time and now, plus
                    // our time accumulator
                    time = savedTime + System.currentTimeMillis() - startTime;

                    // Write the lower and upper words of the time MMIO respectivly
                    updateMMIOControlAndData(TIME_ADDRESS, (int)(time & 0xFFFFFFFF));
                    updateMMIOControlAndData(TIME_ADDRESS+4, (int)(time >> 32));

                    // The logic for if a timer interrupt should be raised
                    // Note: if either the UTIP bit in the uie CSR or the UIE bit in the ustatus CSR
                    //      are zero then this interrupt will be stopped further on in the pipeline
                    if (time >= timeCmp.value && timeCmp.postInterrupt && bitsEnabled()) {
                        InterruptController.registerTimerInterrupt(ControlAndStatusRegisterFile.TIMER_INTERRUPT);
                        timeCmp.postInterrupt = false; // Wait for timecmp to be writen to again
                    }
                    timePanel.updateTime();
                }
            }
            // Otherwise we keep track of the last time the tool was not connected
            else {
                time = savedTime + System.currentTimeMillis() - startTime;
                startTime = System.currentTimeMillis();
            }
        }

        // Checks the control bits to see if user-level timer inturrupts are enabled
        private boolean bitsEnabled() {
            boolean utip = (ControlAndStatusRegisterFile.getValue("uie") & 0x10) == 0x10;
            boolean uie = (ControlAndStatusRegisterFile.getValue("ustatus") & 0x1) == 0x1;

            return (utip && uie);
        }

        // Set time MMIO to zero
        public void reset() {
            updateMMIOControlAndData(TIME_ADDRESS, 0);
            updateMMIOControlAndData(TIME_ADDRESS+4, 0);
        }
    }

    // Writes a word to a virtual memory address
    private synchronized void updateMMIOControlAndData(int dataAddr, int dataValue) {
        Globals.memoryAndRegistersLock.lock();
        try {
            try {
                Globals.memory.setRawWord(dataAddr, dataValue);
            } catch (AddressErrorException aee) {
                System.out.println("Tool author specified incorrect MMIO address!" + aee);
                System.exit(0);
            }
        } finally {
            Globals.memoryAndRegistersLock.unlock();
        }
    }


    /*****************************  GUI Objects  *******************************/

    // A panel that displays time
    public class TimePanel extends JPanel {
        JLabel currentTime = new JLabel("Hello world");
        public TimePanel() {
            FlowLayout fl = new FlowLayout();
            this.setLayout(fl);
            this.add(currentTime);
            updateTime();
            start();
        }

        public void updateTime() {
            currentTime.setText(String.format("%02d:%02d.%02d", time/60000, (time/1000)%60, time%100));
        }
    }

    // A help popup window on how to use this tool
    protected JComponent getHelpComponent() {
        final String helpContent =
            "Use this tool to simulate the Memory Mapped IO (MMIO) for a timing device allowing the program to utalize timer interupts. " +
            "While this tool is connected to the program it runs a clock (starting from time 0), storing the time in milliseconds. " +
            "The time is stored as a 64 bit integer and can be accessed (using a lw instruction) at 0xFFFF0018 for the lower 32 bits and 0xFFFF001B for the upper 32 bits.\n\n" +
            "Three things must be done before an interrupt can be set:\n" +
            " The address of your interrupt handler must be stored in the utvec CSR\n" +
            " The fourth bit of the uie CSR must be set to 1 (ie. ori uie, uie, 0x10)\n" +
            " The zeroth bit of the ustatus CSR must be set to 1 (ie. ori ustatus, ustatus, 0x1)\n" +
            "To set the timer you must write the time that you want the timer to go off (called timecmp) as a 64 bit integer at the address of 0xFFFF0020 for the lower 32 bits and 0xFFFF0024 for the upper 32 bits. " +
            "An interrupt will occur when the time is greater than or equal to timecmp which is a 64 bit integer (interpreted as milliseconds) stored at 0xFFFF0020 for the lower 32 bits and 0xFFFF0024 for the upper 32 bits. " +
            "To set the timer you must set timecmp (using a sw instruction) to be the time that you want the timer to go off at.\n\n" +
            "Note: the timer will only go off once after the time is reached and is not rearmed until timecmp is writen to again. " +
            "So if you are writing 64 bit values (opposed to on 32) then to avoid spuriously triggering a timer interrupt timecmp should be written to as such\n" +
            "    # a0: lower 32 bits of time\n" +
            "    # a1: upper 32 bits of time\n" +
            "    li  t0, -1\n" +
            "    la t1, timecmp\n" +
            "    sw t0, 0(t1)\n" +
            "    sw a1, 4(t1)\n" +
            "    sw a0, 0(t0)\n\n\n" +
            "(contributed by Zachary Selk, zrselk@gmail.com)";
        JButton help = new JButton("Help");
        help.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        JTextArea ja = new JTextArea(helpContent);
                        ja.setRows(20);
                        ja.setColumns(60);
                        ja.setLineWrap(true);
                        ja.setWrapStyleWord(true);
                        JOptionPane.showMessageDialog(theWindow, new JScrollPane(ja),
                                "Simulating a timing device", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
        return help;
    }
}
