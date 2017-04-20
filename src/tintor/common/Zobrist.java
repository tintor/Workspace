package tintor.common;

import java.util.Arrays;
import java.util.Random;

// Note: It only returns 31-bits! non-negative integers!
public class Zobrist {
	private static int[] hash = new int[0];
	private static final Random random = new Random(0);

	public static void ensure(int size) {
		if (size <= hash.length)
			return;
		synchronized (random) {
			if (size <= hash.length)
				return;
			int z = hash.length;
			hash = Arrays.copyOf(hash, Util.roundUpPowerOf2(size));
			for (int i = z; i < hash.length; i++) {
				hash[i] = random.nextInt();
				if (hash[i] < 0)
					hash[i] = ~hash[i];
			}
		}
	}

	public static int hash(int i) {
		return hash[i];
	}

	static {
		ensure(64);
	}

	public static int hashBitset(long b, int start) {
		ensure(start + 64);
		int h = 0;
		for (int i = 0; i < 64; i++)
			if (Bits.test(b, i))
				h ^= hash[start + i];
		return h;
	}

	public static int hash(boolean[] b) {
		ensure(b.length);
		int h = 0;
		for (int i = 0; i < b.length; i++)
			if (b[i])
				h ^= hash[i];
		return h;
	}
}