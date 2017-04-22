package tintor.sokoban;

import tintor.common.BinaryHeap;
import tintor.common.InlineChainingHashSet;
import tintor.common.Timer;
import tintor.common.Util;

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

	long report(int cycles) {
		timer_get.total /= cycles;
		timer_update.total /= cycles;
		timer_addUnsafe.total /= cycles;
		timer_removeMin.total /= cycles;

		long total = timer_get.total + timer_update.total + timer_addUnsafe.total + timer_removeMin.total;
		System.out.printf("open:%s %.2f garbage:%s [get:%s update:%s add:%s pop:%s]\n", Util.human(size()), set.ratio(),
				Util.human(garbage), timer_get.clear(), timer_update.clear(), timer_addUnsafe.clear(),
				timer_removeMin.clear());
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