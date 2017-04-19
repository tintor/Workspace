package tintor.common;

import java.util.Arrays;
import java.util.Iterator;

public final class Visitor implements Iterable<Integer> {
	public Visitor(int size) {
		queue = new int[size];
		set = new boolean[size];
	}

	public void add(int a) {
		assert !set[a];
		set[a] = true;
		queue[tail++] = a;
	}

	public boolean visited(int a) {
		return set[a];
	}

	public boolean done() {
		return head == tail;
	}

	public int next() {
		assert head < tail;
		return queue[head++];
	}

	public Visitor init() {
		head = tail = 0;
		Arrays.fill(set, false);
		return this;
	}

	public Visitor init(int a) {
		Arrays.fill(set, false);
		set[a] = true;
		queue[0] = a;
		head = 0;
		tail = 1;
		return this;
	}

	public boolean[] visited() {
		return set;
	}

	@Override
	public Iterator<Integer> iterator() {
		return it;
	}

	private int head;
	private int tail;
	private int[] queue;
	private boolean[] set;

	private final Iterator<Integer> it = new Iterator<Integer>() {
		@Override
		public boolean hasNext() {
			return head < tail;
		}

		@Override
		public Integer next() {
			assert head < tail;
			return queue[head++];
		}
	};
}