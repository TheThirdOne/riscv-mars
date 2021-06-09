package rars.riscv.hardware;

public class Range {
    public final int high, low;
    public Range(int low, int high){
        if(Integer.compareUnsigned(low,high) > 0) throw new IllegalArgumentException();
        this.high = high;
        this.low = low;
    }
    public boolean contains(int ptr, int size){
        return Integer.compareUnsigned(low,ptr) <= 0 && 0 <= Integer.compareUnsigned(high,ptr+size);
    }
    public Range combine(Range other){
        int low = (Integer.compareUnsigned(this.low,other.low) < 0) ?this.low:other.low;
        int high = (Integer.compareUnsigned(this.high,other.high) > 0) ?this.high:other.high;
        return new Range(low,high);
    }
    public Range limit(int size){
        int tmp = low + size;
        if(Integer.compareUnsigned(tmp,high) < 0 && Integer.compareUnsigned(tmp,low) > 0){
            return new Range(low,tmp);
        }
        return this;
    }
    public Range limitReverse(int size){
        int tmp = high - size;
        if(Integer.compareUnsigned(tmp,low) > 0 && Integer.compareUnsigned(tmp,high) < 0){
            return new Range(tmp,high);
        }
        return this;
    }
}