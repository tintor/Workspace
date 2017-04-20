package tintor.sokoban;

import tintor.common.BinaryHeap;
import tintor.common.InlineChainingHashSet;
import tintor.common.Timer;
import tintor.common.Util;

// Set of all States for which we found some path from the start (not sure if optimal yet)
final class OpenSet {
	private final BinaryHeap<StateBase> heap = new BinaryHeap<StateBase>();
	private final InlineChainingHashSet set;
	long garbage;

	private final Timer timer_get = new Timer();
	private final Timer timer_update = new Timer();
	private final Timer timer_addUnsafe = new Timer();
	private final Timer timer_removeMin = new Timer();

	OpenSet(int alive) {
		set = new InlineChainingHashSet(16, alive);
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
	public StateBase get(StateBase s) {
		try (Timer t = timer_get.start()) {
			return (StateBase) set.get(s);
		}
	}

	// Called when a better path B to state is found than existing V
	// O(logN)
	public void update(StateBase v, StateBase b) {
		try (Timer t = timer_update.start()) {
			assert set.contains(v);
			assert v.equals(b);
			set.replaceWithEqual(v, b);
			heap.add(b);
			// Note: leaves garbage in heap, but that is fine
		}
	}

	// O(logN)
	public void addUnsafe(StateBase s) {
		try (Timer t = timer_addUnsafe.start()) {
			assert !set.contains(s);
			set.addUnsafe(s);
			heap.add(s);
		}
	}

	// O(logN)
	public StateBase removeMin() {
		try (Timer t = timer_removeMin.start()) {
			StateBase s = heap.removeMin();
			if (s == null)
				return null;
			set.remove(s);
			return s;
		}
	}

	public int size() {
		return heap.size();
	}
}