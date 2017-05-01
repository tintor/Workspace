package tintor.common;

import java.util.Arrays;

// Map from non-negative integer to T (compact version)
@SuppressWarnings("unchecked")
public final class CompactArray<T> {
	private Object[] values = ArrayUtil.EmptyObjectArray;
	private long[] bits = ArrayUtil.EmptyLongArray;

	public int size() {
		assert Bits.count(bits) == values.length;
		return values.length;
	}

	public int begin() {
		return Bits.count_leading_zeroes(bits);
	}

	public int next(int index) {
		int a = nextFast(index);
		assert a == nextSlow(index);
		return a;
	}

	int nextFast(int index) {
		int i = index / 64;
		if (index % 64 < 63) {
			long current = bits[i] & ~((1l << (index + 1)) - 1);
			if (current != 0)
				return i * 64 + Long.numberOfTrailingZeros(current);
		}
		for (i += 1; i < bits.length; i++)
			if (bits[i] != 0)
				return i * 64 + Long.numberOfTrailingZeros(bits[i]);
		return bits.length * 64;
	}

	int nextSlow(int index) {
		for (index += 1; index < bits.length * 64; index++)
			if (Bits.test(bits, index))
				break;
		return index;
	}

	public int end() {
		return bits.length * 64;
	}

	public boolean contains(int index) {
		return index < bits.length * 64 && Bits.test(bits, index);
	}

	public T get(int index) {
		if (index >= bits.length * 64 || !Bits.test(bits, index))
			return null;
		assert Bits.count(bits) == values.length;
		return (T) values[Bits.count_before(bits, index)];
	}

	public void set(int index, T value) {
		if (value == null) {
			remove(index);
			return;
		}
		if (index >= bits.length * 64)
			bits = Arrays.copyOf(bits, Util.roundUpPowerOf2(index / 64 + 1));
		int q = Bits.count_before(bits, index);
		if (!Bits.test(bits, index)) {
			Bits.set(bits, index);
			values = ArrayUtil.expand(values, q, 1);
		}
		values[q] = value;
		assert Bits.count(bits) == values.length;
	}

	public void remove(int index) {
		if (Bits.test(bits, index)) {
			Bits.clear(bits, index);
			values = ArrayUtil.remove(values, Bits.count_before(bits, index), 1);
		}
	}

	public Object[] values() {
		return values;
	}
}