package tintor.sokoban;

final class StateMap {
	private final OpenAddressingIntArrayHashMap[] map;
	private final int[] key;
	private int size;

	StateMap(int alive, int cells) {
		int key_size = (alive + 31) / 32;
		map = new OpenAddressingIntArrayHashMap[cells];
		for (int i = 0; i < cells; i++)
			map[i] = new OpenAddressingIntArrayHashMap(key_size);
		key = new int[key_size];
	}

	int size() {
		return size;
	}

	void update(int a_total_dist, State b) {
		OpenAddressingIntArrayHashMap m = map[b.agent()];
		m.update_unsafe(b.box, serialize_value(b));
	}

	void insert(State s) {
		OpenAddressingIntArrayHashMap m = map[s.agent()];
		m.insert_unsafe(s.box, serialize_value(s));
		size += 1;
	}

	boolean contains(State s) {
		return map[s.agent()].get(s.box) != 0;
	}

	State get_and_remove(int agent, int[] key) {
		long value = map[agent].get(key);
		if (value == 0)
			return null;
		State s = deserialize(agent, value, key);
		OpenAddressingIntArrayHashMap m = map[agent];
		m.remove_unsafe(key);
		return s;
	}

	State get(State s) {
		long value = map[s.agent()].get(s.box);
		if (value == 0)
			return null;
		final int mask = (1 << 13) - 1;
		int total_dist = (int) (value & mask);
		int dist = (int) ((value >> 13) & mask);
		int dir = (int) ((value >> 26) & 3);
		int pushes = (int) ((value >> 28) & 0xF);
		int prev_agent = (int) ((value >> 32) & 0xFF);
		State q = new State(s.agent(), s.box, dist, dir, pushes, prev_agent);
		q.set_heuristic(total_dist - dist);
		return q;
	}

	void remove(State s) {
		OpenAddressingIntArrayHashMap m = map[s.agent()];
		m.remove_unsafe(s.box);
	}

	int get_total_dist(State s) {
		long value = map[s.agent()].get(s.box);
		final int mask = (1 << 13) - 1;
		return (int) (value & mask);
	}

	private static boolean range(int a, int m) {
		return 0 <= a && a < m;
	}

	private int serialize_value(State s) {
		int v = 0;
		assert range(s.total_dist(), 1 << 13);
		assert range(s.dist(), 1 << 13);
		assert range(s.dir, 4);
		assert range(s.pushes(), 16);
		assert range(s.prev_agent(), 256);
		v = s.total_dist();
		v |= s.dist() << 13;
		v |= s.dir << 26;
		v |= s.pushes() << 28;
		v |= s.prev_agent() << 32;
		return v;
	}

	private static State deserialize(int agent, long value, int[] key) {
		final int mask = (1 << 13) - 1;
		int total_dist = (int) (value & mask);
		int dist = (int) ((value >> 13) & mask);
		int dir = (int) ((value >> 26) & 3);
		int pushes = (int) (value >>> 28) & 0xF;
		int prev_agent = (int) ((value >> 32) & 0xFF);
		State q = new State(agent, key.clone(), dist, dir, pushes, prev_agent);
		q.set_heuristic(total_dist - dist);
		return q;
	}
}