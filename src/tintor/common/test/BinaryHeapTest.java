package tintor.common.test;

import org.junit.Assert;
import org.junit.Test;

import tintor.common.BinaryHeap;

public class BinaryHeapTest {
	@Test
	public void test() {
		BinaryHeap<Integer> heap = new BinaryHeap<Integer>();
		Assert.assertEquals(0, heap.size());
	}
}
