package tintor.common;

public final class MurmurHash3 {
	public static int hash(byte[] data, int offset, int len, int seed) {
		final int c1 = 0xcc9e2d51;
		final int c2 = 0x1b873593;

		int h = seed;
		int roundedEnd = offset + (len & 0xfffffffc); // round down to 4 byte block

		for (int i = offset; i < roundedEnd; i += 4) {
			// little endian load order
			int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16)
					| (data[i + 3] << 24);
			h = step(h, k1);
		}

		// tail
		int k1 = 0;

		switch (len & 0x03) {
		case 3:
			k1 = (data[roundedEnd + 2] & 0xff) << 16;
			// fall through
		case 2:
			k1 |= (data[roundedEnd + 1] & 0xff) << 8;
			// fall through
		case 1:
			k1 |= (data[roundedEnd] & 0xff);
			k1 *= c1;
			k1 = (k1 << 15) | (k1 >>> 17); // ROTL32(k1,15);
			k1 *= c2;
			h ^= k1;
		}

		// finalization
		return fmix(h ^ len);
	}

	public static int hash(int[] data, int seed) {
		int h = seed;
		for (int d : data)
			h = step(h, d);
		return fmix(h ^ (data.length * 4));
	}

	public static int hash(int[] data, int offset, int len, int seed) {
		int h = seed;
		for (int i = offset; i < offset + len; i++)
			h = step(h, data[i]);
		return fmix(h ^ (len * 4));
	}

	public static int step(int h, int d) {
		d *= 0xcc9e2d51;
		d = (d << 15) | (d >>> 17); // ROTL32(k1,15);
		d *= 0x1b873593;

		h ^= d;
		h = (h << 13) | (h >>> 19); // ROTL32(h1,13);
		h = h * 5 + 0xe6546b64;
		return h;
	}

	public static int hash(int d0, int seed) {
		int h = seed;
		h = step(h, d0);
		return fmix(h ^ 4);
	}

	public static int hash(int d0, int d1, int seed) {
		int h = seed;
		h = step(h, d0);
		h = step(h, d1);
		return fmix(h ^ 8);
	}

	public static int hash(int d0, int d1, int d2, int seed) {
		int h = seed;
		h = step(h, d0);
		h = step(h, d1);
		h = step(h, d2);
		return fmix(h ^ 12);
	}

	public static int hash(int d0, int d1, int d2, int d3, int seed) {
		int h = seed;
		h = step(h, d0);
		h = step(h, d1);
		h = step(h, d2);
		h = step(h, d3);
		return fmix(h ^ 16);
	}

	public static final int fmix(int h) {
		h ^= h >>> 16;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		h *= 0xc2b2ae35;
		h ^= h >>> 16;
		return h;
	}
}