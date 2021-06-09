package rars.tools;

import rars.Globals;
import rars.riscv.hardware.*;
import rars.util.Binary;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Observable;

@SuppressWarnings("serial")
/*
 * Didier Teifreto LIFC Universit� de franche-Comt� www.lifc.univ-fcomte.fr/~teifreto
 * didier.teifreto@univ-fcomte.fr
 */
public class DigitalLabSim extends AbstractToolAndApplication {
    private static String heading = "Digital Lab Sim";
    private static String version = " Version 1.0 (Didier Teifreto)";

    // Used to be static final variables now they are regenerated per instance
    private final int IN_ADRESS_DISPLAY_1, IN_ADRESS_DISPLAY_2, IN_ADRESS_HEXA_KEYBOARD, IN_ADRESS_COUNTER, OUT_ADRESS_HEXA_KEYBOARD;
    public static final int EXTERNAL_INTERRUPT_TIMER = 0x00000100;
    public static final int EXTERNAL_INTERRUPT_HEXA_KEYBOARD = 0x00000200;

    // GUI Interface.
    private static JPanel panelTools;
    // Seven Segment display
    private SevenSegmentPanel sevenSegPanel;
    // Keyboard
    private static int KeyBoardValueButtonClick = -1; // -1 no button click
    private HexaKeyboard hexaKeyPanel;
    private static boolean KeyboardInterruptOnOff = false;
    // Counter
    private static int CounterValueMax = 30;
    private static int CounterValue = CounterValueMax;
    private static boolean CounterInterruptOnOff = false;
    private static OneSecondCounter SecondCounter;

    public DigitalLabSim(String title, String heading) {
        super(title, heading);

        IN_ADRESS_DISPLAY_1 = Memory.configuration.getMemoryMapBaseAddress() + 0x10;
        IN_ADRESS_DISPLAY_2 = Memory.configuration.getMemoryMapBaseAddress() + 0x11;
        IN_ADRESS_HEXA_KEYBOARD = Memory.configuration.getMemoryMapBaseAddress() + 0x12;
        IN_ADRESS_COUNTER = Memory.configuration.getMemoryMapBaseAddress() + 0x13;
        OUT_ADRESS_HEXA_KEYBOARD = Memory.configuration.getMemoryMapBaseAddress() + 0x14;
    }

    public DigitalLabSim() {
        this(heading + ", " + version, heading);
    }

    public static void main(String[] args) {
        new DigitalLabSim(heading + ", " + version, heading).go();
    }

    @Override
    public String getName() {
        return "Digital Lab Sim";
    }

    protected void addAsObserver() {
        addAsObserver(IN_ADRESS_DISPLAY_1, IN_ADRESS_DISPLAY_1);
        addAsObserver(Memory.configuration.text.low,Memory.configuration.text.high);
    }

    public void update(Observable ressource, Object accessNotice) {
        MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;
        int address = notice.getAddress();
        char value = (char) notice.getValue();
        if (address == IN_ADRESS_DISPLAY_1)
            updateSevenSegment(1, value);
        else if (address == IN_ADRESS_DISPLAY_2)
            updateSevenSegment(0, value);
        else if (address == IN_ADRESS_HEXA_KEYBOARD)
            updateHexaKeyboard(value);
        else if (address == IN_ADRESS_COUNTER)
            updateOneSecondCounter(value);
        if (CounterInterruptOnOff)
            if (CounterValue > 0) {
                CounterValue--;
            } else {
                CounterValue = CounterValueMax;
                InterruptController.registerTimerInterrupt(EXTERNAL_INTERRUPT_TIMER);
            }
    }

    protected void reset() {
        sevenSegPanel.resetSevenSegment();
        hexaKeyPanel.resetHexaKeyboard();
        SecondCounter.resetOneSecondCounter();
    }

    protected JComponent buildMainDisplayArea() {
        panelTools = new JPanel(new GridLayout(1, 2));
        sevenSegPanel = new SevenSegmentPanel();
        panelTools.add(sevenSegPanel);
        hexaKeyPanel = new HexaKeyboard();
        panelTools.add(hexaKeyPanel);
        SecondCounter = new OneSecondCounter();
        return panelTools;
    }

    private synchronized void updateMMIOControlAndData(int dataAddr, int dataValue) {
        if (!this.isBeingUsedAsATool || (this.isBeingUsedAsATool && connectButton.isConnected())) {
            Globals.memoryAndRegistersLock.lock();
            try {
                try {
                    Globals.memory.setByte(dataAddr, dataValue);
                } catch (AddressErrorException aee) {
                    System.out.println("Tool author specified incorrect MMIO address!" + aee);
                    System.exit(0);
                }
            } finally {
                Globals.memoryAndRegistersLock.unlock();
            }
            if (Globals.getGui() != null && Globals.getGui().getMainPane().getExecutePane().getTextSegmentWindow().getCodeHighlighting()) {
                Globals.getGui().getMainPane().getExecutePane().getDataSegmentWindow().updateValues();
            }
        }
    }

    protected JComponent getHelpComponent() {
        final String helpContent =
                " This tool is composed of 3 parts : two seven-segment displays, an hexadecimal keyboard and counter \n" +
                        "Seven segment display\n" +
                        " Byte value at address " + Binary.intToHexString(IN_ADRESS_DISPLAY_1) + " : command right seven segment display \n " +
                        " Byte value at address " + Binary.intToHexString(IN_ADRESS_DISPLAY_2) + " : command left seven segment display \n " +
                        " Each bit of these two bytes are connected to segments (bit 0 for a segment, 1 for b segment and 7 for point \n \n" +
                        "Hexadecimal keyboard\n" +
                        " Byte value at address " + Binary.intToHexString(IN_ADRESS_HEXA_KEYBOARD) + " : command row number of hexadecimal keyboard (bit 0 to 3) and enable keyboard interrupt (bit 7) \n" +
                        " Byte value at address " + Binary.intToHexString(OUT_ADRESS_HEXA_KEYBOARD) + " : receive row and column of the key pressed, 0 if not key pressed \n" +
                        " The program has to scan, one by one, each row (send 1,2,4,8...)" +
                        " and then observe if a key is pressed (that mean byte value at adresse 0xFFFF0014 is different from zero). " +
                        " This byte value is composed of row number (4 left bits) and column number (4 right bits)" +
                        " Here you'll find the code for each key : 0x11,0x21,0x41,0x81,0x12,0x22,0x42,0x82,0x14,0x24,0x44,0x84,0x18,0x28,0x48,0x88. \n" +
                        " For exemple key number 2 return 0x41, that mean the key is on column 3 and row 1. \n" +
                        " If keyboard interruption is enable, an external interrupt is started with value 0x00000200\n \n" +
                        "Counter\n" +
                        " Byte value at address " + Binary.intToHexString(IN_ADRESS_COUNTER) + " : If one bit of this byte is set, the counter interruption is enabled.\n" +
                        " If counter interruption is enable, every 30 instructions, a timer interrupt is started with value 0x00000100.\n" +
                        "   (contributed by Didier Teifreto, dteifreto@lifc.univ-fcomte.fr)";
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
                                "Simulating the Hexa Keyboard and Seven segment display", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
        return help;
    }/* ....................Seven Segment display start here................................... */

    /* ...........................Seven segment display start here ..............................*/
    public void updateSevenSegment(int number, char value) {
        sevenSegPanel.display[number].modifyDisplay(value);
    }

    public class SevenSegmentDisplay extends JComponent {
        public char aff;

        public SevenSegmentDisplay(char aff) {
            this.aff = aff;
            this.setPreferredSize(new Dimension(60, 80));
        }

        public void modifyDisplay(char val) {
            aff = val;
            this.repaint();
        }

        public void SwitchSegment(Graphics g, char segment) {
            switch (segment) {
                case 'a': //a segment
                    int[] pxa1 = {12, 9, 12};
                    int[] pxa2 = {36, 39, 36};
                    int[] pya = {5, 8, 11};
                    g.fillPolygon(pxa1, pya, 3);
                    g.fillPolygon(pxa2, pya, 3);
                    g.fillRect(12, 5, 24, 6);
                    break;
                case 'b': //b segment
                    int[] pxb = {37, 40, 43};
                    int[] pyb1 = {12, 9, 12};
                    int[] pyb2 = {36, 39, 36};
                    g.fillPolygon(pxb, pyb1, 3);
                    g.fillPolygon(pxb, pyb2, 3);
                    g.fillRect(37, 12, 6, 24);
                    break;
                case 'c': // c segment
                    int[] pxc = {37, 40, 43};
                    int[] pyc1 = {44, 41, 44};
                    int[] pyc2 = {68, 71, 68};
                    g.fillPolygon(pxc, pyc1, 3);
                    g.fillPolygon(pxc, pyc2, 3);
                    g.fillRect(37, 44, 6, 24);
                    break;
                case 'd': // d segment
                    int[] pxd1 = {12, 9, 12};
                    int[] pxd2 = {36, 39, 36};
                    int[] pyd = {69, 72, 75};
                    g.fillPolygon(pxd1, pyd, 3);
                    g.fillPolygon(pxd2, pyd, 3);
                    g.fillRect(12, 69, 24, 6);
                    break;
                case 'e': // e segment
                    int[] pxe = {5, 8, 11};
                    int[] pye1 = {44, 41, 44};
                    int[] pye2 = {68, 71, 68};
                    g.fillPolygon(pxe, pye1, 3);
                    g.fillPolygon(pxe, pye2, 3);
                    g.fillRect(5, 44, 6, 24);
                    break;
                case 'f': // f segment
                    int[] pxf = {5, 8, 11};
                    int[] pyf1 = {12, 9, 12};
                    int[] pyf2 = {36, 39, 36};
                    g.fillPolygon(pxf, pyf1, 3);
                    g.fillPolygon(pxf, pyf2, 3);
                    g.fillRect(5, 12, 6, 24);
                    break;
                case 'g': // g segment
                    int[] pxg1 = {12, 9, 12};
                    int[] pxg2 = {36, 39, 36};
                    int[] pyg = {37, 40, 43};
                    g.fillPolygon(pxg1, pyg, 3);
                    g.fillPolygon(pxg2, pyg, 3);
                    g.fillRect(12, 37, 24, 6);
                    break;
                case 'h': // decimal point
                    g.fillOval(49, 68, 8, 8);
                    break;
            }
        }

        public void paint(Graphics g) {
            char c = 'a';
            while (c <= 'h') {
                if ((aff & 0x1) == 1)
                    g.setColor(Color.RED);
                else
                    g.setColor(Color.LIGHT_GRAY);
                SwitchSegment(g, c);
                aff = (char) (aff >>> 1);
                c++;
            }
        }
    }

    public class SevenSegmentPanel extends JPanel {
        public SevenSegmentDisplay[] display;

        public SevenSegmentPanel() {
            int i;
            FlowLayout fl = new FlowLayout();
            this.setLayout(fl);
            display = new SevenSegmentDisplay[2];
            for (i = 0; i < 2; i++) {
                display[i] = new SevenSegmentDisplay((char) (0));
                this.add(display[i]);
            }
        }

        public void modifyDisplay(int num, char val) {
            display[num].modifyDisplay(val);
            display[num].repaint();
        }

        public void resetSevenSegment() {
            int i;
            for (i = 0; i < 2; i++)
                modifyDisplay(i, (char) 0);
        }
    }

    /* ...........................Seven segment display end here ..............................*/
/* ....................Hexa Keyboard start here................................... */
    public void updateHexaKeyboard(char row) {
        int key = KeyBoardValueButtonClick;
        if ((key != -1) && ((1 << (key / 4)) == (row & 0xF))) {
            updateMMIOControlAndData(OUT_ADRESS_HEXA_KEYBOARD,
                    (char) (1 << (key / 4)) | (1 << (4 + (key % 4))));
        } else {
            updateMMIOControlAndData(OUT_ADRESS_HEXA_KEYBOARD, 0);
        }
        KeyboardInterruptOnOff = (row & 0xF0) != 0;
    }

    public class HexaKeyboard extends JPanel {
        public JButton[] button;

        public HexaKeyboard() {
            int i;
            GridLayout layout = new GridLayout(4, 4);
            this.setLayout(layout);
            button = new JButton[16];
            for (i = 0; i < 16; i++) {
                button[i] = new JButton(Integer.toHexString(i));
                button[i].setBackground(Color.WHITE);
                button[i].setMargin(new Insets(10, 10, 10, 10));
                button[i].addMouseListener(new EcouteurClick(i));
                this.add(button[i]);
            }
        }

        public void resetHexaKeyboard() {
            int i;
            KeyBoardValueButtonClick = -1;
            for (i = 0; i < 16; i++) {
                button[i].setBackground(Color.WHITE);
            }
        }

        public class EcouteurClick implements MouseListener {
            private int buttonValue;

            public EcouteurClick(int val) {
                buttonValue = val;
            }

            public void mouseEntered(MouseEvent arg0) {
            }

            public void mouseExited(MouseEvent arg0) {
            }

            public void mousePressed(MouseEvent arg0) {
            }

            public void mouseReleased(MouseEvent arg0) {
            }

            public void mouseClicked(MouseEvent arg0) {
                int i;
                if (KeyBoardValueButtonClick != -1) {//Button already pressed -> now realease
                    KeyBoardValueButtonClick = -1;
                    updateMMIOControlAndData(OUT_ADRESS_HEXA_KEYBOARD, 0);
                    for (i = 0; i < 16; i++)
                        button[i].setBackground(Color.WHITE);
                } else { // new button pressed
                    KeyBoardValueButtonClick = buttonValue;
                    button[KeyBoardValueButtonClick].setBackground(Color.GREEN);
                    if (KeyboardInterruptOnOff) {
                        InterruptController.registerExternalInterrupt(EXTERNAL_INTERRUPT_HEXA_KEYBOARD);
                    }

                }
            }
        }
    }

    /* ....................Hexa Keyboard end here................................... */
/* ....................Timer start here................................... */
    public void updateOneSecondCounter(char value) {
        if (value != 0) {
            CounterInterruptOnOff = true;
            CounterValue = CounterValueMax;
        } else {
            CounterInterruptOnOff = false;
        }
    }

    public class OneSecondCounter {
        public OneSecondCounter() {
            CounterInterruptOnOff = false;
        }

        public void resetOneSecondCounter() {
            CounterInterruptOnOff = false;
            CounterValue = CounterValueMax;
        }
    }
}
