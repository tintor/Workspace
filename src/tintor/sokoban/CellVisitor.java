package tintor.sokoban;

import java.util.Arrays;
import java.util.Iterator;

public final class CellVisitor implements Iterable<Cell> {
	private int head;
	private int tail;
	private Cell[] queue;
	private boolean[] set;

	public CellVisitor(int size) {
		queue = new Cell[size];
		set = new boolean[size];
	}

	public boolean try_add(Cell a) {
		if (set[a.id])
			return false;
		set[a.id] = true;
		queue[tail++] = a;
		return true;
	}

	public boolean try_add(Cell a, int id) {
		if (set[id])
			return false;
		set[id] = true;
		queue[tail++] = a;
		return true;
	}

	public void add(Cell a) {
		assert !set[a.id];
		set[a.id] = true;
		queue[tail++] = a;
	}

	public boolean visited(int a) {
		return set[a];
	}

	public boolean visited(Cell a) {
		return set[a.id];
	}

	public boolean done() {
		return head == tail;
	}

	public Cell next() {
		assert head < tail;
		return queue[head++];
	}

	public CellVisitor init() {
		head = tail = 0;
		Arrays.fill(set, false);
		return this;
	}

	public CellVisitor init(Cell a) {
		Arrays.fill(set, false);
		set[a.id] = true;
		queue[0] = a;
		head = 0;
		tail = 1;
		return this;
	}

	public CellVisitor markVisited(Cell a) {
		set[a.id] = true;
		return this;
	}

	public boolean[] visited() {
		return set;
	}

	@Override
	public Iterator<Cell> iterator() {
		return it;
	}

	private final Iterator<Cell> it = new Iterator<Cell>() {
		@Override
		public boolean hasNext() {
			return head < tail;
		}

		@Override
		public Cell next() {
			assert head < tail;
			return queue[head++];
		}
	};
}