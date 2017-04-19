package tintor.common;

import java.util.Iterator;

public final class InlineChainingHashSet implements Iterable<InlineChainingHashSet.Element> {
	public static class Element {
		private Element next;
	}

	private Element[] buckets;
	private int size;

	public InlineChainingHashSet() {
		this(16);
	}

	public InlineChainingHashSet(int capacity) {
		buckets = new Element[Util.roundUpPowerOf2(capacity)];
	}

	public int size() {
		return size;
	}

	public double ratio() {
		return (double) size / buckets.length;
	}

	public boolean contains(Element s) {
		return get(s) != null;
	}

	public Element get(Element s) {
		for (Element p = buckets[index(s)]; p != null; p = p.next)
			if (p.equals(s))
				return p;
		return null;
	}

	public void clear() {
		for (int i = 0; i < buckets.length; i++)
			if (buckets[i] != null)
				buckets[i] = null;
	}

	public int index(Element s) {
		int h = s.hashCode();
		assert h >= 0;
		return h & (buckets.length - 1);
	}

	public void addUnsafe(Element s) {
		assert s.next == null;
		int i = index(s);
		s.next = buckets[i];
		buckets[i] = s;
		if (++size == buckets.length)
			grow();
	}

	public boolean add(Element s) {
		assert s.next == null;
		int i = index(s);
		for (Element p = buckets[i]; p != null; p = p.next)
			if (p.equals(s))
				return false;
		s.next = buckets[i];
		buckets[i] = s;
		if (++size == buckets.length)
			grow();
		return true;
	}

	public boolean remove(Element s) {
		int i = index(s);
		if (buckets[i] == null)
			return false;
		if (buckets[i].equals(s)) {
			buckets[i] = buckets[i].next;
			return true;
		}

		Element p = buckets[i];
		Element q = p.next;
		while (q != null) {
			if (q.equals(s)) {
				p.next = q.next;
				return true;
			}
			p = q;
			q = q.next;
		}
		return false;
	}

	private void grow() {
		if (buckets.length * 2 > buckets.length)
			resize(buckets.length * 2);
	}

	private void resize(int capacity) {
		Element[] old_buckets = buckets;
		try {
			buckets = new Element[capacity];
		} catch (OutOfMemoryError e) {
			Log.warning("unable to grow hash bucket array to %d: out of memory", capacity);
			return;
		}
		int max = 0;
		for (Element a : old_buckets) {
			int count = 0;
			while (a != null) {
				Element b = a.next;
				int i = index(a);
				a.next = buckets[i];
				buckets[i] = a;
				a = b;
				count += 1;
			}
			max = Math.max(max, count);
		}
		if (max >= 10 && capacity >= 8192)
			Log.fine("longest hash bucket %d elements pre-resize (%d -> %d)", max, old_buckets.length, buckets.length);
	}

	private class IteratorT implements Iterator<Element> {
		IteratorT() {
			index = 0;
			e = buckets[index];
			while (e == null && ++index < buckets.length)
				e = buckets[index];
		}

		@Override
		public boolean hasNext() {
			return index < buckets.length;
		}

		@Override
		public Element next() {
			Element r = e;
			e = e.next;
			while (e == null && ++index < buckets.length)
				e = buckets[index];
			return r;
		}

		int index;
		Element e;
	}

	@Override
	public Iterator<Element> iterator() {
		return new IteratorT();
	}
}