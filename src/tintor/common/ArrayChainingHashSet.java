package tintor.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.Future;

@SuppressWarnings("unchecked")
public final class ArrayChainingHashSet<T> implements IHashSet<T> {
	private Object[] array;
	private int size, shift;
	private boolean enable_parallel_resize;

	public ArrayChainingHashSet() {
		this(4, false);
	}

	public ArrayChainingHashSet(int capacity) {
		this(capacity, false);
	}

	public ArrayChainingHashSet(boolean enable_parallel_resize) {
		this(4, enable_parallel_resize);
	}

	public ArrayChainingHashSet(int capacity, boolean enable_parallel_resize) {
		array = new Object[Util.roundUpPowerOf2(capacity)];
		this.enable_parallel_resize = enable_parallel_resize;
		shift = 32 - Integer.numberOfTrailingZeros(array.length);
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return array.length;
	}

	public void clear() {
		Arrays.fill(array, null);
		size = 0;
	}

	public boolean containsIdentical(T s) {
		Object o = array[index(s)];
		if (o == null)
			return false;
		if (o instanceof Object[])
			for (Object e : (Object[]) o)
				if (e == s)
					return true;
		return o == s;
	}

	public boolean contains(T s) {
		Object o = array[index(s)];
		if (o == null)
			return false;
		if (o instanceof Object[]) {
			Object[] chain = (Object[]) o;
			for (int i = 0; i < length(chain); i++)
				if (chain[i].equals(s))
					return true;
		}
		return o.equals(s);
	}

	public T get(T s) {
		Object o = array[index(s)];
		if (o == null)
			return null;
		if (o instanceof Object[]) {
			Object[] chain = (Object[]) o;
			for (int i = 0; i < length(chain); i++)
				if (chain[i].equals(s))
					return (T) chain[i];
		}
		if (o.equals(s))
			return (T) o;
		return null;
	}

	// return true if Inserted (as opposed to Updated)
	public boolean set(T s) {
		int a = index(s);
		Object o = array[a];
		if (o == null) {
			array[a] = s;
			grow();
			return true;
		}
		if (o instanceof Object[]) {
			Object[] chain = (Object[]) o;
			for (int i = 0; i < length(chain); i++)
				if (chain[i].equals(s)) {
					chain[i] = s;
					return false;
				}
			if (chain[chain.length - 1] == null) {
				chain[chain.length - 1] = s;
				grow();
				return true;
			}
			chain = Arrays.copyOf(chain, chain.length + 2);
			chain[chain.length - 2] = s;
			array[a] = chain;
			grow();
			return true;
		}
		if (o.equals(s)) {
			array[a] = s;
			return false;
		}
		array[a] = new Object[] { o, s };
		grow();
		return true;
	}

	public boolean insert(T s) {
		int a = index(s);
		Object o = array[a];
		if (o == null) {
			array[a] = s;
			grow();
			return true;
		}
		if (o instanceof Object[]) {
			Object[] chain = (Object[]) o;
			for (int i = 0; i < length(chain); i++)
				if (chain[i].equals(s))
					return false;
			if (chain[chain.length - 1] == null) {
				chain[chain.length - 1] = s;
				grow();
				return true;
			}
			chain = Arrays.copyOf(chain, chain.length + 2);
			chain[chain.length - 2] = s;
			array[a] = chain;
			grow();
			return true;
		}
		if (o.equals(s))
			return false;
		array[a] = new Object[] { o, s };
		grow();
		return true;
	}

	public boolean update(T s) {
		int a = index(s);
		Object o = array[a];
		if (o == null)
			return false;
		if (o instanceof Object[]) {
			Object[] chain = (Object[]) o;
			for (int i = 0; i < length(chain); i++)
				if (chain[i].equals(s)) {
					chain[i] = s;
					return true;
				}
			return false;
		}
		if (!o.equals(s))
			return false;
		array[a] = s;
		return true;
	}

	public boolean remove(T s) {
		int a = index(s);
		Object o = array[a];
		if (o == null)
			return false;
		if (o instanceof Object[]) {
			Object[] chain = (Object[]) o;
			for (int i = 0; i < length(chain); i++)
				if (chain[i].equals(s)) {
					size -= 1;
					if (chain[chain.length - 1] == null) {
						chain[i] = chain[chain.length - 2];
						chain = Arrays.copyOf(chain, chain.length - 2);
						array[a] = chain.length > 1 ? chain : chain[0];
						return true;
					}
					chain[i] = chain[chain.length - 1];
					chain[chain.length - 1] = null;
					if (chain.length == 2)
						array[a] = chain[0];
					return true;
				}
			return false;
		}
		if (!o.equals(s))
			return false;
		array[a] = null;
		size -= 1;
		return true;
	}

	private void grow() {
		if (++size <= array.length)
			return;

		int capacity = array.length * 2;
		if (capacity <= array.length)
			return;

		final Object[] oarray = array;
		try {
			array = new Object[capacity];
		} catch (OutOfMemoryError e) {
			Log.warning("unable to grow hash bucket array to %d: out of memory", capacity);
			return;
		}

		shift -= 1;
		int[] histogram = null;
		final int min_chunk_size = 1 << 14;
		if (enable_parallel_resize && oarray.length > min_chunk_size) {
			int tasks = Math.max(8, oarray.length / min_chunk_size);
			final int chunk_size = oarray.length / tasks;
			Future<int[]>[] future = new Future[tasks];
			for (int k = 0; k < tasks; k++) {
				final int kk = k;
				future[k] = ThreadPool.executor
						.submit(() -> resize_range(oarray, chunk_size * kk, chunk_size * (kk + 1)));
			}
			for (int k = 0; k < tasks; k++)
				histogram = merge(histogram, get(future[k]));
		} else {
			histogram = resize_range(oarray, 0, oarray.length);
		}
		if (capacity >= 1 << 18 && histogram != null)
			Log.fine("hash table resize %s -> %s: %s", Util.human(oarray.length), Util.human(array.length),
					Arrays.toString(histogram));
	}

	private static int[] merge(int[] a, int[] b) {
		if (a == null)
			return b;
		for (int i = 0; i < a.length; i++)
			a[i] += b[i];
		return a;
	}

	private static <T> T get(Future<T> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	private static int length(Object[] list) {
		return list[list.length - 1] == null ? list.length - 1 : list.length;
	}

	private int[] resize_range(Object[] old_buckets, int start, int end) {
		// writes to buckets[] will be in sequential order
		//int[] histogram = new int[10];

		ArrayList<Object> high = new ArrayList<Object>();
		ArrayList<Object> low = new ArrayList<Object>();
		for (int j = start; j < end; j++) {
			Object a = old_buckets[j];
			if (a == null)
				continue;
			if (a instanceof Object[]) {
				Object[] chain = (Object[]) a;
				high.clear();
				low.clear();
				for (int i = 0; i < length(chain); i++) {
					Object b = chain[i];
					int c = index(b);
					if (c % 2 == 0)
						low.add(b);
					else
						high.add(b);
				}
				array[j * 2] = low.size() > 1 ? low.toArray(new Object[(low.size() + 1) / 2 * 2])
						: low.size() == 1 ? low.get(0) : null;
				array[j * 2 + 1] = high.size() > 1 ? high.toArray(new Object[(high.size() + 1) / 2 * 2])
						: high.size() == 1 ? high.get(0) : null;
				//histogram[Math.min(histogram.length, chain.length) - 1] += 1;
			} else {
				Object b = a;
				int i = index(b);
				if (i % 2 == 0)
					array[j * 2] = b;
				else
					array[j * 2 + 1] = b;
				//histogram[Math.min(histogram.length, 1) - 1] += 1;
			}
		}
		return null;
	}

	private int index(Object o) {
		return o.hashCode() >>> shift;
	}

	public Iterator<T> iterator() {
		return new Iterator<T>() {
			void seek() {
				while (index < array.length && array[index] == null)
					index += 1;
			}

			public boolean hasNext() {
				seek();
				return index < array.length;
			}

			public T next() {
				seek();
				if (array[index] instanceof Object[]) {
					Object[] chain = (Object[]) array[index];
					T e = (T) chain[sub_index++];
					if (e == null) {
						index += 1;
						sub_index = 0;
						return next();
					}
					if (sub_index == chain.length) {
						sub_index = 0;
						index += 1;
					}
					return e;
				}
				T e = (T) array[index];
				index += 1;
				return e;
			}

			int index;
			int sub_index;
		};
	}

	public Remover<T> remover() {
		return new Remover<T>() {
			public T remove() {
				if (size == 0)
					return null;
				while (array[index] == null)
					index = (index + 1) & (array.length - 1);
				if (array[index] instanceof Object[]) {
					Object[] chain = (Object[]) array[index];
					T e = (T) chain[0];
					ArrayChainingHashSet.this.remove(e);
					return e;
				}
				T e = (T) array[index];
				array[index++] = null;
				return e;
			}

			int index;
		};
	}
}