package tintor.sokoban;

final class StateMap {
	private final OpenAddressingIntArrayHashMap[] map;
	private int size;

	StateMap(int alive, int cells) {
		int key_size = (alive + 31) / 32;
		map = new OpenAddressingIntArrayHashMap[cells];
		for (int i = 0; i < cells; i++)
			map[i] = new OpenAddressingIntArrayHashMap(key_size);
	}

	int size() {
		return size;
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
		return map[s.agent].get(s.box) != 0;
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