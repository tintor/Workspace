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

	public int first() {
		return Bits.count_leading_zeroes(bits);
	}

	public int next(int index) {
		return Bits.count_leading_zeroes_after(bits, index);
	}

	public int end() {
		return bits.length * 64;
	}

	public boolean contains(int index) {
		return Bits.test(bits, index);
	}

	public T get(int index) {
		if (!Bits.test(bits, index))
			return null;
		return (T) values[Bits.count_before(bits, index)];
	}

	public void set(int index, T value) {
		if (value == null) {
			remove(index);
			return;
		}
		if (index >= bits.length * 64)
			bits = Arrays.copyOf(bits, Util.roundUpPowerOf2((index + 63) / 64));
		int q = Bits.count_before(bits, index);
		if (!Bits.test(bits, index)) {
			Bits.set(bits, index);
			values = ArrayUtil.expand(values, q, 1);
		}
		values[q] = value;
	}

	public void remove(int index) {
		if (Bits.test(bits, index)) {
			Bits.clear(bits, index);
			ArrayUtil.remove(values, Bits.count_before(bits, index), 1);
		}
	}

	public Object[] values() {
		return values;
	}
}