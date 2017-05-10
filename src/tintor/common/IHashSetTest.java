package tintor.common;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

final class JavaHashSet<T> implements IHashSet<T> {
	HashSet<T> set = new HashSet<T>();

	@Override
	public Iterator<T> iterator() {
		return set.iterator();
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public int capacity() {
		return 0;
	}

	@Override
	public void clear() {
		set.clear();
	}

	@Override
	public boolean contains(T s) {
		return set.contains(s);
	}

	@Override
	public T get(T s) {
		return null;
	}

	@Override
	public boolean set(T s) {
		return set.contains(s) ? !update(s) : set.add(s);
	}

	@Override
	public boolean insert(T s) {
		return set.add(s);
	}

	@Override
	public boolean update(T s) {
		return set.remove(s) && set.add(s);
	}

	@Override
	public boolean remove(T s) {
		return set.remove(s);
	}

	@Override
	public tintor.common.IHashSet.Remover<T> remover() {
		return new tintor.common.IHashSet.Remover<T>() {
			Iterator<T> it = JavaHashSet.this.iterator();

			@Override
			public T remove() {
				if (!it.hasNext())
					return null;
				T a = it.next();
				it.remove();
				return a;
			}
		};
	}
}

final class CompactHashSet<T> implements IHashSet<T> {
	final CompactHashMap set = new CompactHashMap(4, 0, 4);
	final ByteBuffer buffer = ByteBuffer.allocate(4);

	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			ByteBuffer bb = ByteBuffer.allocate(4);
			Iterator<byte[]> it = set.iterator(bb.array());
			Element element = new Element(0);

			public boolean hasNext() {
				return it.hasNext();
			}

			@SuppressWarnings("unchecked")
			@Override
			public T next() {
				it.next();
				element.value = bb.getInt(0);
				return (T) element;
			}
		};
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public int capacity() {
		return 0;
	}

	@Override
	public void clear() {
		set.clear();
	}

	public boolean contains(T s) {
		Element a = (Element) s;
		buffer.putInt(0, a.value);
		return set.contains(buffer.array());
	}

	public T get(T s) {
		throw new Error();
	}

	public boolean set(T s) {
		throw new Error();
	}

	public boolean insert(T s) {
		Element a = (Element) s;
		buffer.putInt(0, a.value);
		return set.insert(buffer.array());
	}

	@Override
	public boolean update(T s) {
		throw new Error();
	}

	@Override
	public boolean remove(T s) {
		Element a = (Element) s;
		buffer.putInt(0, a.value);
		return set.remove(buffer.array());
	}

	@Override
	public IHashSet.Remover<T> remover() {
		return new IHashSet.Remover<T>() {
			public T remove() {
				throw new Error();
			}
		};
	}
}

final class CompactHashSet4<T> implements IHashSet<T> {
	final CompactHashMap4 set = new CompactHashMap4(1, 0, 4);
	final int[] key = new int[1];

	public Iterator<T> iterator() {
		return new Iterator<T>() {
			final int[] key = new int[1];
			Iterator<int[]> it = set.iterator(key);
			Element element = new Element(0);

			public boolean hasNext() {
				return it.hasNext();
			}

			@SuppressWarnings("unchecked")
			public T next() {
				it.next();
				element.value = key[0];
				return (T) element;
			}
		};
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public int capacity() {
		return 0;
	}

	@Override
	public void clear() {
		set.clear();
	}

	public boolean contains(T s) {
		Element a = (Element) s;
		key[0] = a.value;
		return set.contains(key);
	}

	public T get(T s) {
		throw new Error();
	}

	public boolean set(T s) {
		throw new Error();
	}

	public boolean insert(T s) {
		Element a = (Element) s;
		key[0] = a.value;
		return set.insert(key);
	}

	@Override
	public boolean update(T s) {
		throw new Error();
	}

	@Override
	public boolean remove(T s) {
		Element a = (Element) s;
		key[0] = a.value;
		return set.remove(key);
	}

	@Override
	public IHashSet.Remover<T> remover() {
		return new IHashSet.Remover<T>() {
			public T remove() {
				throw new Error();
			}
		};
	}
}

final class OpenAddressingHashSetInteger<T> implements IHashSet<T> {
	final OpenAddressingHashSetInt set = new OpenAddressingHashSetInt();
	final int[] key = new int[1];

	public Iterator<T> iterator() {
		return new Iterator<T>() {
			IteratorInt it = set.iterator();
			Element element = new Element(0);

			public boolean hasNext() {
				return it.hasNext();
			}

			@SuppressWarnings("unchecked")
			public T next() {
				element.value = it.next();
				return (T) element;
			}
		};
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public int capacity() {
		return 0;
	}

	@Override
	public void clear() {
		set.clear();
	}

	public boolean contains(T s) {
		Element a = (Element) s;
		return set.contains(a.value);
	}

	public T get(T s) {
		throw new Error();
	}

	public boolean set(T s) {
		throw new Error();
	}

	public boolean insert(T s) {
		Element a = (Element) s;
		return set.insert(a.value);
	}

	@Override
	public boolean update(T s) {
		throw new Error();
	}

	@Override
	public boolean remove(T s) {
		Element a = (Element) s;
		return set.remove(a.value);
	}

	@Override
	public IHashSet.Remover<T> remover() {
		return new IHashSet.Remover<T>() {
			public T remove() {
				throw new Error();
			}
		};
	}
}

final class Element {
	int value;

	public Element(int a) {
		value = a;
	}

	public boolean equals(Object o) {
		Element a = (Element) o;
		return value == a.value;
	}

	public int hashCode() {
		return MurmurHash3.fmix(value);
	}
}

@RunWith(Parameterized.class)
public class IHashSetTest {
	@Parameters(name = "{0}_{1}")
	public static Collection<Object[]> data() {
		ArrayList<Object[]> test = new ArrayList<Object[]>();
		for (int i = 0; i < 10; i++) {
			test.add(new Object[] { OpenAddressingHashSetInteger.class, i });
			test.add(new Object[] { JavaHashSet.class, i });
			test.add(new Object[] { ArrayChainingHashSet.class, i });
			test.add(new Object[] { OpenAddressingHashSet.class, i });
			test.add(new Object[] { CompactHashSet.class, i });
			test.add(new Object[] { CompactHashSet4.class, i });
		}
		return test;
	}

	static Element[] array;
	static {
		HashSet<Element> h = new HashSet<Element>();
		Random rand = new Random(0);
		int n = 10000000;
		array = new Element[n];
		for (int i = 0; i < n; i++) {
			while (true) {
				array[i] = new Element(rand.nextInt());
				if (h.add(array[i]))
					break;
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void test() throws Exception {
		IHashSet<Element> set = (IHashSet<Element>) klass.newInstance();
		long sum = 0;
		for (int i = 1; i <= array.length; i++) {
			Element x = array[i - 1];
			sum += x.value;
			Assert.assertFalse(set.contains(x));
			Assert.assertTrue(set.insert(x));
			Assert.assertTrue(set.contains(x));
			Assert.assertEquals(i, set.size());
		}
		long sum2 = 0;
		for (Element x : set)
			sum2 += x.value;
		Assert.assertEquals(sum, sum2);
		for (int i = 1; i <= array.length; i++) {
			Element x = array[i - 1];
			Assert.assertTrue(set.contains(x));
			Assert.assertTrue(set.remove(x));
			Assert.assertFalse(set.contains(x));
			Assert.assertEquals(array.length - i, set.size());
		}
	}

	@SuppressWarnings("unchecked")
	public void sizeof() throws Exception {
		IHashSet<Element> set = (IHashSet<Element>) klass.newInstance();
		for (int i = 1; i <= array.length; i++)
			Assert.assertTrue(set.insert(array[i - 1]));
		long size = InstrumentationAgent.deepSizeOf(array[0]) * array.length;
		Log.info("sizeof(objects) = %s", Util.human(size));
		size = InstrumentationAgent.deepSizeOf(set);
		Log.info("sizeof(%s) = %s", klass.getName(), Util.human(size));
	}

	@After
	public void clean() throws Exception {
		System.gc();
	}

	@Parameter(0)
	public Class<?> klass;
	@Parameter(1)
	public int broj;

	public static void main(String[] args) throws Exception {
		IHashSetTest test = new IHashSetTest();
		test.klass = OpenAddressingHashSetInteger.class;
		test.sizeof();
		test.klass = OpenAddressingHashSet.class;
		test.sizeof();
		test.klass = ArrayChainingHashSet.class;
		test.sizeof();
		test.klass = JavaHashSet.class;
		test.sizeof();
		test.klass = CompactHashSet.class;
		test.sizeof();
		test.klass = CompactHashSet4.class;
		test.sizeof();
	}
}

// Overhead of set:
// OpenAddressingHashSet = 67M
// ArrayChainingHashSet = 119M
// JavaHashSet = 387M