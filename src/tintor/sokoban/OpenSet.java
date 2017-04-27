package tintor.sokoban;

import java.util.Arrays;

import tintor.common.BinaryHeap;
import tintor.common.CompactArray;
import tintor.common.InlineChainingHashSet;
import tintor.common.Timer;
import tintor.common.Util;

class HeaplessOpenSet {
	private final CompactArray<InlineChainingHashSet> priority = new CompactArray<InlineChainingHashSet>();
	private int min_priority = Integer.MAX_VALUE;
	private int size;
	private InlineChainingHashSet.Scanner scanner;

	private final Timer timer_get = new Timer();
	private final Timer timer_update = new Timer();
	private final Timer timer_addUnsafe = new Timer();
	private final Timer timer_removeMin = new Timer();

	long report(int cycles) {
		timer_get.total /= cycles;
		timer_update.total /= cycles;
		timer_addUnsafe.total /= cycles;
		timer_removeMin.total /= cycles;

		StringBuilder sb = new StringBuilder();
		for (int p = priority.first(); p != priority.end(); p = priority.next(p)) {
			if (sb.length() > 0)
				sb.append(' ');
			sb.append(p);
			sb.append(':');
			sb.append(priority.get(p).size());
			if (sb.length() > 100)
				break;
		}

		long total_size = 0;
		long total_capacity = 0;
		double ratio = (double) total_size / Math.max(1, total_capacity);
		long total = timer_get.total + timer_update.total + timer_addUnsafe.total + timer_removeMin.total;
		System.out.printf("open:%s %.2f [get:%s update:%s add:%s pop:%s]\n", Util.human(total_size), ratio,
				timer_get.clear(), timer_update.clear(), timer_addUnsafe.clear(), timer_removeMin.clear());
		System.out.println(sb);
		return total;
	}

	// O(1)
	public State get(State s) {
		try (Timer t = timer_get.start()) {
			for (Object o : priority.values()) {
				InlineChainingHashSet set = (InlineChainingHashSet) o;
				State q = (State) set.get(s);
				if (q != null)
					return q;
			}
			return null;
		}
	}

	// Called when a better path B to state is found than existing V
	// O(1)
	public void update(State v, State b) {
		try (Timer t = timer_update.start()) {
			priority.get(v.total_dist()).remove(v);
			priority.get(b.total_dist()).add(b);
		}
	}

	// O(1)
	public void addUnsafe(State s) {
		try (Timer t = timer_addUnsafe.start()) {
			int p = s.total_dist();
			InlineChainingHashSet set = priority.get(p);
			if (set == null) {
				set = new InlineChainingHashSet(4, null, false);
				priority.set(p, set);
				if (p < min_priority) {
					min_priority = p;
					scanner = set.new Scanner();
				}
			}
			boolean added = set.add(s);
			assert added;
			size += 1;
		}
	}

	// O(1)
	public State removeMin() {
		try (Timer t = timer_removeMin.start()) {
			int p = min_priority;
			assert p != priority.end();
			InlineChainingHashSet set = priority.get(p);
			State s = (State) scanner.remove();
			if (set.size() == 0) {
				priority.remove(p);
				min_priority = priority.next(min_priority);
				scanner = null;
				if (min_priority != priority.end())
					scanner = priority.get(min_priority).new Scanner();
			}
			size -= 1;
			return s;
		}
	}

	public int size() {
		return size;
	}
}

// Set of all States for which we found some path from the start (not sure if optimal yet)
final class OpenSet {
	private final BinaryHeap<State> heap = new BinaryHeap<State>();
	private final InlineChainingHashSet set;
	int garbage;

	private final Timer timer_get = new Timer();
	private final Timer timer_update = new Timer();
	private final Timer timer_addUnsafe = new Timer();
	private final Timer timer_removeMin = new Timer();

	OpenSet(int alive, boolean enable_parallel_hashtable_resize) {
		set = new InlineChainingHashSet(16, alive, enable_parallel_hashtable_resize);
	}

	int[] histogram = new int[1000];

	long report(int cycles) {
		timer_get.total /= cycles;
		timer_update.total /= cycles;
		timer_addUnsafe.total /= cycles;
		timer_removeMin.total /= cycles;

		Arrays.fill(histogram, 0);
		for (int i = 0; i < heap.size(); i++)
			histogram[heap.get(i).total_dist()] += 1;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < histogram.length; i++)
			if (histogram[i] > 0) {
				if (sb.length() > 0)
					sb.append(' ');
				sb.append(i);
				sb.append(':');
				sb.append(histogram[i]);
				if (sb.length() > 100)
					break;
			}

		long total = timer_get.total + timer_update.total + timer_addUnsafe.total + timer_removeMin.total;
		System.out.printf("open:%s %.2f garbage:%s [get:%s update:%s add:%s pop:%s]\n", Util.human(size()), set.ratio(),
				Util.human(garbage), timer_get.clear(), timer_update.clear(), timer_addUnsafe.clear(),
				timer_removeMin.clear());
		System.out.println(sb);
		return total;
	}

	// O(1)
	public State get(State s) {
		try (Timer t = timer_get.start()) {
			return (State) set.get(s);
		}
	}

	// Called when a better path B to state is found than existing V
	// O(logN)
	public void updateTrashy(State v, State b) {
		try (Timer t = timer_update.start()) {
			// TODO: try to cleanup existing garbage first if heap array is full
			assert set.contains(v);
			assert v.equals(b);
			set.replaceWithEqual(v, b);
			heap.add(b);
			// Note: leaves garbage in heap, but that is fine
			garbage += 1;
			// TODO if too much garbage: 1) sort heap by (priority, identityHashCode) 2) remove duplicates (garbage), 3) done (sorted heap is a valid heap)
			assert heap.size() == set.size() + garbage;
		}
	}

	// O(logN)
	public void addUnsafe(State s) {
		try (Timer t = timer_addUnsafe.start()) {
			// TODO: try to cleanup existing garbage first if heap array is full
			assert !set.contains(s);
			set.addUnsafe(s);
			heap.add(s);
			assert heap.size() == set.size() + garbage;
		}
	}

	// O(logN)
	public State removeMin() {
		try (Timer t = timer_removeMin.start()) {
			while (true) {
				State s = heap.removeMin();
				if (s == null)
					return null;
				if (!set.remove(s)) {
					garbage -= 1;
					assert garbage >= 0;
					continue;
				}
				return s;
			}
		}
	}

	public int size() {
		assert heap.size() == set.size() + garbage;
		return set.size();
	}
}