package tintor.sokoban;

import tintor.common.Array;
import tintor.common.ArrayDequeInt;
import tintor.common.AutoTimer;
import tintor.common.BitMatrix;
import tintor.common.Hungarian;

final class Heuristic {
	private final int Infinity = Integer.MAX_VALUE / 2; // limitation due to Hungarian
	final Level level;
	final boolean optimal;
	final int[] boxes;
	//final int[][] distance_goal; // distance[agent][box] to nearest goal
	final int[][] distance_box; // distance[box][goal_orginal]
	final Hungarian hungarian;
	static final AutoTimer timer = new AutoTimer("heuristic");
	int deadlocks;
	int non_deadlocks;

	Heuristic(Level level, boolean optimal) {
		this.level = level;
		this.optimal = optimal;
		int num_goals = level.num_boxes;
		int num_boxes = level.num_boxes;
		hungarian = new Hungarian(level.num_boxes);

		boxes = new int[num_boxes];

		/*distance_goal = null; // new int[level.cells][level.alive];
		if (distance_goal != null)
			for (int[] d : distance_goal)
				Arrays.fill(d, Integer.MAX_VALUE);*/

		distance_box = Array.ofInt(level.alive, num_goals, Infinity);
		int w = 0;
		ArrayDequeInt deque = new ArrayDequeInt(level.cells);
		BitMatrix visited = new BitMatrix(level.cells, level.alive);
		for (int g = 0; g < level.alive; g++) {
			if (!level.goal(g))
				continue;
			compute_distances_from_goal(g, w++, deque, visited);
		}
	}

	private static int make_pair(int a, int b) {
		assert 0 <= a && a < 65536;
		assert 0 <= b && b < 65536;
		return (a << 16) | b;
	}

	static int dist(int s, int[][] distance) {
		final int s_agent = (s >> 16) & 0xFFFF;
		final int s_box = s & 0xFFFF;
		return distance[s_agent][s_box];
	}

	void compute_distances_from_goal(int goal, int goal_ordinal, ArrayDequeInt deque, BitMatrix visited) {
		int[][] distance = Array.ofInt(level.cells, level.alive, Infinity);
		deque.clear();
		visited.clear();

		for (int a : level.moves[goal]) {
			deque.addFirst(make_pair(a, goal));
			distance[a][goal] = 0;
		}
		distance_box[goal][goal_ordinal] = 0;

		while (!deque.isEmpty()) {
			final int s = deque.removeFirst();
			final int s_agent = (s >> 16) & 0xFFFF;
			final int s_box = s & 0xFFFF;
			assert distance[s_agent][s_box] >= 0;
			assert distance[s_agent][s_box] != Infinity;
			distance_box[s_box][goal_ordinal] = Math.min(distance_box[s_box][goal_ordinal], distance[s_agent][s_box]);

			for (int c : level.moves[s_agent]) {
				if (c != s_box && distance[c][s_box] == Infinity) {
					// TODO moves included only if ! optimal
					set_distance(c, s_box, distance[s_agent][s_box] + 1, distance);
					deque.addLast(make_pair(c, s_box));
				}
				if (s_agent < level.alive && level.move(s_agent, level.delta[c][s_agent]) == s_box
						&& distance[c][s_agent] == Infinity) {
					set_distance(c, s_agent, distance[s_agent][s_box] + 1, distance);
					deque.addLast(make_pair(c, s_agent));
				}
			}
		}
	}

	private void set_distance(int a, int b, int d, int[][] distance) {
		distance[a][b] = d;
		/*if (distance_goal != null && d < distance_goal[a][b])
			distance_goal[a][b] = d;*/
	}

	public int evaluate(StateKey s) {
		try (AutoTimer t = timer.open()) {
			int h = evaluate_internal(s);
			assert h >= 0;
			if (h == Integer.MAX_VALUE) {
				deadlocks += 1;
				return h;
			}

			if (!optimal)
				h *= 3;
			non_deadlocks += 1;
			return h;
		}
	}

	/*private int evaluate_internal_cheap(State s) {
		int h = 0;
		for (int i = 0; i < level.alive; i++)
			if (s.box(i))
				h += distance_goal[s.agent][i];
		return h;
	}*/

	private int evaluate_internal(StateKey s) {
		int bc = 0;
		for (int b = 0; b < level.alive; b++)
			if (s.box(b)) {
				System.arraycopy(distance_box[b], 0, hungarian.costs[bc], 0, level.num_boxes);
				boxes[bc++] = b;
			}
		assert bc == boxes.length;

		int[] result = hungarian.execute();
		Array.for_each(result, (i, e) -> result[i] = distance_box[boxes[e]][i]);
		if (Array.contains(result, Infinity))
			return Integer.MAX_VALUE;
		return Array.sum(result);
	}
}