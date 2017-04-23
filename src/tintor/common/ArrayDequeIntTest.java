package tintor.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArrayDequeIntTest {
	@Test
	public void test() {
		ArrayDequeInt q = new ArrayDequeInt(2);
		assertEquals(0, q.size());
		q.addLast(1);
		q.addLast(2);
		assertEquals(1, q.removeFirst());
		q.addLast(3);
		assertEquals(2, q.size());
		assertEquals(2, q.removeFirst());
		assertEquals(3, q.removeFirst());
		assertEquals(0, q.size());
	}

	@Test
	public void test2() {
		ArrayDequeInt q = new ArrayDequeInt(2);
		q.addLast(1);
		q.addLast(2);
		q.addLast(3);
		assertEquals(3, q.size());
		assertEquals(1, q.removeFirst());
		assertEquals(2, q.removeFirst());
		assertEquals(3, q.removeFirst());
		assertEquals(0, q.size());
	}

	@Test
	public void test3() {
		ArrayDequeInt q = new ArrayDequeInt(2);
		assertEquals(0, q.size());
		q.addFirst(1);
		q.addFirst(2);
		assertEquals(1, q.removeLast());
		q.addFirst(3);
		assertEquals(2, q.size());
		assertEquals(2, q.removeLast());
		assertEquals(3, q.removeLast());
		assertEquals(0, q.size());
	}

	@Test
	public void test4() {
		ArrayDequeInt q = new ArrayDequeInt(2);
		q.addFirst(1);
		q.addFirst(2);
		q.addFirst(3);
		assertEquals(3, q.size());
		assertEquals(1, q.removeLast());
		assertEquals(2, q.removeLast());
		assertEquals(3, q.removeLast());
		assertEquals(0, q.size());
	}
}
