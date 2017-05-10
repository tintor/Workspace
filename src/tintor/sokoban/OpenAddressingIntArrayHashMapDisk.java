package tintor.sokoban;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.function.Predicate;

import tintor.common.MurmurHash3;
import tintor.common.Util;

// Maps int[N] -> long. Very memory compact.
// Can store values in memory or on disk.
// Removing elements is efficient and doesn't leave garbage.
public final class OpenAddressingIntArrayHashMapDisk {
	private int[] key;
	private FileChannel file;
	private final ByteBuffer buffer = ByteBuffer.allocate(8);
	private final int N;
	private int size;
	private int mask;

	public OpenAddressingIntArrayHashMapDisk(int N) {
		this.N = N;
		int capacity = 8;
		key = new int[N * capacity];
		mask = capacity - 1;
		file = Util.newTempFile();
	}

	@Override
	protected void finalize() {
		Util.close(file);
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return (mask + 1) / 8 * 5; // 62.5%
	}

	public void clear() {
		Arrays.fill(key, 0);
		size = 0;
	}

	public boolean contains(int[] k) {
		int a = hash(k) & mask;
		while (true) {
			if (empty(a))
				return false;
			if (equal(a, k))
				return true;
			a = (a + 1) & mask;
		}
	}

	public long get(int[] k) {
		int a = hash(k) & mask;
		while (true) {
			if (empty(a))
				return 0;
			if (equal(a, k))
				return value(a);
			a = (a + 1) & mask;
		}
	}

	public void insert_unsafe(int[] k, long v) {
		assert v != 0;
		int a = hash(k) & mask;
		while (!empty(a)) {
			assert !equal(a, k);
			a = (a + 1) & mask;
		}
		set(a, k);
		set_value(a, v);
		grow();
	}

	public void update_unsafe(int[] k, long v) {
		assert v != 0;
		int a = hash(k) & mask;
		while (!equal(a, k)) {
			assert !empty(a);
			a = (a + 1) & mask;
		}
		set_value(a, v);
	}

	// returns true if insert
	public boolean insert_or_update(int[] k, long v) {
		assert v != 0;
		int a = hash(k) & mask;
		while (!empty(a)) {
			if (equal(a, k)) {
				set_value(a, v);
				return false;
			}
			a = (a + 1) & mask;
		}
		set(a, k);
		set_value(a, v);
		grow();
		return true;
	}

	public void remove_unsafe(int[] k) {
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

	private void grow() {
		if (++size <= capacity())
			return;
		if (key.length * 2 <= key.length)
			throw new Error("can't grow anymore");
		final int[] old_key = key;
		key = new int[key.length * 2];
		mask = mask * 2 + 1;
		assert mask == key.length / N - 1;

		final FileChannel old_file = file;
		file = Util.newTempFile();
		for (int b = 0; b < old_key.length / N; b++) {
			if (empty(b, old_key))
				continue;
			int a = copy_unsafe(b, old_key);
			buffer.position(0);
			Util.read(old_file, buffer, b * 8L);
			buffer.position(0);
			Util.write(file, buffer, a * 8L);
		}
		Util.close(old_file);
	}

	public int copy_unsafe(int b, int[] old_key) {
		int a = hash_of(b, old_key) & mask;
		while (!empty(a, key))
			a = (a + 1) & mask;
		for (int i = 0; i < N; i++)
			key[N * a + i] = old_key[N * b + i];
		return a;
	}

	public void remove_if(Predicate<int[]> fn) {
		int[] key_copy = new int[N];
		for (int a = mask; a >= 0; a--)
			if (!empty(a)) {
				// TODO avoid copying
				System.arraycopy(key, a * N, key_copy, 0, N);
				if (fn.test(key_copy))
					remove_unsafe_internal(a, a);
			}
	}

	private long value(int a) {
		buffer.position(0);
		Util.read(file, buffer, a * 8L);
		return buffer.getLong(0);
	}

	private void set_value(int a, long v) {
		buffer.position(0);
		buffer.putLong(0, v);
		Util.write(file, buffer, a * 8L);
	}

	private boolean empty(int a) {
		return empty(a, key);
	}

	private boolean empty(int a, int[] key) {
		for (int i = 0; i < N; i++)
			if (key[N * a + i] != 0)
				return false;
		return true;
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
		assert empty(a);
	}

	private void set(int a, int[] k) {
		for (int i = 0; i < N; i++)
			key[N * a + i] = k[i];
	}

	private void copy(int a, int b) {
		for (int i = 0; i < N; i++)
			key[N * a + i] = key[N * b + i];
		buffer.position(0);
		Util.read(file, buffer, b * 8L);
		buffer.position(0);
		Util.write(file, buffer, a * 8L);
	}

	private int hash_of(int a, int[] array) {
		int h = 0;
		for (int i = 0; i < N; i++)
			h = MurmurHash3.step(h, array[N * a + i]);
		return MurmurHash3.fmix(h); // no length
	}

	private static int hash(int[] k) {
		int h = 0;
		for (int d : k)
			h = MurmurHash3.step(h, d);
		return MurmurHash3.fmix(h); // no length
	}
}
