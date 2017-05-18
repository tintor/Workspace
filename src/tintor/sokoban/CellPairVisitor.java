package tintor.sokoban;

import tintor.common.ArrayDequeInt;
import tintor.common.BitMatrix;

public final class CellPairVisitor {
	private ArrayDequeInt deque;
	private BitMatrix visited;
	Cell[] cells;

	public CellPairVisitor(int size1, int size2, Cell[] cells) {
		deque = new ArrayDequeInt(Math.max(size1, size2));
		visited = new BitMatrix(size1, size2);
		this.cells = cells;
	}

	public void add(Cell a, Cell b) {
		assert !visited.get(a.id, b.id);
		visited.set(a.id, b.id);
		deque.addLast(pair(a, b));
	}

	public boolean try_add(Cell a, Cell b) {
		if (!visited.try_set(a.id, b.id))
			return false;
		deque.addLast(pair(a, b));
		return true;
	}

	public boolean visited(int a, int b) {
		return visited.get(a, b);
	}

	public boolean done() {
		return deque.isEmpty();
	}

	public Cell first() {
		return cells[(deque.get(0) >> 16) & 0xFFFF];
	}

	public Cell second() {
		return cells[deque.removeFirst() & 0xFFFF];
	}

	public void init() {
		deque.clear();
		visited.clear();
	}

	public void init(Cell a, Cell b) {
		deque.clear();
		visited.clear();
		deque.addFirst(pair(a, b));
		visited.set(a.id, b.id);
	}

	private static int pair(Cell a, Cell b) {
		assert 0 <= a.id && a.id < (1 << 16);
		assert 0 <= b.id && b.id < (1 << 16);
		return (a.id << 16) | b.id;
	}
}