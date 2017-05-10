package tintor.common;

import java.util.Arrays;

public final class BitArray {
	private final int[] bits;

	public BitArray(int length) {
		bits = new int[(length + 31) / 32];
	}

	public void clear() {
		Arrays.fill(bits, 0);
	}

	public int size() {
		return bits.length * 32;
	}

	public boolean try_set(int i) {
		int m = 1 << i;
		if ((bits[i / 32] & m) != 0)
			return false;
		bits[i / 32] |= m;
		return true;
	}

	public boolean get(int i) {
		int m = 1 << i;
		return (bits[i / 32] & m) != 0;
	}

	public void set(int i) {
		int m = 1 << i;
		bits[i / 32] |= m;
	}

	static {
		for (int i = 0; i < 1000; i++)
			assert (1 << (i % 32)) == (1 << i);
	}
}