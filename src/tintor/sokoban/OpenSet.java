package tintor.sokoban;

import tintor.common.BinaryHeap;
import tintor.common.InlineChainingHashSet;
import tintor.common.Timer;
import tintor.common.Util;

// Set of all States for which we found some path from the start (not sure if optimal yet)
final class OpenSet {
	private final BinaryHeap<State> heap = new BinaryHeap<State>();
	private final InlineChainingHashSet set = new InlineChainingHashSet();
	long garbage;

	private final Timer timer_get = new Timer();
	private final Timer timer_update = new Timer();
	private final Timer timer_addUnsafe = new Timer();
	private final Timer timer_removeMin = new Timer();

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

	// Called when a better path to state is found
	// O(logN)
	public void update(State s) {
		try (Timer t = timer_update.start()) {
			assert set.contains(s);
			heap.add(s);
		}
	}

	// O(logN)
	public void addUnsafe(State s) {
		try (Timer t = timer_addUnsafe.start()) {
			assert !set.contains(s);
			set.addUnsafe(s);
			heap.add(s);
		}
	}

	// O(logN)
	public State removeMin() {
		try (Timer t = timer_removeMin.start()) {
			State s = heap.removeMin();
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