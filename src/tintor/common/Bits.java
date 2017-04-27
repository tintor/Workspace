package tintor.common;

public class Bits {
	public static long power(int a) {
		return 1l << a;
	}

	public static long mask(int a) {
		return power(a) - 1;
	}

	public static long set(long bits, int index) {
		assert 0 <= index && index < 64;
		return bits | power(index);
	}

	public static void set(long[] bits, int index) {
		assert 0 <= index && index < bits.length * 64;
		bits[index / 64] = set(bits[index / 64], index);
	}

	public static long clear(long bits, int index) {
		assert 0 <= index && index < 64;
		return bits & ~power(index);
	}

	public static void clear(long[] bits, int index) {
		assert 0 <= index && index < bits.length * 64;
		bits[index / 64] = set(bits[index / 64], index);
	}

	public static boolean test(long bits, int index) {
		assert 0 <= index && index < 64;
		return (bits & power(index)) != 0;
	}

	public static boolean test(long[] bits, int index) {
		assert 0 <= index && index < bits.length * 64;
		return test(bits[index / 64], index);
	}

	public static boolean test(long bits0, long bits1, int index) {
		assert 0 <= index && index < 128;
		return index < 64 ? test(bits0, index) : test(bits1, index - 64);
	}

	public static int count_leading_zeroes(long[] bits) {
		for (int i = 0; i < bits.length; i++)
			if (bits[i] != 0)
				return i * 64 + Long.numberOfLeadingZeros(bits[i]);
		return bits.length;
	}

	public static int count_leading_zeroes_after(long[] bits, int index) {
		if (index % 64 != 63) {
			long b = bits[index / 64] & ~mask(index + 1);
			if (b != 0)
				return index * 64 + Long.numberOfLeadingZeros(b);
		}
		for (int i = index / 64 + 1; i < bits.length; i++)
			if (bits[i] != 0)
				return i * 64 + Long.numberOfLeadingZeros(bits[i]);
		return bits.length;
	}

	public static int count_before(long bits, int index) {
		return Long.bitCount(bits & mask(index));
	}

	public static int count_before(long[] bits, int index) {
		return count_before(bits, index / 64, index);
	}

	public static int count_before(long[] bits, int hi, int lo) {
		assert 0 <= hi && hi < bits.length;
		assert 0 <= lo && lo < 64;
		int q = 0;
		for (int i = 0; i < hi; i++)
			q += Long.bitCount(bits[i]);
		return q + count_before(bits[hi], lo);
	}

	public static int count(long bits) {
		return Long.bitCount(bits);
	}

	public static int count(long[] bits) {
		int q = 0;
		for (long b : bits)
			q += Long.bitCount(b);
		return q;
	}
}