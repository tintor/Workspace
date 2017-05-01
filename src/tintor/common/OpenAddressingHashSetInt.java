package tintor.common;

import java.util.Arrays;

import tintor.common.IHashSet.Remover;

interface IteratorInt {
	boolean hasNext();

	int next();
}

public final class OpenAddressingHashSetInt {
	private int[] array;
	private int size;
	private static final int Empty = Integer.MIN_VALUE;

	public OpenAddressingHashSetInt() {
		this(5);
	}

	public OpenAddressingHashSetInt(int capacity) {
		array = new int[Math.max(8, Util.roundUpPowerOf2(capacity / 5 * 8))];
		Arrays.fill(array, Empty);
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return array.length / 8 * 5; // 62.5%
	}

	public void clear() {
		Arrays.fill(array, Empty);
		size = 0;
	}

	public boolean contains(int e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		while (true) {
			if (array[a] == Empty)
				return false;
			if (array[a] == e)
				return true;
			a = (a + 1) & mask;
		}
	}

	public int get(int e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		while (true) {
			if (array[a] == Empty)
				return Empty;
			if (array[a] == e)
				return array[a];
			a = (a + 1) & mask;
		}
	}

	public boolean remove(int e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		int z = a;
		while (true) {
			if (array[a] == Empty)
				return false;
			if (array[a] == e)
				break;
			a = (a + 1) & mask;
		}
		size -= 1;

		int b = (a + 1) & mask;
		if (array[b] == Empty) {
			array[a] = Empty;
			return true;
		}

		while (true) {
			int w = (z - 1) & mask;
			if (array[w] == Empty)
				break;
			z = w;
		}

		while (true) {
			int c = hash(array[b]) & mask;
			if ((z <= c && c <= a) || (a < z && (z <= c || c <= a))) {
				array[a] = array[b];
				a = b;
			}
			b = (b + 1) & mask;
			if (array[b] == Empty)
				break;
		}
		array[a] = Empty;
		return true;
	}

	public boolean insert(int e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		while (array[a] != Empty) {
			if (array[a] == e)
				return false;
			a = (a + 1) & mask;
		}
		array[a] = e;
		grow();
		return true;
	}

	private void grow() {
		if (++size <= capacity())
			return;
		if (array.length * 2 <= array.length)
			throw new Error("can't grow anymore");
		final int[] oarray = array;
		array = new int[array.length * 2];
		Arrays.fill(array, Empty);
		final int mask = array.length - 1;
		for (int e : oarray)
			if (e != Empty) {
				int a = hash(e) & mask;
				while (array[a] != Empty)
					a = (a + 1) & mask;
				array[a] = e;
			}
	}

	private static int hash(int a) {
		return ArrayUtil.fmix32(a);
	}

	public IteratorInt iterator() {
		return new IteratorInt() {
			public boolean hasNext() {
				while (true) {
					if (index >= array.length)
						return false;
					if (array[index] != Empty)
						return true;
					index += 1;
				}
			}

			public int next() {
				return array[index++];
			}

			int index;
		};
	}

	public Remover<Integer> remover() {
		return new Remover<Integer>() {
			public Integer remove() {
				if (size == 0)
					return null;
				while (array[index] == Empty)
					index = (index + 1) & (array.length - 1);
				int e = array[index];
				OpenAddressingHashSetInt.this.remove(e);
				return e;
			}

			private int index;
		};
	}
}