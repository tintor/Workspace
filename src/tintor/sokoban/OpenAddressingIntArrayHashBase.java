package tintor.sokoban;

import java.util.Arrays;

import tintor.common.MurmurHash3;
import tintor.common.OnlineEstimator;

// Hash table of int[N]. Very memory compact.
// Removing elements is efficient and doesn't leave garbage.
class OpenAddressingIntArrayHashBase {
	protected int[] key;
	protected final int N;
	protected int size;
	protected int mask;

	protected OpenAddressingIntArrayHashBase(int N) {
		this.N = N;
		int capacity = 8;
		key = new int[N * capacity];
		mask = capacity - 1;
	}

	public final int size() {
		return size;
	}

	public final int capacity() {
		return (mask + 1) / 8 * 5; // 62.5%
	}

	public final void clear() {
		Arrays.fill(key, 0);
		size = 0;
	}

	public final OnlineEstimator probe_distance() {
		OnlineEstimator estimator = new OnlineEstimator();
		for (int a = 0; a <= mask; a++) {
			int b = hash_of(a, key) & mask;
			estimator.add((a + mask + 1 - b) & mask);
		}
		return estimator;
	}

	public final boolean contains(int[] k) {
		int a = hash(k) & mask;
		while (true) {
			if (empty(a))
				return false;
			if (equal(a, k))
				return true;
			a = (a + 1) & mask;
		}
	}

	public final boolean contains(int[] k, int offset) {
		int a = hash(k, offset) & mask;
		while (true) {
			if (empty(a))
				return false;
			if (equal(a, k, offset))
				return true;
			a = (a + 1) & mask;
		}
	}

	// TODO get_unsafe?
	protected final int get_internal(int[] k) {
		int a = hash(k) & mask;
		while (true) {
			if (empty(a))
				return -1;
			if (equal(a, k))
				return a;
			a = (a + 1) & mask;
		}
	}

	protected final int insert_unsafe_internal(int[] k) {
		int a = hash(k) & mask;
		while (!empty(a)) {
			assert !equal(a, k);
			a = (a + 1) & mask;
		}
		set(a, k);
		assert !empty(a);
		return a;
	}

	protected final int update_unsafe_internal(int[] k) {
		int a = hash(k) & mask;
		while (!equal(a, k)) {
			assert !empty(a);
			a = (a + 1) & mask;
		}
		return a;
	}

	// returns true if insert
	protected final int insert_or_update_internal(int[] k) {
		int a = hash(k) & mask;
		while (!empty(a)) {
			if (equal(a, k))
				return ~a;
			a = (a + 1) & mask;
		}
		set(a, k);
		return a;
	}

	public final void remove_unsafe(int[] k) {
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

	protected final void remove_unsafe_internal(int a, int z) {
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
			if (between(hash_of(b, key) & mask, z, a)) {
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

	protected final void grow() {
		if (++size <= capacity())
			return;
		if (key.length * 2 <= key.length)
			throw new Error("can't grow anymore");
		int old_capacity = mask + 1;
		final int[] old_key = key;
        if (key.length * 2 >= 4*1000*1000)
            System.out.printf("[info] growing hash table to %s mb\n", key.length * 8 / 1024 / 1024);
		key = new int[key.length * 2];
		mask = mask * 2 + 1;
		assert mask == key.length / N - 1;
		reinsert_all(old_capacity, old_key);
	}

	// Can be overidden
	protected void reinsert_all(int old_capacity, int[] old_key) {
		for (int b = 0; b < old_capacity; b++)
			if (!empty(b, old_key))
				reinsert(b, old_key);
	}

	protected final int reinsert(int b, int[] old_key) {
		int a = hash_of(b, old_key) & mask;
		while (!empty(a, key))
			a = (a + 1) & mask;
		for (int i = 0; i < N; i++)
			key[N * a + i] = old_key[N * b + i];
		return a;
	}

	interface KeyPredicate {
		boolean test(int[] key, int offset);
	}

	public final void remove_if(KeyPredicate fn) {
		for (int a = mask; a >= 0; a--)
			if (!empty(a) && fn.test(key, a * N))
				remove_unsafe_internal(a, a);
	}

	protected final boolean empty(int a) {
		return empty(a, key);
	}

	protected final boolean empty(int a, int[] key) {
        switch (N) {
        case 1:
			return key[a] == 0;
        case 2:
			return key[2 * a] == 0 && key[2 * a + 1] == 0;
        case 3:
			return key[3 * a] == 0 && key[3 * a + 1] == 0 && key[3 * a + 2] == 0;
        case 4:
			return key[4 * a] == 0 && key[4 * a + 1] == 0 && key[4 * a + 2] == 0 && key[4 * a + 3] == 0;
        default:
		    for (int i = 0; i < N; i++)
				if (key[N * a + i] != 0)
					return false;
			return true;
        }
	}

	protected final boolean equal(int a, int[] k) {
        switch (N) {
        case 1:
			return key[a] == k[0];
        case 2:
			return key[2 * a] == k[0] && key[2 * a + 1] == k[1];
        case 3:
			return key[3 * a] == k[0] && key[3 * a + 1] == k[1] && key[3 * a + 2] == k[2];
        case 4:
			return key[4 * a] == k[0] && key[4 * a + 1] == k[1] && key[4 * a + 2] == k[2] && key[4 * a + 3] == k[3];
        default:
		    for (int i = 0; i < N; i++)
				if (key[N * a + i] != k[i])
					return false;
			return true;
        }
	}

	protected final boolean equal(int a, int[] k, int offset) {
		for (int i = 0; i < N; i++)
			if (key[N * a + i] != k[i + offset])
				return false;
		return true;
	}

	protected final void clear(int a) {
		for (int i = 0; i < N; i++)
			key[N * a + i] = 0;
	}

	protected final void set(int a, int[] k) {
		for (int i = 0; i < N; i++)
			key[N * a + i] = k[i];
	}

	// Can be overridden
	protected void copy(int a, int b) {
		for (int i = 0; i < N; i++)
			key[N * a + i] = key[N * b + i];
	}

	protected final int hash_of(int a, int[] array) {
		int h = 0;
		for (int i = 0; i < N; i++)
			h = MurmurHash3.step(h, array[N * a + i]);
		return MurmurHash3.fmix(h); // no length
	}

	protected static int hash(int[] k) {
		int h = 0;
		for (int d : k)
			h = MurmurHash3.step(h, d);
		return MurmurHash3.fmix(h); // no length
	}

	protected int hash(int[] k, int offset) {
		int h = 0;
		for (int i = 0; i < N; i++)
			h = MurmurHash3.step(h, k[offset + i]);
		return MurmurHash3.fmix(h); // no length
	}
}
