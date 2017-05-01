package tintor.sokoban;

import tintor.common.ArrayUtil;

// maps int[N] -> long
public final class OpenAddressingIntArrayHashMap {
	private int[] key;
	private long[] value;
	private int size;
	private final int N;

	public OpenAddressingIntArrayHashMap(int N) {
		this.N = N;
		int capacity = 8;
		key = new int[N * capacity];
		value = new long[capacity];
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return value.length / 8 * 5; // 62.5%
	}

	public void clear() {
		size = 0;
	}

	public long get(int[] k) {
		final int mask = value.length - 1;
		int a = hash(k) & mask;
		while (true) {
			if (empty(a))
				return 0;
			if (equal(a, k))
				return value[a];
			a = (a + 1) & mask;
		}
	}

	public void insert_unsafe(int[] k, long v) {
		assert v != 0;
		final int mask = value.length - 1;
		int a = hash(k) & mask;
		while (!empty(a)) {
			assert !equal(a, k);
			a = (a + 1) & mask;
		}
		set(a, k);
		value[a] = v;
		grow();
	}

	public void update_unsafe(int[] k, long v) {
		assert v != 0;
		final int mask = value.length - 1;
		int a = hash(k) & mask;
		while (!equal(a, k)) {
			assert !empty(a);
			a = (a + 1) & mask;
		}
		value[a] = v;
	}

	// returns true if insert
	public boolean insert_or_update(int[] k, long v) {
		assert v != 0;
		final int mask = value.length - 1;
		int a = hash(k) & mask;
		while (!empty(a)) {
			if (equal(a, k)) {
				value[a] = v;
				return false;
			}
			a = (a + 1) & mask;
		}
		set(a, k);
		value[a] = v;
		grow();
		return true;
	}

	public void remove_unsafe(int[] k) {
		final int mask = value.length - 1;
		int a = hash(k) & mask;
		int z = a;
		if (!equal(a, k))
			while (true) {
				assert !empty(a);
				a = (a + 1) & mask;
				if (equal(a, k))
					break;
			}
		remove_unsafe_internal(a, z);
	}

	private void remove_unsafe_internal(int a, int z) {
		final int mask = value.length - 1;
		size -= 1;

		int b = (a + 1) & mask;
		if (empty(b)) { // common case
			clear(a);
			return;
		}

		while (true) {
			int w = (z - 1) & mask;
			if (empty(w))
				break;
			z = w;
		}

		while (true) {
			if (between(hash_of(b) & mask, z, a)) {
				copy(a, b);
				a = b;
			}
			b = (b + 1) & mask;
			if (empty(b))
				break;
		}
		clear(a);
	}

	private static boolean between(int c, int z, int a) {
		return (z <= a) ? (z <= c && c <= a) : (z <= c || c <= a);
	}

	private void grow() {
		if (++size <= capacity())
			return;
		if (key.length * 2 <= key.length)
			throw new Error("can't grow anymore");
		final int[] old_key = key;
		final long[] old_value = value;
		key = new int[key.length * 2];
		value = new long[value.length * 2];
		final int mask = value.length - 1;
		for (int b = 0; b < old_value.length; b++) {
			long v = old_value[b];
			if (v == 0)
				continue;
			int a = hash_of(b, old_key) & mask;
			while (!empty(a))
				a = (a + 1) & mask;
			for (int i = 0; i < N; i++)
				key[N * a + i] = old_key[N * b + i];
			value[a] = v;
		}
	}

	private boolean empty(int a) {
		return value[a] == 0;
	}

	private boolean equal(int a, int[] k) {
		for (int i = 0; i < N; i++)
			if (key[N * a + i] != k[i])
				return false;
		return true;
	}

	private void clear(int a) {
		for (int i = 0; i < N; i++)
			key[N * a + i] = 0;
		value[a] = 0;
	}

	private void set(int a, int[] k) {
		for (int i = 0; i < N; i++)
			key[N * a + i] = k[i];
	}

	private void copy(int a, int b) {
		for (int i = 0; i < N; i++)
			key[N * a + i] = key[N * b + i];
		value[a] = value[b];
	}

	private int hash_of(int a) {
		return hash_of(a, key);
	}

	private int hash_of(int a, int[] array) {
		int h = 0;
		for (int i = 0; i < N; i++)
			h = ArrayUtil.murmurhash32_step(h, array[N * a + i]);
		return ArrayUtil.fmix32(h);
	}

	private static int hash(int[] k) {
		int h = 0;
		for (int d : k)
			h = ArrayUtil.murmurhash32_step(h, d);
		return ArrayUtil.fmix32(h);
	}
}