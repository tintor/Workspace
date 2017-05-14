package tintor.sokoban;

import tintor.common.AutoTimer;
import tintor.common.InstrumentationAgent;
import tintor.common.Util;

public final class ClosedSet {
	final StateMap map;
	final CellLevelTransforms transforms;
	static final AutoTimer timer_add = new AutoTimer("closed.add");
	static final AutoTimer timer_contains = new AutoTimer("closed.contains");

	ClosedSet(CellLevel level) {
		this.transforms = level.transforms;
		map = new StateMap(level.alive, level.cells.length);
	}

	public int size() {
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
			State a = transforms.normalize(s);
			assert !map.contains(a);
			map.insert(a);
		}
	}

	boolean contains(StateKey s) {
		try (AutoTimer t = timer_contains.open()) {
			return map.contains(transforms.normalize(s));
		}
	}

	State get(StateKey s) {
		return transforms.denormalize(map.get(transforms.normalize(s)));
	}
}