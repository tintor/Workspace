package tintor.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Random;

import org.junit.Test;

public class CompactHashMapTest {
	static byte[] make(int a) {
		return new byte[] { (byte) a, (byte) (a >> 8), (byte) (a >> 16), (byte) (a >> 24) };
	}

	static byte[] make(int a, int b) {
		return new byte[] { (byte) a, (byte) (a >> 8), (byte) (a >> 16), (byte) (a >> 24), (byte) b, (byte) (b >> 8),
				(byte) (b >> 16), (byte) (b >> 24) };
	}

	@Test
	public void simple() {
		CompactHashMap m = new CompactHashMap(4, 4, 2);
		// empty
		assertEquals(0, m.size());
		assertTrue(!m.contains(make(0)));
		assertTrue(!m.contains(make(1)));
		assertTrue(!m.iterator().hasNext());
		assertTrue(!m.remove(make(0)));
		assertArrayEquals(null, m.get(make(0), null));
		// add one element
		assertTrue(m.insert(make(0, 1)));
		assertEquals(1, m.size());
		assertTrue(m.contains(make(0)));
		assertTrue(!m.contains(make(1)));
		assertArrayEquals(make(1), m.get(make(0), null));
		Iterator<byte[]> it = m.iterator();
		assertTrue(it.hasNext());
		assertArrayEquals(make(0, 1), it.next());
		assertTrue(!it.hasNext());
		// remove one element
		assertTrue(m.remove(make(0)));
		assertTrue(!m.contains(make(0)));
		assertEquals(0, m.size());
		assertTrue(!m.iterator().hasNext());
		assertArrayEquals(null, m.get(make(0), null));
	}

	public static void main2(String[] args) {
		Log.info("sizeof(byte[])=%d", Measurer.sizeOf(byte[].class));
		CompactHashMap m = new CompactHashMap(4, 4, 2);
		Random rand = new Random(0);
		for (int i = 0; true; i++) {
			if (i % 1000000 == 0)
				Log.info("%d, %.1f bits, %.2f, %s", i / 1000000,
						(InstrumentationAgent.deepSizeOf(m) - m.size() * 8.0) / m.size() * 8,
						(double) m.size() / m.capacity(), Util.human(Runtime.getRuntime().freeMemory()));
			m.set(make(rand.nextInt(), rand.nextInt()));
		}
	}

	public static void main(String[] args) {
		Log.info("sizeof(byte[])=%d", Measurer.sizeOf(byte[].class));
		CompactHashMap m = new CompactHashMap(4, 4, 2);
		for (int j = 0; j < 1000000; j++) {
			for (int i = 0; i < 1000000; i++) {
				m.set(make(j + i, i));
			}
			for (int i = 0; i < 1000000; i++) {
				m.remove(make(j + i));
			}
		}
	}

	@Test
	public void big() {
		CompactHashMap m = new CompactHashMap(4, 4, 2);
		Random rand = new Random();
		for (int i = 0; i < 1000000; i++)
			m.set(make(rand.nextInt(), rand.nextInt()));
	}

	@Test
	public void sequential() {
		CompactHashMap m = new CompactHashMap(4, 4, 2);
		for (int i = 0; i < 1000000; i++) {
			assertTrue(!m.contains(make(i)));
			assertArrayEquals(null, m.get(make(i), null));
			m.set(make(i, -i));
			assertTrue(m.contains(make(i)));
			assertArrayEquals(make(-i), m.get(make(i), null));
			assertEquals(i + 1, m.size());
		}
	}
}