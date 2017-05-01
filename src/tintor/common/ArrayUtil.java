package tintor.common;

public final class ArrayUtil {
	public final static Object[] EmptyObjectArray = new Object[0];
	public final static long[] EmptyLongArray = new long[0];
	public final static byte[] EmptyByteArray = new byte[0];
	public final static int[] EmptyIntArray = new int[0];

	public static Object[] expand(Object[] array, int pos, int length) {
		Object[] narray = new Object[array.length + length];
		System.arraycopy(array, 0, narray, 0, pos);
		System.arraycopy(array, pos, narray, pos + length, array.length - pos);
		return narray;
	}

	public static Object[] remove(Object[] array, int pos, int length) {
		if (array.length == length)
			return EmptyObjectArray;
		Object[] narray = new Object[array.length - length];
		System.arraycopy(array, 0, narray, 0, pos);
		System.arraycopy(array, pos + length, narray, pos, array.length - pos - length);
		return narray;
	}

	public static int[] expand(int[] array, int pos, int length) {
		int[] narray = new int[array.length + length];
		System.arraycopy(array, 0, narray, 0, pos);
		System.arraycopy(array, pos, narray, pos + length, array.length - pos);
		return narray;
	}

	public static int[] remove(int[] array, int pos, int length) {
		if (array.length == length)
			return EmptyIntArray;
		int[] narray = new int[array.length - length];
		System.arraycopy(array, 0, narray, 0, pos);
		System.arraycopy(array, pos + length, narray, pos, array.length - pos - length);
		return narray;
	}

	public static byte[] expand(byte[] array, int pos, int length) {
		byte[] narray = new byte[array.length + length];
		System.arraycopy(array, 0, narray, 0, pos);
		System.arraycopy(array, pos, narray, pos + length, array.length - pos);
		return narray;
	}

	public static byte[] remove(byte[] array, int pos, int length) {
		if (array.length == length)
			return EmptyByteArray;
		byte[] narray = new byte[array.length - length];
		System.arraycopy(array, 0, narray, 0, pos);
		System.arraycopy(array, pos + length, narray, pos, array.length - pos - length);
		return narray;
	}

	public static int murmurhash3_32(byte[] data, int offset, int len, int seed) {
		final int c1 = 0xcc9e2d51;
		final int c2 = 0x1b873593;

		int h = seed;
		int roundedEnd = offset + (len & 0xfffffffc); // round down to 4 byte block

		for (int i = offset; i < roundedEnd; i += 4) {
			// little endian load order
			int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16)
					| (data[i + 3] << 24);
			h = murmurhash32_step(h, k1);
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
		return fmix32(h ^ len);
	}

	public static int murmurhash3_32(int d0, int seed) {
		int h = seed;
		h = murmurhash32_step(h, d0);
		return fmix32(h); // no length
	}

	public static int murmurhash3_32(int d0, int d1, int seed) {
		int h = seed;
		h = murmurhash32_step(h, d0);
		h = murmurhash32_step(h, d1);
		return fmix32(h); // no length
	}

	public static int murmurhash3_32(int d0, int d1, int d2, int seed) {
		int h = seed;
		h = murmurhash32_step(h, d0);
		h = murmurhash32_step(h, d1);
		h = murmurhash32_step(h, d2);
		return fmix32(h); // no length
	}

	public static int murmurhash3_32(int d0, int d1, int d2, int d3, int seed) {
		int h = seed;
		h = murmurhash32_step(h, d0);
		h = murmurhash32_step(h, d1);
		h = murmurhash32_step(h, d2);
		h = murmurhash32_step(h, d3);
		return fmix32(h); // no length
	}

	public static int murmurhash3_32(int[] data, int offset, int len, int seed) {
		int h = seed;
		for (int i = offset; i < offset + len; i++)
			h = murmurhash32_step(h, data[i]);
		return fmix32(h ^ (len * 4));
	}

	public static int murmurhash32_step(int h, int d) {
		d *= 0xcc9e2d51;
		d = (d << 15) | (d >>> 17); // ROTL32(k1,15);
		d *= 0x1b873593;

		h ^= d;
		h = (h << 13) | (h >>> 19); // ROTL32(h1,13);
		h = h * 5 + 0xe6546b64;
		return h;
	}

	public static final int fmix32(int h) {
		h ^= h >>> 16;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		h *= 0xc2b2ae35;
		h ^= h >>> 16;
		return h;
	}

	public static boolean equals(byte[] first, int first_offset, byte[] second, int second_offset, int length) {
		for (int i = 0; i < length; i++)
			if (first[i + first_offset] != second[i + second_offset])
				return false;
		return true;
	}

	public static boolean equals(int[] first, int first_offset, int[] second, int second_offset, int length) {
		for (int i = 0; i < length; i++)
			if (first[i + first_offset] != second[i + second_offset])
				return false;
		return true;
	}
}
