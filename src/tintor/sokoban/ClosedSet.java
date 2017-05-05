package tintor.sokoban;

import tintor.common.AutoTimer;
import tintor.common.InstrumentationAgent;
import tintor.common.Util;

final class ClosedSet {
	final StateMap map;
	final AutoTimer timer_add = new AutoTimer("closed.add");
	final AutoTimer timer_contains = new AutoTimer("closed.contains");

	ClosedSet(int alive, int cells) {
		map = new StateMap(alive, cells);
	}

	int size() {
		return map.size();
	}

	void report() {
		System.out.printf("closed:%s memory:%s\n", Util.human(size()),
				Util.human(InstrumentationAgent.deepSizeOf(map)));
	}

	void add(State s) {
		try (AutoTimer t = timer_add.open()) {
			map.insert(s);
		}
	}

	boolean contains(StateKey s) {
		try (AutoTimer t = timer_contains.open()) {
			return map.contains(s);
		}
	}

	State get(StateKey s) {
		return map.get(s);
	}
}