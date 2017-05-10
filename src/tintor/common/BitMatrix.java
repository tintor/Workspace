package tintor.common;

import java.util.Arrays;

public final class BitMatrix {
	private final int[] bits;
	private final int width;

	public BitMatrix(int width, int height) {
		bits = new int[(width * height + 31) / 32];
		this.width = width;
	}

	public void clear() {
		Arrays.fill(bits, 0);
	}

	public boolean try_set(int x, int y) {
		int i = y * width + x;
		int m = 1 << i;
		if ((bits[i / 32] & m) != 0)
			return false;
		bits[i / 32] |= m;
		return true;
	}

	public boolean get(int x, int y) {
		int i = y * width + x;
		int m = 1 << i;
		return (bits[i / 32] & m) != 0;
	}

	public void set(int x, int y) {
		int i = y * width + x;
		int m = 1 << i;
		bits[i / 32] |= m;
	}

	static {
		for (int i = 0; i < 1000; i++)
			assert (1 << (i % 32)) == (1 << i);
	}
}