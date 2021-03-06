package tintor.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Bits {
	public int power32(int a) {
		return 1 << a;
	}

	public long power(int a) {
		return 1l << a;
	}

	public long mask(int a) {
		return power(a) - 1;
	}

	// ---------
	public int set(int bits, int index) {
		assert 0 <= index && index < 32;
		return bits | power32(index);
	}

	public void set(int[] bits, int index) {
		assert 0 <= index && index < bits.length * 32;
		bits[index / 32] = set(bits[index / 32], index % 32);
	}

	public long set(long bits, int index) {
		assert 0 <= index && index < 64;
		return bits | power(index);
	}

	public void set(long[] bits, int index) {
		assert 0 <= index && index < bits.length * 64;
		bits[index / 64] = set(bits[index / 64], index % 64);
	}

	// ---------

	public int clear(int bits, int index) {
		assert 0 <= index && index < 32;
		return bits & ~power32(index);
	}

	public void clear(int[] bits, int index) {
		assert 0 <= index && index < bits.length * 32;
		bits[index / 32] = clear(bits[index / 32], index % 32);
	}

	public long clear(long bits, int index) {
		assert 0 <= index && index < 64;
		return bits & ~power(index);
	}

	public void clear(long[] bits, int index) {
		assert 0 <= index && index < bits.length * 64;
		bits[index / 64] = clear(bits[index / 64], index % 64);
	}

	// ---------

	public boolean test(long bits, int index) {
		assert 0 <= index && index < 64;
		return (bits & power(index)) != 0;
	}

	public boolean test(int[] bits, int index) {
		assert 0 <= index && index < bits.length * 32;
		return test(bits[index / 32], index % 32);
	}

	public boolean test(int[] bits, int offset, int length, int index) {
		assert 0 <= index && index < length * 32;
		return test(bits[offset + index / 32], index % 32);
	}

	public boolean test(long[] bits, int index) {
		assert 0 <= index && index < bits.length * 64;
		return test(bits[index / 64], index % 64);
	}

	public boolean test(long bits0, long bits1, int index) {
		assert 0 <= index && index < 128;
		return index < 64 ? test(bits0, index) : test(bits1, index - 64);
	}

	// ---------

	public int count_leading_zeroes(long[] bits) {
		for (int i = 0; i < bits.length; i++)
			if (bits[i] != 0)
				return i * 64 + Long.numberOfTrailingZeros(bits[i]);
		return bits.length * 64;
	}

	// ---------

	public int count_before(int bits, int index) {
		return Integer.bitCount(bits & ((1 << index) - 1));
	}

	public int count_before(long bits, int index) {
		return Long.bitCount(bits & mask(index));
	}

	public int count_before(int[] bits, int index) {
		assert 0 <= index && index < bits.length * 32 : index + " vs " + bits.length;
		return count_before(bits, index / 32, index);
	}

	public int count_before(long[] bits, int index) {
		assert 0 <= index && index < bits.length * 64 : index + " vs " + bits.length;
		return count_before(bits, index / 64, index);
	}

	public int count_before(int[] bits, int hi, int lo) {
		assert 0 <= hi && hi < bits.length : hi;
		assert 0 <= lo && lo < 32 : lo;
		int q = 0;
		for (int i = 0; i < hi; i++)
			q += Integer.bitCount(bits[i]);
		return q + count_before(bits[hi], lo);
	}

	public int count_before(long[] bits, int hi, int lo) {
		assert 0 <= hi && hi < bits.length : hi;
		assert 0 <= lo && lo < 64 : lo;
		int q = 0;
		for (int i = 0; i < hi; i++)
			q += Long.bitCount(bits[i]);
		return q + count_before(bits[hi], lo);
	}

	// ---------

	public int count(int bits) {
		return Integer.bitCount(bits);
	}

	public int count(long bits) {
		return Long.bitCount(bits);
	}

	public int count(int[] bits) {
		int q = 0;
		for (int b : bits)
			q += Integer.bitCount(b);
		return q;
	}

	public int count(long[] bits) {
		int q = 0;
		for (long b : bits)
			q += Long.bitCount(b);
		return q;
	}
}