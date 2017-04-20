package tintor.common;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public class BinaryHeap<T extends Comparable<T>> {
	private Object[] queue = new Object[4];
	private int size;

	public T get(int index) {
		return (T) queue[index];
	}

	public int size() {
		return size;
	}

	public void add(T e) {
		if (size == queue.length)
			queue = Arrays.copyOf(queue, queue.length * 2);
		siftUp(size++, e);
	}

	public boolean remove(T e) {
		for (int i = 0; i < queue.length; i++)
			if (queue[i] == e) {
				siftDown(i, (T) queue[--size]);
				queue[size] = null; // remove reference for GC
				return true;
			}
		return false;
	}

	public T removeMin() {
		T e = (T) queue[0];
		siftDown(0, (T) queue[--size]);
		queue[size] = null; // remove reference for GC
		return e;
	}

	public void decrease(int index) {
		siftUp(index, (T) queue[index]);
	}

	private void siftDown(int k, T x) {
		int half = size >>> 1; // loop while a non-leaf
		while (k < half) {
			int child = (k << 1) + 1; // assume left child is least
			T c = (T) queue[child];
			int right = child + 1;
			if (right < size && c.compareTo((T) queue[right]) > 0)
				c = (T) queue[child = right];
			if (x.compareTo(c) <= 0)
				break;
			queue[k] = c;
			k = child;
		}
		queue[k] = x;
	}

	private void siftUp(int k, T x) {
		while (k > 0) {
			int parent = (k - 1) >>> 1;
			T e = (T) queue[parent];
			if (x.compareTo(e) >= 0)
				break;
			queue[k] = e;
			k = parent;
		}
		queue[k] = x;
	}
}