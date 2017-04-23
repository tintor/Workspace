package tintor.common;

import java.util.Arrays;

public class BitMatrix {
	private final int[] bits;
	private final int size;

	public BitMatrix(int size) {
		bits = new int[(size * size + 31) / 32];
		this.size = size;
	}

	public void clear() {
		Arrays.fill(bits, 0);
	}

	public boolean try_set(int x, int y) {
		int i = x * size + y;
		int m = 1 << i;
		if ((bits[i / 32] & m) != 0)
			return false;
		bits[i / 32] |= m;
		return true;
	}

	public boolean get(int x, int y) {
		int i = x * size + y;
		int m = 1 << i;
		return (bits[i / 32] & m) != 0;
	}

	public void set(int x, int y) {
		int i = x * size + y;
		int m = 1 << i;
		bits[i / 32] |= m;
	}

	static {
		for (int i = 0; i < 1000; i++)
			assert (1 << (i % 32)) == (1 << i);
	}
}