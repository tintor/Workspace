package tintor.sokoban;

import tintor.common.InstrumentationAgent;
import tintor.common.Timer;
import tintor.common.Util;

final class ClosedSet {
	final StateMap map;
	final Timer timer_add = new Timer();
	final Timer timer_contains = new Timer();

	ClosedSet(int alive, int cells) {
		map = new StateMap(alive, cells);
	}

	int size() {
		return map.size();
	}

	long report(int cycles) {
		timer_add.total /= cycles;
		timer_contains.total /= cycles;
		long total = timer_add.total + timer_contains.total;
		System.out.printf("closed:%s memory:%s [add:%s contains:%s]\n", Util.human(size()),
				Util.human(InstrumentationAgent.deepSizeOf(map)), timer_add.clear(), timer_contains.clear());
		return total;
	}

	void add(State s) {
		try (Timer t = timer_add.start()) {
			map.insert(s);
		}
	}

	boolean contains(State s) {
		try (Timer t = timer_contains.start()) {
			return map.contains(s);
		}
	}

	State get(State s) {
		return map.get(s);
	}
}