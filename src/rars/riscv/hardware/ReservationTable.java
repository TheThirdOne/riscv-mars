package rars.riscv.hardware;

import java.util.ArrayList;
import java.util.function.Predicate;

/*
Copyright (c) 2021, Siva Chowdeswar Nandipati & Giancarlo Pernudi Segura.

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

public class ReservationTable {
	private ArrayList<ReservationTableEntry> table;
	public final static int capacity = 8;
	private final static int doubleAlignMask = ~0x7;

	public enum bitWidth {
		word,
		doubleWord
	}

	protected class ReservationTableEntry {
		protected Integer address;
		protected bitWidth width;

		public ReservationTableEntry(Integer address, bitWidth width) {
			this.address = address;
			this.width = width;
		}

		public boolean equals(Object o) {
			ReservationTableEntry other = (ReservationTableEntry) o;
			return address.equals(other.address) && width.equals(other.width);
		}
	}

	public ReservationTable() {
		table = new ArrayList<ReservationTableEntry>();
	}

	public void reserveAddress(int address, bitWidth width) {
		ReservationTableEntry newEntry = new ReservationTableEntry(address, width);
		if(table.contains(newEntry))
			return;
		if (table.size() == capacity)
			table.remove(0);
		table.add(newEntry);
	}

	public void unreserveAddress(int address, bitWidth width) {
		Predicate<ReservationTableEntry> filter = entry ->
				(entry.address == address && entry.width == width)
				|| ((address & doubleAlignMask) == entry.address && entry.width == bitWidth.doubleWord);
		table.removeIf(filter);
	}

	public boolean contains(int address, bitWidth width) {
		for (ReservationTableEntry entry : table) {
			if ((entry.address == address && entry.width == width)
					|| ((address & doubleAlignMask) == entry.address && entry.width == bitWidth.doubleWord))
				return true;
		}
		return false;
	}

	public Integer[] getAddresses() {
		Integer[] addresses = new Integer[capacity];
		for (int i = 0; i < capacity; i++) {
			try {
				addresses[i] = this.table.get(i).address;
			} catch (IndexOutOfBoundsException e) {
				addresses[i] = 0;
			}
		}
		return addresses;
	}

	public bitWidth[] getWidths() {
		bitWidth[] addresses = new bitWidth[capacity];
		for (int i = 0; i < capacity; i++) {
			try {
				addresses[i] = this.table.get(i).width;
			} catch (IndexOutOfBoundsException e) {
				addresses[i] = null;
			}
		}
		return addresses;
	}
}
