package tintor.common;

import java.util.Arrays;
import java.util.Iterator;

public final class CompactHashMap implements Iterable<byte[]> {
	private final int key_length, length;
	private byte[][] bucket;
	private long[] bit;
	private int size;
	private int shift;
	private int capacity_mask;

	public CompactHashMap(int key_length, int value_length, int bucket_count_bits) {
		this.key_length = key_length;
		this.length = key_length + value_length;
		bucket = new byte[1 << bucket_count_bits][];
		Arrays.fill(bucket, ArrayUtil.EmptyByteArray);
		bit = new long[1 << bucket_count_bits];
		shift = 32 - bucket_count_bits - 6/*bits per bucket*/;
		capacity_mask = bit.length * 64 - 1;
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return bit.length * 40; // 62.5% of 64
	}

	public void clear() {
		Arrays.fill(bucket, ArrayUtil.EmptyByteArray);
		Arrays.fill(bit, 0);
		size = 0;
	}

	public boolean contains(byte[] key) {
		assert key.length == key_length;
		int a = index(key, 0);
		if (!get_bit(a))
			return false;
		int q = count_bits(a);
		while (true) {
			if (equals(a, q, key))
				return true;
			a = next(a);
			if (!get_bit(a))
				return false;
			q = next_q(q, a);
		}
	}

	// out_value can be null
	public byte[] get(byte[] key, byte[] out_value) {
		assert key.length == key_length;
		assert out_value == null || out_value.length == length - key_length;
		int a = index(key, 0);
		if (!get_bit(a))
			return null;
		int q = count_bits(a);
		while (true) {
			if (equals(a, q, key))
				break;
			a = next(a);
			q = next_q(q, a);
			if (!get_bit(a))
				return null;
		}
		byte[] value = out_value != null ? out_value : new byte[length - key_length];
		System.arraycopy(bucket[a >>> 6], length * q + key_length, value, 0, length - key_length);
		return value;
	}

	public boolean remove(byte[] key) {
		assert key.length == key_length;
		int a = index(key, 0);
		int z = a;
		if (!get_bit(a))
			return false;
		int q = count_bits(a);
		int zq = q;
		while (true) {
			if (equals(a, q, key))
				break;
			a = next(a);
			q = next_q(q, a);
			if (!get_bit(a))
				return false;
		}
		size -= 1;

		int b = next(a);
		if (!get_bit(b)) {
			remove(a, q);
			return true;
		}
		int bq = next_q(q, b);

		while (true) {
			int w = prev(z);
			if (!get_bit(w))
				break;
			z = w;
			zq = prev_q(zq, z);
		}

		while (true) {
			int c = index(bucket[b >>> 6], length * bq);
			if ((z <= c && c <= a) || (a < z && (z <= c || c <= a))) {
				System.arraycopy(bucket[b >>> 6], length * bq, bucket[a >>> 6], length * q, length);
				a = b;
				q = bq;
			}
			b = next(b);
			if (!get_bit(b)) {
				remove(a, q);
				return true;
			}
			bq = next_q(bq, b);
		}
	}

	// return true if this was Insert (as opposed to Update)
	public boolean set(byte[] key_and_value) {
		assert length == key_and_value.length;
		int a = index(key_and_value, 0);
		int q = count_bits(a);
		while (get_bit(a)) {
			if (equals(a, q, key_and_value)) {
				set_value(a, q, key_and_value, key_length);
				return false;
			}
			a = next(a);
			q = next_q(q, a);
		}
		insert(a, q, key_and_value, 0);
		grow();
		return true;
	}

	public boolean insert(byte[] key_and_value) {
		assert length == key_and_value.length;
		int a = index(key_and_value, 0);
		int q = count_bits(a);
		while (get_bit(a)) {
			if (equals(a, q, key_and_value))
				return false;
			a = next(a);
			q = next_q(q, a);
		}
		insert(a, q, key_and_value, 0);
		grow();
		return true;
	}

	public boolean update(byte[] key_and_value) {
		assert length == key_and_value.length;
		int index = index(key_and_value, 0);
		int q = count_bits(index);
		while (get_bit(index)) {
			if (equals(index, q, key_and_value)) {
				set_value(index, q, key_and_value, key_length);
				return true;
			}
			index = next(index);
			q = next_q(q, index);
		}
		return false;
	}

	private void grow() {
		if (++size < capacity())
			return;
		if (bucket.length * 2 < bucket.length || bit.length * 2 < bit.length)
			return;

		byte[][] obuckets = bucket;
		bucket = new byte[bucket.length * 2][];
		Arrays.fill(bucket, ArrayUtil.EmptyByteArray);
		bit = new long[bit.length * 2];
		shift -= 1;
		capacity_mask = bit.length * 64 - 1;

		for (int i = 0; i < obuckets.length; i++) {
			for (int offset = 0; offset < obuckets[i].length; offset += length) {
				int a = index(obuckets[i], offset);
				int q = count_bits(a);
				while (get_bit(a)) {
					a = next(a);
					q = next_q(q, a);
				}
				// TODO try to reuse bucket allocations during resize
				insert(a, q, obuckets[i], offset);
			}
			obuckets[i] = null; // release bucket during resize
		}
	}

	private void remove(int a, int q) {
		clear_bit(a);
		bucket[a >>> 6] = ArrayUtil.remove(bucket[a >>> 6], length * q, length);
	}

	private int prev_q(int q, int index) {
		if (q > 0)
			return q - 1;
		return bucket[index >>> 6].length / length - 1;
	}

	private int next_q(int q, int index) {
		return (index & (64 - 1)) == 0 ? 0 : q + 1;
	}

	boolean equals(int a, int q, byte[] key) {
		return ArrayUtil.equals(key, 0, bucket[a >>> 6], length * q, key_length);
	}

	void set_value(int a, int q, byte[] value, int offset) {
		System.arraycopy(value, offset, bucket[a >>> 6], length * q + key_length, length - key_length);
	}

	void set(int a, int q, byte[] key_and_value, int offset) {
		System.arraycopy(key_and_value, offset, bucket[a >>> 6], length * q, length - key_length);
	}

	private void insert(int index, int q, byte[] key_and_value, int offset) {
		assert offset + length <= key_and_value.length;
		set_bit(index);
		bucket[index >>> 6] = ArrayUtil.expand(bucket[index >>> 6], length * q, length);
		System.arraycopy(key_and_value, offset, bucket[index >>> 6], length * q, length);
	}

	private int index(byte[] key, int offset) {
		assert offset + key_length <= key.length : offset + " " + key_length + " " + key.length;
		return ArrayUtil.murmurhash3_32(key, offset, key_length, 0/*seed*/) >>> shift;
	}

	private int prev(int index) {
		return (index - 1) & capacity_mask;
	}

	private int next(int index) {
		return (index + 1) & capacity_mask;
	}

	private int count_bits(int index) {
		return Bits.count_before(bit[index / 64], index);
	}

	private void set_bit(int i) {
		bit[i / 64] |= 1l << i;
	}

	private void clear_bit(int i) {
		bit[i / 64] &= ~(1l << i);
	}

	private boolean get_bit(int i) {
		return (bit[i / 64] & (1l << i)) != 0;
	}

	public Iterator<byte[]> iterator() {
		return iterator(new byte[length]);
	}

	public Iterator<byte[]> iterator(byte[] buffer) {
		return new Iterator<byte[]>() {
			byte[] key_and_value = buffer;
			int b = 0;
			int index = 0;

			@Override
			public boolean hasNext() {
				while (b < bucket.length && bucket[b].length == 0)
					b += 1;
				return b < bucket.length;
			}

			@Override
			public byte[] next() {
				final int length = key_and_value.length;
				System.arraycopy(bucket[b], index * length, key_and_value, 0, length);
				index += 1;
				if (index * length == bucket[b].length) {
					b += 1;
					index = 0;
				}
				return key_and_value;
			}
		};
	}
}