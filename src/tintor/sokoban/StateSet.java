package tintor.sokoban;

import tintor.common.Array;

final class StateSet {
	private final OpenAddressingIntArrayHashSet[] set;
	private int size;

	StateSet(int alive, int cells) {
		int key_size = (alive + 31) / 32;
		set = Array.make(cells, i -> new OpenAddressingIntArrayHashSet(key_size));
	}

	int size() {
		return size;
	}

	void remove_if(StateKeyPredicate fn) {
		for (int agent = 0; agent < set.length; agent++) {
			final int a = agent;
			set[agent].remove_if((box, offset) -> fn.test(a, box, offset));
		}
	}

	void insert(StateKey s) {
		set[s.agent].insert_unsafe(s.box);
		size += 1;
	}

	boolean contains(StateKey s) {
		return set[s.agent].contains(s.box);
	}

	void remove(StateKey s) {
		set[s.agent].remove_unsafe(s.box);
		size -= 1;
	}

	void clear() {
		for (OpenAddressingIntArrayHashSet s : set)
			s.clear();
	}
}