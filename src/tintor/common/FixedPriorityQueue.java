package tintor.common;

import java.util.Arrays;

public abstract class FixedPriorityQueue<T> {
	public void clear() {
		size = 0;
		index = Integer.MAX_VALUE;
		for (Bucket b : buckets)
			if (b != null)
				b.size = 0;
	}

	protected abstract int priority(T a);

	public boolean empty() {
		return size == 0;
	}

	public int size() {
		return size;
	}

	public int capacity() {
		int c = 0;
		for (Bucket b : buckets)
			if (b != null)
				c += b.array.length;
		return c;
	}

	public void decrease(T a) {
		int p = priority(a);
		if (p < index)
			index = p;
		if (buckets[p] == null)
			buckets[p] = new Bucket();
		buckets[p].push(a);
	}

	public void add(T a) {
		size += 1;
		ensure(priority(a));
		buckets[priority(a)].push(a);
	}

	private void ensure(int p) {
		if (p < index)
			index = p;
		if (p >= buckets.length) {
			int new_length = buckets.length * 2;
			while (p >= new_length)
				new_length *= 2;
			buckets = Arrays.copyOf(buckets, new_length);
		}
		if (buckets[p] == null)
			buckets[p] = new Bucket();
	}

	public T remove_min() {
		if (size-- == 0)
			throw new RuntimeException();
		while (true) {
			T s = poll_internal();
			if (priority(s) == index)
				return s;
		}
	}

	@SuppressWarnings("unchecked")
	private T poll_internal() {
		while (buckets[index] == null || buckets[index].size == 0)
			index += 1;
		return (T) buckets[index].pop();
	}

	private Bucket[] buckets = new Bucket[10];
	private int size;
	private int index;

	private static class Bucket {
		Object[] array = new Object[8];
		int size;

		void push(Object a) {
			if (size == array.length)
				array = Arrays.copyOf(array, array.length * 2);
			array[size++] = a;
		}

		Object pop() {
			return array[--size];
		}
	}
}