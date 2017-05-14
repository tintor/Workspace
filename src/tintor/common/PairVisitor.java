package tintor.common;

public final class PairVisitor {
	private ArrayDequeInt deque;
	private BitMatrix visited;

	public PairVisitor(int size1, int size2) {
		deque = new ArrayDequeInt(Math.max(size1, size2));
		visited = new BitMatrix(size1, size2);
	}

	public void add(int a, int b) {
		assert !visited.get(a, b);
		visited.set(a, b);
		deque.addLast(pair(a, b));
	}

	public boolean try_add(int a, int b) {
		if (!visited.try_set(a, b))
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

	public int first() {
		return (deque.get(0) >> 16) & 0xFFFF;
	}

	public int second() {
		return deque.removeFirst() & 0xFFFF;
	}

	public void init() {
		deque.clear();
		visited.clear();
	}

	public void init(int a, int b) {
		deque.clear();
		visited.clear();
		deque.addFirst(pair(a, b));
		visited.set(a, b);
	}

	private static int pair(int a, int b) {
		assert 0 <= a && a < (1 << 16);
		assert 0 <= b && b < (1 << 16);
		return (a << 16) | b;
	}
}