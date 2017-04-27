package tintor.sokoban;

import java.util.Arrays;

import tintor.common.ArrayDequeInt;
import tintor.common.BitMatrix;
import tintor.common.HungarianAlgorithm;
import tintor.common.Timer;

final class Heuristic {
	final Level level;
	final int[] boxes;
	final int[][] distance_box;
	final HungarianAlgorithm hungarian;
	final Timer timer = new Timer();
	int deadlocks;
	int non_deadlocks;

	Heuristic(Level level) {
		this.level = level;
		int num_goals = level.num_boxes;
		int num_boxes = level.num_boxes;
		hungarian = new HungarianAlgorithm(num_boxes, num_goals);

		boxes = new int[num_boxes];

		distance_box = new int[level.alive][num_goals];
		for (int[] d : distance_box)
			Arrays.fill(d, Integer.MAX_VALUE);
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
		int[][] distance = new int[level.cells][level.alive];
		for (int[] d : distance)
			Arrays.fill(d, -1);
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
			distance_box[s_box][goal_ordinal] = Math.min(distance_box[s_box][goal_ordinal], distance[s_agent][s_box]);

			for (int c : level.moves[s_agent]) {
				if (c != s_box && distance[c][s_box] == -1) {
					distance[c][s_box] = distance[s_agent][s_box] + 1;
					deque.addLast(make_pair(c, s_box));
				}
				if (s_agent < level.alive && level.move(s_agent, level.delta[c][s_agent]) == s_box
						&& distance[c][s_agent] == -1) {
					distance[c][s_agent] = distance[s_agent][s_box] + 1;
					deque.addLast(make_pair(c, s_agent));
				}
			}
		}
	}

	public int evaluate(State s, State prev) {
		try (Timer t = timer.start()) {
			int h = (prev != null && !s.is_push()) ? evaluate_delta(s, prev) : evaluate_internal(s);
			assert h >= 0;
			if (h == Integer.MAX_VALUE) {
				deadlocks += 1;
			} else {
				non_deadlocks += 1;
			}
			return h;
		}
	}

	private int evaluate_delta(State s, State prev) {
		int a = prev.total_dist() - prev.dist() - agent_to_nearest_box_distance(prev);
		assert a >= 0;
		return a + agent_to_nearest_box_distance(s);
	}

	private int evaluate_internal(State s) {
		int w = 0;
		for (int i = 0; i < level.alive; i++)
			if (s.box(i))
				boxes[w++] = i;
		assert w == boxes.length;

		for (int i = 0; i < boxes.length; i++) {
			for (int j = 0; j < distance_box[0].length; j++) {
				int d = distance_box[boxes[i]][j];
				hungarian.cost[i][j] = (d < 0 || d == Integer.MAX_VALUE) ? Double.POSITIVE_INFINITY : d;
			}
		}

		int[] goal = hungarian.execute();
		for (int i = 0; i < boxes.length; i++)
			if (goal[i] < 0)
				return Integer.MAX_VALUE;

		int sum = 0;
		for (int i = 0; i < boxes.length; i++) {
			int d = distance_box[boxes[i]][goal[i]];
			assert d >= 0;
			assert d != Integer.MAX_VALUE;
			sum += d;
		}

		assert sum >= 0;
		return sum + agent_to_nearest_box_distance(s);
	}

	private int agent_to_nearest_box_distance(State s) {
		int dist = Integer.MAX_VALUE;
		for (int i = 0; i < level.alive; i++)
			if (s.box(i) && !level.goal(i))
				dist = Math.min(level.agent_distance[s.agent()][i], dist);
		if (dist == Integer.MAX_VALUE)
			return 0;
		assert dist > 0;
		return dist - 1;
	}
}