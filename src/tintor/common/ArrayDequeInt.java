package tintor.common;

import java.util.Arrays;

public final class ArrayDequeInt {
	private int[] array;
	private int head, tail;

	public ArrayDequeInt(int capacity) {
		if (capacity < 0)
			throw new IllegalArgumentException();
		array = new int[Util.roundUpPowerOf2(capacity)];
	}

	public int get(int index) {
		return array[(head + index) & (array.length - 1)];
	}

	public void clear() {
		head = tail = 0;
	}

	public boolean isEmpty() {
		assert check();
		return head == tail;
	}

	public int size() {
		assert check();
		return tail - head;
	}

	private boolean check() {
		return 0 <= head && head <= tail && head < array.length && tail <= array.length + head;
	}

	public int removeFirst() {
		assert check();
		int v = array[head];
		if (++head == array.length) {
			head = 0;
			tail -= array.length;
		}
		assert check();
		return v;
	}

	public int removeLast() {
		assert check();
		int v = array[--tail & (array.length - 1)];
		assert check();
		return v;
	}

	public void addFirst(int v) {
		assert check();
		if (array.length == tail - head)
			grow();
		if (--head < 0) {
			head += array.length;
			tail += array.length;
		}
		array[head] = v;
		assert check();
	}

	public void addLast(int v) {
		assert check();
		if (array.length == tail - head)
			grow();
		array[tail++ & (array.length - 1)] = v;
		assert check();
	}

	private void grow() {
		if (tail <= array.length) {
			array = Arrays.copyOf(array, array.length * 2);
		} else {
			int b = array.length - head;
			int[] a = new int[array.length * 2];
			System.arraycopy(array, head, a, 0, b);
			System.arraycopy(array, 0, a, b, head);
			head = 0;
			tail = array.length;
			array = a;
		}
	}
}