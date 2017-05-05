package tintor.common;

import java.util.Arrays;

public final class BinaryHeapInt {
	private int[] queue = new int[4];
	private int size;

	public int get(int index) {
		return queue[index];
	}

	public void set(int index, int value) {
		queue[index] = value;
	}

	public int size() {
		return size;
	}

	public void heapify() {
		for (int i = 0; i < size; i++)
			siftUp(i, queue[i]);
	}

	public void add(int e) {
		if (size == queue.length)
			queue = Arrays.copyOf(queue, queue.length * 2);
		siftUp(size++, e);
	}

	public boolean remove(int e) {
		for (int i = 0; i < queue.length; i++)
			if (queue[i] == e) {
				siftDown(i, queue[--size]);
				return true;
			}
		return false;
	}

	public int removeMin() {
		int e = queue[0];
		siftDown(0, queue[--size]);
		return e;
	}

	public void decrease(int index) {
		siftUp(index, queue[index]);
	}

	private void siftDown(int k, int x) {
		int half = size >>> 1; // loop while a non-leaf
		while (k < half) {
			int child = (k << 1) + 1; // assume left child is least
			int c = queue[child];
			int right = child + 1;
			if (right < size && c > queue[right])
				c = queue[child = right];
			if (x <= c)
				break;
			queue[k] = c;
			k = child;
		}
		queue[k] = x;
	}

	private void siftUp(int k, int x) {
		while (k > 0) {
			int parent = (k - 1) >>> 1;
			int e = queue[parent];
			if (x >= e)
				break;
			queue[k] = e;
			k = parent;
		}
		queue[k] = x;
	}
}