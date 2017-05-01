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
		serialize(b);
		OpenAddressingIntArrayHashMap m = map[b.agent()];
		m.update_unsafe(key, serialize_value(b));
	}

	void insert(State s) {
		serialize(s);
		OpenAddressingIntArrayHashMap m = map[s.agent()];
		m.insert_unsafe(key, serialize_value(s));
		size += 1;
	}

	boolean contains(State s) {
		serialize(s);
		return map[s.agent()].get(key) != 0;
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
		serialize(s);
		long value = map[s.agent()].get(key);
		if (value == 0)
			return null;
		final int mask = (1 << 13) - 1;
		int total_dist = (int) (value & mask);
		int dist = (int) ((value >> 13) & mask);
		int dir = (int) ((value >> 26) & 3);
		int pushes = (int) ((value >> 28) & 0xF);
		int prev_agent = (int) ((value >> 32) & 0xFF);
		State q = new State(s.agent(), s.box0, s.box1, dist, dir, pushes, prev_agent);
		q.set_heuristic(total_dist - dist);
		return q;
	}

	void remove(State s) {
		serialize(s);
		OpenAddressingIntArrayHashMap m = map[s.agent()];
		m.remove_unsafe(key);
	}

	int get_total_dist(State s) {
		serialize(s);
		long value = map[s.agent()].get(key);
		final int mask = (1 << 13) - 1;
		return (int) (value & mask);
	}

	private int[] serialize(State s) {
		switch (key.length) {
		case 4:
			key[3] = (int) (s.box1 >>> 32);
		case 3:
			key[2] = (int) s.box1;
		case 2:
			key[1] = (int) (s.box0 >>> 32);
		case 1:
			key[0] = (int) s.box0;
		}
		return key;
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

		long box0 = 0;
		long box1 = 0;
		switch (key.length) {
		case 4:
			box1 |= ((long) key[3]) << 32;
		case 3:
			box1 |= ((long) key[2]) & 0xFFFFFFFFl;
		case 2:
			box0 |= ((long) key[1]) << 32;
		case 1:
			box0 |= ((long) key[0]) & 0xFFFFFFFFl;
		}
		State q = new State(agent, box0, box1, dist, dir, pushes, prev_agent);
		q.set_heuristic(total_dist - dist);
		return q;
	}
}