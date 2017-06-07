package tintor.common;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Iterator;

@SuppressWarnings("unchecked")
public final class OpenAddressingHashSet<T> implements IHashSet<T> {
	private Object[] array;
	private int size;

	public OpenAddressingHashSet() {
		this(5);
	}

	public OpenAddressingHashSet(int capacity) {
		array = new Object[Math.max(8, Util.roundUpPowerOf2(capacity / 5 * 8))];
	}

	public int size() {
		return size;
	}

	public long deepSizeOfWithoutElements(Instrumentation ins) {
		return ins.getObjectSize(this) + ins.getObjectSize(array);
	}

	public int capacity() {
		return array.length / 8 * 5; // 62.5%
	}

	public void clear() {
		Arrays.fill(array, null);
		size = 0;
	}

	public boolean contains(T e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		while (true) {
			if (array[a] == null)
				return false;
			if (array[a].equals(e))
				return true;
			a = (a + 1) & mask;
		}
	}

	public T get(T e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		while (true) {
			if (array[a] == null)
				return null;
			if (array[a].equals(e))
				return (T) array[a];
			a = (a + 1) & mask;
		}
	}

	public boolean remove(T e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		int z = a;
		while (true) {
			if (array[a] == null)
				return false;
			if (array[a].equals(e))
				break;
			a = (a + 1) & mask;
		}
		size -= 1;

		int b = (a + 1) & mask;
		if (array[b] == null) {
			array[a] = null;
			return true;
		}

		while (true) {
			int w = (z - 1) & mask;
			if (array[w] == null)
				break;
			z = w;
		}

		while (true) {
			if (between(hash(array[b]) & mask, z, a)) {
				array[a] = array[b];
				a = b;
			}
			b = (b + 1) & mask;
			if (array[b] == null)
				break;
		}
		array[a] = null;
		return true;
	}

	private static boolean between(int c, int z, int a) {
		return (z <= a) ? (z <= c && c <= a) : (z <= c || c <= a);
	}

	// return true if this was Insert (as opposed to Update)
	public boolean set(T e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		while (array[a] != null) {
			if (array[a].equals(e)) {
				array[a] = e;
				return false;
			}
			a = (a + 1) & mask;
		}
		array[a] = e;
		grow();
		return true;
	}

	public boolean insert(T e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		while (array[a] != null) {
			if (array[a].equals(e))
				return false;
			a = (a + 1) & mask;
		}
		array[a] = e;
		grow();
		return true;
	}

	public boolean update(T e) {
		final int mask = array.length - 1;
		int a = hash(e) & mask;
		while (array[a] != null) {
			if (array[a].equals(e)) {
				array[a] = e;
				return true;
			}
			a = (a + 1) & mask;
		}
		return false;
	}

	private void grow() {
		if (++size <= capacity())
			return;
		if (array.length * 2 <= array.length)
			throw new Error("can't grow anymore");
		final Object[] oarray = array;
		array = new Object[array.length * 2];
		final int mask = array.length - 1;
		for (Object e : oarray)
			if (e != null) {
				int a = hash(e) & mask;
				while (array[a] != null)
					a = (a + 1) & mask;
				array[a] = e;
			}
	}

	private static int hash(Object a) {
		return a.hashCode();
	}

	public Iterator<T> iterator() {
		return new Iterator<T>() {
			public boolean hasNext() {
				while (true) {
					if (index >= array.length)
						return false;
					if (array[index] != null)
						return true;
					index += 1;
				}
			}

			public T next() {
				return (T) array[index++];
			}

			int index;
		};
	}

	public Remover<T> remover() {
		return new Remover<T>() {
			public T remove() {
				if (size == 0)
					return null;
				while (array[index] == null)
					index = (index + 1) & (array.length - 1);
				T e = (T) array[index];
				OpenAddressingHashSet.this.remove(e);
				return e;
			}

			private int index;
		};
	}
}