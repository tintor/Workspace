package tintor.common;

import org.junit.Assert;
import org.junit.Test;

public class BinaryHeapTest {
	@Test
	public void test() {
		BinaryHeap<Integer> heap = new BinaryHeap<Integer>();
		Assert.assertEquals(0, heap.size());
	}
}
