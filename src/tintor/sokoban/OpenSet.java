package tintor.sokoban;

import java.util.Arrays;

import tintor.common.AutoTimer;
import tintor.common.InstrumentationAgent;
import tintor.common.Util;
import tintor.sokoban.StateMap.StateKeyPredicate;

// Set of all States for which we found some path from the start (not sure if optimal yet)
class OpenSet {
	private final StateMap map;
	private StateArray[] queue = new StateArray[0];
	private int min = Integer.MAX_VALUE;

	private static final AutoTimer timer_get = new AutoTimer("open.get");
	private static final AutoTimer timer_update = new AutoTimer("open.update");
	private static final AutoTimer timer_add = new AutoTimer("open.add");
	private static final AutoTimer timer_remove_min = new AutoTimer("open.remove_min");

	OpenSet(int alive, int cells) {
		map = new StateMap(alive, cells, OpenAddressingIntArrayHashMap.Values.InMemory);
	}

	void report() {
		StringBuilder sb = new StringBuilder();
		int size = 0;
		for (int p = min; p < queue.length; p += 1) {
			if (queue[p] == null)
				continue;
			size += queue[p].size();
			if (queue[p].size() > 0 && sb.length() <= 60) {
				if (sb.length() > 0)
					sb.append(' ');
				sb.append(p);
				sb.append(':');
				sb.append(queue[p].size());
			}
		}

		for (int i = 0; i < queue.length; i++)
			if (queue[i] != null && queue[i].size() == 0)
				queue[i] = null;

		System.out.printf("open:%s memory_map:%s memory_queue:%s\n", Util.human(size), deep_size(map),
				deep_size(queue));
		if (sb.length() > 0)
			System.out.printf("  %s\n", sb);
	}

	static String deep_size(Object o) {
		return Util.human(InstrumentationAgent.deepSizeOf(o));
	}

	private StateArray queue(int p) {
		if (p >= queue.length)
			queue = Arrays.copyOf(queue, Math.max(p + 1, queue.length * 3 / 2));
		if (queue[p] == null)
			queue[p] = new StateArray();
		return queue[p];
	}

	public void remove_if(StateKeyPredicate fn) {
		map.remove_if(fn);
	}

	// O(1)
	public int get_total_dist(StateKey s) {
		try (AutoTimer t = timer_get.open()) {
			return map.get_total_dist(s);
		}
	}

	// Called when a better path B to state is found than existing V
	// O(1)
	public void update(int v_total_dist, State b) {
		try (AutoTimer t = timer_update.open()) {
			map.update(v_total_dist, b);
			if (b.total_dist < min)
				min = b.total_dist;
			queue(b.total_dist).push(b);
		}
	}

	// O(1)
	public void add(State s) {
		try (AutoTimer t = timer_add.open()) {
			map.insert(s);
			if (s.total_dist < min)
				min = s.total_dist;
			queue(s.total_dist).push(s);
		}
	}

	// O(1)
	public State remove_min() {
		try (AutoTimer t = timer_remove_min.open()) {
			while (true) {
				while (true) {
					if (min == queue.length)
						return null;
					if (queue[min] != null && queue[min].size() > 0)
						break;
					min += 1;
				}
				StateKey k = queue(min).pop();
				State s = map.get(k);
				if (s != null) {
					map.remove(k);
					return s;
				}
			}
		}
	}

	public void remove_all_ge(int total_dist) {
		for (int p = total_dist; p < queue.length; p++)
			while (queue[p] != null && queue[p].size() > 0) {
				StateKey k = queue(p).pop();
				State s = map.get(k);
				if (s != null)
					map.remove(k);
			}
	}

	public int size() {
		for (int p = 0; p < min; p++)
			assert queue[p] == null || queue[p].size() == 0;
		int size = 0;
		for (int p = min; p < queue.length; p += 1)
			if (queue[p] != null)
				size += queue[p].size();
		return size;
	}
}