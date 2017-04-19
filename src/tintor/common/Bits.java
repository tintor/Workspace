package tintor.common;

public class Bits {
	public static int mask(int a) {
		return (1 << a) - 1;
	}

	public static long lmask(int a) {
		return (1l << a) - 1;
	}

	public static long set(long bits, int index) {
		return bits | (1l << index);
	}

	public static long clear(long bits, int index) {
		return bits & ~(1l << index);
	}

	public static boolean test(long bits, int index) {
		return (bits & (1l << index)) != 0;
	}

	public static int count_before(long bits, int index) {
		return Long.bitCount(bits & lmask(index));
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