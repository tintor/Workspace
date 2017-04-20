package tintor.common;

public class Bits {
	public static long power(int a) {
		assert 0 <= a && a < 64 : a;
		return 1l << a;
	}

	public static long mask(int a) {
		return power(a) - 1;
	}

	public static long set(long bits, int index) {
		return bits | power(index);
	}

	public static long clear(long bits, int index) {
		return bits & ~power(index);
	}

	public static boolean test(long bits, int index) {
		return (bits & power(index)) != 0;
	}

	public static boolean test(long bits0, long bits1, int index) {
		assert 0 <= index && index < 128;
		return index < 64 ? test(bits0, index) : test(bits1, index - 64);
	}

	public static int count_before(long bits, int index) {
		return Long.bitCount(bits & mask(index));
	}

	public static int count_before(long[] bits, int hi, int lo) {
		assert 0 <= hi && hi < bits.length;
		assert 0 <= lo && lo < 64;
		int q = 0;
		for (int i = 0; i < hi; i++)
			q += Long.bitCount(bits[i]);
		return q + count_before(bits[hi], lo);
	}
}