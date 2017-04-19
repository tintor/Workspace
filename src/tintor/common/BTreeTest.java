package tintor.common;

import java.util.HashSet;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class BTreeTest {
	static class BTree {
		ExternalBTree m;

		BTree(boolean a, int keys) {
			m = new ExternalBTree(6 + keys * 8, 4, 4);
		}

		boolean contains(int key) {
			byte[] b = new byte[4];
			b[3] = (byte) (key & 0xFF);
			key >>>= 8;
			b[2] = (byte) (key & 0xFF);
			key >>>= 8;
			b[1] = (byte) (key & 0xFF);
			key >>>= 8;
			b[0] = (byte) (key & 0xFF);
			return m.contains(b);
		}

		int get(int key) {
			byte[] b = new byte[4];
			b[3] = (byte) (key & 0xFF);
			key >>>= 8;
			b[2] = (byte) (key & 0xFF);
			key >>>= 8;
			b[1] = (byte) (key & 0xFF);
			key >>>= 8;
			b[0] = (byte) (key & 0xFF);
			byte[] v = m.get(b);
			if (v == null)
				return Integer.MIN_VALUE;
			int value = ((int) v[0]) & 0xFF;
			value <<= 8;
			value |= ((int) v[1]) & 0xFF;
			value <<= 8;
			value |= ((int) v[2]) & 0xFF;
			value <<= 8;
			value |= ((int) v[3]) & 0xFF;
			return value;
		}

		void put(int key, int value) {
			byte[] b = new byte[4];
			b[3] = (byte) (key & 0xFF);
			key >>>= 8;
			b[2] = (byte) (key & 0xFF);
			key >>>= 8;
			b[1] = (byte) (key & 0xFF);
			key >>>= 8;
			b[0] = (byte) (key & 0xFF);

			byte[] v = new byte[4];
			v[3] = (byte) (value & 0xFF);
			value >>>= 8;
			v[2] = (byte) (value & 0xFF);
			value >>>= 8;
			v[1] = (byte) (value & 0xFF);
			value >>>= 8;
			v[0] = (byte) (value & 0xFF);
			m.put(b, v);
		}
	}

	@Test
	public void testSimple2() {
		BTree a = new BTree(false, 2);
		a.put(1, 3);
		Assert.assertEquals(3, a.get(1));
	}

	@Test
	public void testSimple() {
		BTree a = new BTree(false, 2);
		a.put(0x11223344, 0x55667788);
		a.put(0xAABBCCDD, 0x33445566);
		Assert.assertEquals(0x55667788, a.get(0x11223344));
		Assert.assertEquals(0x33445566, a.get(0xAABBCCDD));
		a.put(1, 0);
	}

	@Test
	public void testSequential2() {
		BTree a = new BTree(false, 2);
		for (int i = 0; i < n; i++) {
			Assert.assertEquals(false, a.contains(i));
			Assert.assertEquals(Integer.MIN_VALUE, a.get(i));
			a.put(i, -i);
			Assert.assertEquals(true, a.contains(i));
			Assert.assertEquals(-i, a.get(i));
		}
	}

	@Test
	public void testSequential3() {
		BTree a = new BTree(false, 3);
		for (int i = 0; i < n; i++) {
			Assert.assertEquals(false, a.contains(i));
			Assert.assertEquals(Integer.MIN_VALUE, a.get(i));
			a.put(i, -i);
			Assert.assertEquals(true, a.contains(i));
			Assert.assertEquals(-i, a.get(i));
		}
	}

	@Test
	public void testSequential4() {
		BTree a = new BTree(false, 4);
		for (int i = 0; i < n; i++) {
			Assert.assertEquals(false, a.contains(i));
			Assert.assertEquals(Integer.MIN_VALUE, a.get(i));
			a.put(i, -i);
			Assert.assertEquals(true, a.contains(i));
			Assert.assertEquals(-i, a.get(i));
		}
	}

	@Test
	public void testSequential5() {
		BTree a = new BTree(false, 5);
		for (int i = 0; i < n; i++) {
			Assert.assertEquals(false, a.contains(i));
			Assert.assertEquals(Integer.MIN_VALUE, a.get(i));
			a.put(i, -i);
			Assert.assertEquals(true, a.contains(i));
			Assert.assertEquals(-i, a.get(i));
		}
	}

	@Test
	public void testRandom2() {
		BTree a = new BTree(false, 2);
		Random rand = new Random(2);
		HashSet<Integer> m = new HashSet<Integer>();
		for (int i = 0; i < n; i++) {
			int x = rand.nextInt();
			Assert.assertEquals(m.contains(x), a.contains(x));
			Assert.assertEquals(m.contains(x) ? -x : Integer.MIN_VALUE, a.get(x));
			a.put(x, -x);
			m.add(x);
			Assert.assertEquals(true, a.contains(x));
			Assert.assertEquals(-x, a.get(x));
		}
	}

	static final int n = 100;

	@Test
	public void testRandom3() {
		BTree a = new BTree(false, 3);
		Random rand = new Random(3);
		HashSet<Integer> m = new HashSet<Integer>();
		for (int i = 0; i < n; i++) {
			int x = rand.nextInt();
			Assert.assertEquals(m.contains(x), a.contains(x));
			Assert.assertEquals(m.contains(x) ? -x : Integer.MIN_VALUE, a.get(x));
			a.put(x, -x);
			Assert.assertEquals(true, a.contains(x));
			Assert.assertEquals(-x, a.get(x));
		}
	}

	@Test
	public void testRandom4() {
		BTree a = new BTree(false, 4);
		Random rand = new Random(4);
		HashSet<Integer> m = new HashSet<Integer>();
		for (int i = 0; i < n; i++) {
			int x = rand.nextInt();
			Assert.assertEquals(m.contains(x), a.contains(x));
			Assert.assertEquals(m.contains(x) ? -x : Integer.MIN_VALUE, a.get(x));
			a.put(x, -x);
			Assert.assertEquals(true, a.contains(x));
			Assert.assertEquals(-x, a.get(x));
		}
	}

	@Test
	public void testRandom5() {
		BTree a = new BTree(false, 5);
		Random rand = new Random(5);
		HashSet<Integer> m = new HashSet<Integer>();
		for (int i = 0; i < n; i++) {
			int x = rand.nextInt();
			Assert.assertEquals(m.contains(x), a.contains(x));
			Assert.assertEquals(m.contains(x) ? -x : Integer.MIN_VALUE, a.get(x));
			a.put(x, -x);
			Assert.assertEquals(true, a.contains(x));
			Assert.assertEquals(-x, a.get(x));
		}
	}
}