package tintor.sokoban;

import tintor.common.Array;

final class StateMap {
	private final OpenAddressingIntArrayHashMap[] map;
	private int size;

	StateMap(int alive) {
		int key_size = (alive + 31) / 32;
		map = Array.make(alive, i -> new OpenAddressingIntArrayHashMap(key_size));
	}

	int size() {
		return size;
	}

	void remove_if(StateKeyPredicate fn) {
		for (int agent = 0; agent < map.length; agent++) {
			OpenAddressingIntArrayHashMap m = map[agent];
			final int a = agent;
			m.remove_if(key -> fn.test(a, key));
		}
	}

	void update(int a_total_dist, State b) {
		map[b.agent].update_unsafe(b.box, b.serialize());
		assert map[b.agent].get(b.box) == b.serialize();
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

	void remove(StateKey s) {
		map[s.agent].remove_unsafe(s.box);
	}

	int get_total_dist(StateKey s) {
		long value = map[s.agent].get(s.box);
		final int mask = (1 << 13) - 1;
		return (int) (value & mask);
	}
}