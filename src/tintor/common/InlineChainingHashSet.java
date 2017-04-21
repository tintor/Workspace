package tintor.common;

import java.util.Iterator;

public final class InlineChainingHashSet implements Iterable<InlineChainingHashSet.Element> {
	public abstract static class Element {
		private Element next;

		protected abstract int hashCode(Object context);
	}

	private Element[] buckets;
	private int size, shift;
	private Object context;

	public InlineChainingHashSet(int capacity, Object context) {
		this.buckets = new Element[Util.roundUpPowerOf2(capacity)];
		this.shift = 31 - Integer.bitCount(buckets.length - 1);
		this.context = context;
	}

	public int size() {
		return size;
	}

	public double ratio() {
		return (double) size / buckets.length;
	}

	public boolean containsIdentical(Element s) {
		for (Element p = buckets[index(s)]; p != null; p = p.next)
			if (p == s)
				return true;
		return false;
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

	public int index(Element s) {
		int h = s.hashCode(context);
		assert h >= 0;
		return h >> shift;
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
			size -= 1;
			return true;
		}

		Element p = buckets[i];
		Element q = p.next;
		while (q != null) {
			if (q.equals(s)) {
				p.next = q.next;
				size -= 1;
				return true;
			}
			p = q;
			q = q.next;
		}
		return false;
	}

	public boolean replaceWithEqual(Element a, Element b) {
		assert a.equals(b);
		assert a.hashCode(context) == b.hashCode(context);
		assert b.next == null;

		int i = index(a);
		if (buckets[i] == null)
			return false;
		if (buckets[i].equals(a)) {
			b.next = buckets[i].next;
			buckets[i] = b;
			return true;
		}

		Element p = buckets[i];
		Element q = p.next;
		while (q != null) {
			if (q.equals(a)) {
				p.next = b;
				b.next = q.next;
				return true;
			}
			p = q;
			q = q.next;
		}
		return false;
	}

	private void grow() {
		int capacity = buckets.length * 2;
		if (capacity <= buckets.length)
			return;

		Element[] old_buckets = buckets;
		try {
			buckets = new Element[capacity];
			shift -= 1;
		} catch (OutOfMemoryError e) {
			Log.warning("unable to grow hash bucket array to %d: out of memory", capacity);
			return;
		}

		int max = 0;
		for (int j = 0; j < old_buckets.length; j++) {
			Element a = old_buckets[j];
			Element high = null;
			Element low = null;
			if (a != null) {
				int count = 1;
				while (true) {
					Element b = a.next;
					int i = index(a);
					assert 2 * j <= i && i <= 2 * j + 1;
					if (i % 2 == 0) {
						a.next = low;
						low = a;
					} else {
						a.next = high;
						high = a;
					}
					a = b;
					if (a == null)
						break;
					count += 1;
				}
				// writes to buckets[] will be in sequential order
				buckets[j * 2] = low;
				buckets[j * 2 + 1] = high;
				if (count > max)
					max = count;
			}
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