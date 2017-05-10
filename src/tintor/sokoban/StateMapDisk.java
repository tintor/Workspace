package tintor.sokoban;

import tintor.common.Array;

final class StateMapDisk {
	private final OpenAddressingIntArrayHashMapDisk[] map;
	private int size;

	StateMapDisk(int alive, int cells) {
		int key_size = (alive + 31) / 32;
		map = Array.make(cells, i -> new OpenAddressingIntArrayHashMapDisk(key_size));
	}

	int size() {
		return size;
	}

	void remove_if(StateKeyPredicate fn) {
		for (int agent = 0; agent < map.length; agent++) {
			OpenAddressingIntArrayHashMapDisk m = map[agent];
			final int a = agent;
			m.remove_if(key -> fn.test(a, key));
		}
	}

	void insert(State s) {
		map[s.agent].insert_unsafe(s.box, s.serialize());
		size += 1;
		assert map[s.agent].get(s.box) == s.serialize();
	}

	boolean contains(StateKey s) {
		return map[s.agent].contains(s.box);
	}

	State get(StateKey s) {
		long value = map[s.agent].get(s.box);
		if (value == 0)
			return null;
		return State.deserialize(s.agent, s.box, value);
	}
}