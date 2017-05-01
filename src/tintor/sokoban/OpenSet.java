package tintor.sokoban;

import tintor.common.InstrumentationAgent;
import tintor.common.Timer;
import tintor.common.Util;

// Set of all States for which we found some path from the start (not sure if optimal yet)
class OpenSet {
	private final StateMap map;
	private final StateArray[] queue = new StateArray[1000];
	private int min = queue.length;
	private final int[] key;

	private final Timer timer_get = new Timer();
	private final Timer timer_update = new Timer();
	private final Timer timer_add = new Timer();
	private final Timer timer_remove_min = new Timer();

	OpenSet(int alive, int cells) {
		map = new StateMap(alive, cells);
		for (int i = 0; i < queue.length; i++)
			queue[i] = new StateArray();
		key = new int[(alive + 31) / 32];
	}

	long report(int cycles) {
		timer_get.total /= cycles;
		timer_update.total /= cycles;
		timer_add.total /= cycles;
		timer_remove_min.total /= cycles;

		StringBuilder sb = new StringBuilder();
		int size = 0;
		for (int p = min; p < queue.length; p += 1) {
			size += queue[p].size();
			if (queue[p].size() > 0 && sb.length() < 50) {
				if (sb.length() > 0)
					sb.append(' ');
				sb.append(p);
				sb.append(':');
				sb.append(queue[p].size());
			}
		}

		for (StateArray array : queue)
			array.try_cleanup();

		long total = timer_get.total + timer_update.total + timer_add.total + timer_remove_min.total;
		System.out.printf("open:%s memory_map:%s memory_queue:%s\n", Util.human(size), deep_size(map),
				deep_size(queue));
		System.out.printf("  [get:%s update:%s add:%s remove_min:%s]\n", timer_get.clear(), timer_update.clear(),
				timer_add.clear(), timer_remove_min.clear());
		System.out.printf("  %s\n", sb);
		return total;
	}

	static String deep_size(Object o) {
		return Util.human(InstrumentationAgent.deepSizeOf(o));
	}

	// O(1)
	public int get_total_dist(State s) {
		try (Timer t = timer_get.start()) {
			return map.get_total_dist(s);
		}
	}

	// Called when a better path B to state is found than existing V
	// O(1)
	public void update(int v_total_dist, State b) {
		try (Timer t = timer_update.start()) {
			map.update(v_total_dist, b);
			if (b.total_dist() < min)
				min = b.total_dist();
			queue[b.total_dist()].push(b);
		}
	}

	// O(1)
	public void add(State s) {
		try (Timer t = timer_add.start()) {
			map.insert(s);
			if (s.total_dist() < min)
				min = s.total_dist();
			queue[s.total_dist()].push(s);
		}
	}

	// O(1)
	public State remove_min() {
		try (Timer t = timer_remove_min.start()) {
			while (true) {
				while (queue[min].size() == 0)
					min += 1;
				State s = map.get_and_remove(queue[min].pop(key), key);
				if (s != null)
					return s;
			}
		}
	}

	public boolean empty() {
		while (queue[min].size() == 0)
			if (++min == queue.length)
				return true;
		return false;
	}

	public int size() {
		int size = 0;
		for (int p = min; p < queue.length; p += 1)
			size += queue[p].size();
		return size;
	}
}