package tintor.sokoban;

import tintor.common.AutoTimer;
import tintor.common.InstrumentationAgent;
import tintor.common.Util;

final class ClosedSet {
	final StateMapDisk map;
	final Level level;
	static final AutoTimer timer_add = new AutoTimer("closed.add");
	static final AutoTimer timer_contains = new AutoTimer("closed.contains");

	ClosedSet(Level level) {
		this.level = level;
		map = new StateMapDisk(level.alive, level.cells);
	}

	int size() {
		return map.size();
	}

	void report() {
		System.out.printf("closed:%s memory:%s\n", Util.human(size()),
				Util.human(InstrumentationAgent.deepSizeOf(map)));
	}

	public void remove_if(StateKeyPredicate fn) {
		map.remove_if(fn);
	}

	void add(State s) {
		try (AutoTimer t = timer_add.open()) {
			State a = level.normalize(s);
			assert !map.contains(a);
			map.insert(a);
		}
	}

	boolean contains(StateKey s) {
		try (AutoTimer t = timer_contains.open()) {
			return map.contains(level.normalize(s));
		}
	}

	State get(StateKey s) {
		return level.denormalize(map.get(level.normalize(s)));
	}
}