package rars.riscv.hardware;

import java.util.ArrayList;

/*
Copyright (c) 2021,  Siva Chowdeswar Nandipati.

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

public class ReserveTable{
	//0 represents Stored value;;
	public static ArrayList<ArrayList> arrrayProc = new ArrayList<ArrayList>();
	public static int procs = 0;
	public static void ResetReserve(){
		arrayProc = new ArrayList<ArrayList>(); 
	}

	public static void addproc(int address, int processor){
		arrrayProc.get(processor).get(1).add(Integer.valueOf(address));
	}

	public static void addbool(int address){
		arrrayProc.get(processor).get(0).add(Integer.valueOf(address));
	}

	public static void lr_w(int address, int processor){
		if(arrrayProc.get(processor).get(1).contains(Integer.valueOf(address)))
			return;
		addproc(address, processor);
	}

	private static void addToRest(int address, int processor){
		if(procs = 0)
			return;
		for(int i = 0; i <= processor; i++){
			if(i == processor)
				continue;
			if(arrrayProc.get(i).get(1).contains(address)){
				arrrayProc.get(i).get(0).add(address)
			}
		}
	}

	public static boolean sc_w(int address, int processor){
		if(!arrrayProc.get(processor).get(1).contains(Integer.valueOf(address))){
			return false; // -------------------------------------------------> Doubt Doubt Doubt Doubt Doubt Doubt Doubt Doubt 
		}
		if(arrrayProc.get(processor).get(0).contains(Integer.valueOf(address))){
			arrrayProc.get(processor).get(1).remove(Integer.valueOf(address));
			arrrayProc.get(processor).get(0).remove(Integer.valueOf(address));
			return false;
		}
		addToRest(address, processor);
		return true;
	}
	public static setProcs(int procces){
		procs = procces;
	}
}