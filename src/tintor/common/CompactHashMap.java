package tintor.common;

import java.util.Arrays;
import java.util.Iterator;

/*final class CompactArray2 {
	private final BitArray bits;
	private final int bucket_size_bits;
	private byte[][] buckets;

	CompactArray2(int bucket_count_bits, int bucket_size_bits) {
		bucket_size_bits = Math.max(6, bucket_size_bits);
		bits = new BitArray(1 << (bucket_count_bits + bucket_size_bits));
		this.bucket_size_bits = bucket_size_bits;
		buckets = new byte[1 << bucket_count_bits][];
		Arrays.fill(buckets, ArrayUtil.EmptyByteArray);
	}

	int capacity() {
		return bits.size();
	}

	void clear() {
		Arrays.fill(buckets, ArrayUtil.EmptyByteArray);
		bits.clear();
	}

	// all index values wrap around on capacity

	void set(int index, byte[] value) {

	}

	boolean get_suffix(int index, byte[] out_value) {

	}

	boolean remove(int index) {

	}

	boolean contains(int index) {

	}
}

public class CompactHashMap2 {
	private final CompactArray2 array;
	private final int key_length, value_length;
	private int size;
	private int shift;
	private final byte[] deleted_key;

	public CompactHashMap2(int key_length, int value_length, int bucket_count_bits, int bucket_size_bits,
			byte[] deleted_key) {
		assert deleted_key == null || key_length == deleted_key.length;
		this.key_length = key_length;
		this.value_length = value_length;
		array = new CompactArray2(bucket_count_bits, bucket_size_bits);
		shift = 32 - bucket_count_bits - bucket_size_bits;
		this.deleted_key = deleted_key != null ? deleted_key.clone() : null;
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return array.capacity();
	}

	public void clear() {
		array.clear();
		size = 0;
	}

	private byte[] find(byte[] key, byte[] out_value, boolean remove, boolean clone) {
		int index = index(key, 0);
		while (true) {
			if (!array.contains(index))
				return null;
			if (array.equals(index, key))
				break;
			index += 1;
		}

		if (out_value != null) {
			array.get_suffix(index, out_value);
		}
		if (remove) {
			if (!array.contains(index + 1)) {
				array.remove(index);

				// try to clean up deleted keys
				index -= 1;
				while (array.contains(index) && array.equals(index, deleted_key)) {
					array.remove(index);
					index -= 1;
				}
			} else {
				array.set(index, deleted_key);

				// try to reinsert elements after the current one (to cleanup deleted keys)
				index += 1;
				while (array.contains(index) && index( get_bit(index)) {
				
					index = (index + 1) & capacity_mask;
				}
			}
			size -= 1;
		}

		if (clone) {
			byte[] value = new byte[value_length];
			array.get_suffix(index, value);
			return value;
		}
		return ArrayUtil.EmptyByteArray;
	}

	private int index(byte[] key, int offset) {
		return ArrayUtil.murmurhash3_32(key, offset, key_length, 0/*seed* /) >>> shift;
	}
}*/

public class CompactHashMap implements Iterable<byte[]> {
	private final int key_length, value_length;
	private final int bucket_size_bits;

	private byte[][] buckets;
	private long[] bits;
	private int size;
	private int shift;
	private final byte[] deleted_key;

	public CompactHashMap(int key_length, int value_length, int bucket_count_bits, int bucket_size_bits,
			byte[] deleted_key) {
		assert deleted_key == null || key_length == deleted_key.length;
		bucket_size_bits = Math.max(6, bucket_size_bits);
		this.key_length = key_length;
		this.value_length = value_length;
		buckets = new byte[1 << bucket_count_bits][];
		Arrays.fill(buckets, ArrayUtil.EmptyByteArray);
		bits = new long[1 << (bucket_count_bits + bucket_size_bits - 6)];
		this.bucket_size_bits = bucket_size_bits;
		shift = 32 - bucket_count_bits - bucket_size_bits;
		this.deleted_key = deleted_key != null ? deleted_key.clone() : null;
	}

	public int deleted_keys() {
		int cnt = 0;
		final int length = key_length + value_length;
		for (byte[] bucket : buckets)
			for (int q = 0; q < bucket.length / length; q++)
				if (ArrayUtil.equals(deleted_key, 0, bucket, length * q, key_length))
					cnt += 1;
		return cnt;
	}

	public boolean check() {
		// check the length of every bucket against bit count
		assert shift == 1 + Integer.numberOfLeadingZeros(buckets.length) - bucket_size_bits;
		for (int i = 0; i < buckets.length; i++) {
			int q = count_bits(i * (1 << bucket_size_bits), (i + 1) * (1 << bucket_size_bits));
			assert buckets[i].length == (key_length + value_length) * q;
		}

		// make sure that every key in table is reachable
		final int length = key_length + value_length;
		byte[] key = new byte[key_length];
		for (byte[] bucket : buckets)
			for (int q = 0; q < bucket.length / length; q++) {
				System.arraycopy(bucket, length * q, key, 0, key_length);
				assert (deleted_key != null && Arrays.equals(deleted_key, key)) || contains(key);
			}
		return true;
	}

	public int size() {
		return size;
	}

	public long memory_usage() {
		long usage = Measurer.sizeOf(CompactHashMap.class);
		usage += Measurer.sizeOf(long[].class) + 8 * bits.length;
		usage += Measurer.sizeOf(byte[][].class) + (4 + Measurer.sizeOf(byte[].class)) * buckets.length;
		if (deleted_key != null)
			usage += Measurer.sizeOf(byte[].class) + deleted_key.length;
		for (byte[] bucket : buckets)
			usage += bucket.length;
		return usage;
	}

	public int capacity() {
		return bits.length * 64;
	}

	public void clear() {
		Arrays.fill(buckets, ArrayUtil.EmptyByteArray);
		Arrays.fill(bits, 0);
		size = 0;
	}

	public boolean contains(byte[] key) {
		return find(key, null, false, false) != null;
	}

	public byte[] get(byte[] key) {
		return find(key, null, false, true);
	}

	public boolean get(byte[] key, byte[] out_value) {
		return find(key, out_value, false, false) != null;
	}

	public boolean remove(byte[] key) {
		assert deleted_key != null;
		return find(key, null, true, false) != null;
	}

	private byte[] find(byte[] key, byte[] out_value, boolean remove, boolean clone) {
		assert deleted_key == null || !Arrays.equals(deleted_key, key);
		assert key.length == key_length;
		assert out_value == null || out_value.length == value_length;
		final int length = key_length + value_length;
		final int bucket_mask = ~((1 << bucket_size_bits) - 1);

		int index = index(key, 0);
		if (!get_bit(index))
			return null;
		byte[] bucket = buckets[index >>> bucket_size_bits];
		int q = count_bits(index & bucket_mask, index);

		final int capacity_mask = bits.length * 64 - 1;
		while (true) {
			if (ArrayUtil.equals(key, 0, bucket, length * q, key_length))
				break;
			index = (index + 1) & capacity_mask;
			q += 1;
			if ((index & bucket_mask) == index) {
				q = 0;
				bucket = buckets[index >>> bucket_size_bits];
			}
			if (!get_bit(index))
				return null;
		}

		if (out_value != null) {
			System.arraycopy(bucket, length * q + key_length, out_value, 0, value_length);
		}
		if (remove) {
			if (!get_bit((index + 1) & capacity_mask)) {
				clear_bit(index);
				int b = index >>> bucket_size_bits;
				buckets[b] = (bucket.length == length) ? ArrayUtil.EmptyByteArray
						: ArrayUtil.remove(bucket, length * q, length);

				// try to clean up deleted keys
				index = (index - 1) & capacity_mask;
				q -= 1;
				if (q < 0) {
					b = index >>> bucket_size_bits;
					bucket = buckets[b];
					q = bucket.length / length - 1;
				}
				while (get_bit(index) && ArrayUtil.equals(deleted_key, 0, bucket, length * q, key_length)) {
					buckets[b] = (bucket.length == length) ? ArrayUtil.EmptyByteArray
							: ArrayUtil.remove(bucket, length * q, length);
					index = (index - 1) & capacity_mask;
					q -= 1;
					if (q < 0) {
						b = index >>> bucket_size_bits;
						bucket = buckets[b];
						q = bucket.length / length - 1;
					}
				}
			} else {
				System.arraycopy(deleted_key, 0, bucket, length * q, key_length);

				// try to reinsert elements after the current one (to cleanup deleted keys)
				/*index = (index + 1) & capacity_mask;
				while (get_bit(index)) {
				
					index = (index + 1) & capacity_mask;
				}*/
			}
			size -= 1;
			assert size >= 0;
		}
		if (clone) {
			byte[] value = new byte[value_length];
			System.arraycopy(bucket, length * q + key_length, value, 0, value_length);
			return value;
		}
		return ArrayUtil.EmptyByteArray;
	}

	// returns true if same key already existed in the map
	public boolean set(byte[] key_and_value) {
		assert deleted_key == null || !ArrayUtil.equals(deleted_key, 0, key_and_value, 0, key_length);
		final int length = key_length + value_length;
		assert key_and_value.length == length;
		if (set_internal(key_and_value, 0))
			return true;

		if (size >= (int) ((long) bits.length * 64 * 3 / 5))
			rehash_internal(2);
		return false;
	}

	public void rehash() {
		rehash_internal(1);
	}

	private void rehash_internal(int mult) {
		final int length = key_length + value_length;
		byte[][] obytes = buckets;
		buckets = new byte[buckets.length * mult][];
		Arrays.fill(buckets, ArrayUtil.EmptyByteArray);
		bits = new long[bits.length * mult];
		shift -= 1;

		// TODO try to reuse bucket allocations during resize
		@SuppressWarnings("resource")
		Timer timer = new Timer().start();
		for (int i = 0; i < obytes.length; i++) {
			for (int offset = 0; offset < obytes[i].length; offset += length)
				if (deleted_key == null || !ArrayUtil.equals(obytes[i], offset, deleted_key, 0, key_length)) {
					size -= 1;
					set_internal(obytes[i], offset);
				}
			obytes[i] = null; // release bucket during resize
		}
		timer.close();
		Log.info("rehash from %s to %s buckets in %s", Util.human(buckets.length / 2), Util.human(buckets.length),
				timer.human());
	}

	private boolean set_internal(byte[] key_and_value, int offset) {
		final int length = key_length + value_length;
		assert offset + length <= key_and_value.length;
		final int bucket_mask = ~((1 << bucket_size_bits) - 1);

		int index = index(key_and_value, offset);
		int q = count_bits(index);
		int index2 = index;
		int q2 = q;

		while (get_bit(index)) {
			if (equals(index, q, key_and_value, offset)) {
				set_value(index, q, key_and_value, offset + key_length);
				return true;
			}
			index = next(index);
			q += 1;
			if ((index & bucket_mask) == index)
				q = 0;
		}

		if (deleted_key != null) {
			// scan again and try to find an earlier deleted key to reuse
			while (index2 != index) {
				if (equals(index2, q2, deleted_key, 0)) {
					size += 1;
					set_key_and_value(index2, q2, key_and_value, offset);
					return true;
				}
				index2 = next(index2);
				q2 += 1;
				if ((index2 & bucket_mask) == index2)
					q2 = 0;
			}
		}

		size += 1;
		insert(index, q, key_and_value, offset);
		return false;
	}

	boolean equals(int index, int q, byte[] key, int offset) {
		int b = index >>> bucket_size_bits;
		final int length = key_length + value_length;
		return ArrayUtil.equals(key, offset, buckets[b], length * q, key_length);
	}

	void set_value(int index, int q, byte[] value, int offset) {
		int b = index >>> bucket_size_bits;
		final int length = key_length + value_length;
		System.arraycopy(value, offset, buckets[b], length * q + key_length, value_length);
	}

	void set_key_and_value(int index, int q, byte[] key_and_value, int offset) {
		int b = index >>> bucket_size_bits;
		final int length = key_length + value_length;
		System.arraycopy(key_and_value, offset, buckets[b], length * q, value_length);
	}

	private void insert(int index, int q, byte[] key_and_value, int offset) {
		final int length = key_length + value_length;
		set_bit(index);
		int b = index >>> bucket_size_bits;
		assert length * q <= buckets[b].length : length + " vs " + q + " vs " + length * q + " vs " + buckets[b].length;
		buckets[b] = ArrayUtil.expand(buckets[b], length * q, length);
		System.arraycopy(key_and_value, offset, buckets[b], length * q, length);
	}

	private int index(byte[] key, int offset) {
		return ArrayUtil.murmurhash3_32(key, offset, key_length, 0/*seed*/) >>> shift;
	}

	private int prev(int index) {
		final int capacity_mask = bits.length * 64 - 1;
		return (index - 1) & capacity_mask;
	}

	private int next(int index) {
		final int capacity_mask = bits.length * 64 - 1;
		return (index + 1) & capacity_mask;
	}

	private int count_bits(int index) {
		final int bucket_mask = ~((1 << bucket_size_bits) - 1);
		return count_bits(index & bucket_mask, index);
	}

	private int count_bits(int start, int end) {
		assert 0 <= start && start <= end;
		int s = start / 64, e = end / 64;
		int count = 0;
		while (s < e)
			count += Bits.count(bits[s++]);
		if (end % 64 != 0)
			count += Bits.count_before(bits[e], end % 64);
		return count;
	}

	private void set_bit(int i) {
		bits[i / 64] |= 1l << i;
	}

	private void clear_bit(int i) {
		bits[i / 64] &= ~(1l << i);
	}

	private boolean get_bit(int i) {
		return (bits[i / 64] & (1l << i)) != 0;
	}

	@Override
	public Iterator<byte[]> iterator() {
		return new Iterator<byte[]>() {
			byte[] key_and_value = new byte[key_length + value_length];
			int bucket = 0;
			int index = 0;

			@Override
			public boolean hasNext() {
				while (bucket < buckets.length && buckets[bucket].length == 0)
					bucket += 1;
				return bucket < buckets.length;
			}

			@Override
			public byte[] next() {
				final int length = key_and_value.length;
				System.arraycopy(buckets[bucket], index * length, key_and_value, 0, length);
				index += 1;
				Log.info("index:%d bucket:%d length:%d buckets[bucket].length:%d", index, bucket, length,
						buckets[bucket].length);
				if (index * length == buckets[bucket].length) {
					bucket += 1;
					index = 0;
				}
				return key_and_value;
			}
		};
	}
}