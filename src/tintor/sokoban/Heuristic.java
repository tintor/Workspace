package tintor.sokoban;

import java.util.Arrays;

import tintor.common.ArrayDequeInt;
import tintor.common.AutoTimer;
import tintor.common.BitMatrix;
import tintor.common.HungarianAlgorithm;

final class Heuristic {
	final Level level;
	final int[] boxes;
	final int[][] distance_goal; // distance[agent][box] to nearest goal
	final int[][] distance_box; // distance[box][goal_orginal]
	final HungarianAlgorithm hungarian;
	final AutoTimer timer = new AutoTimer("heuristic");
	int deadlocks;
	int non_deadlocks;

	Heuristic(Level level) {
		this.level = level;
		int num_goals = level.num_boxes;
		int num_boxes = level.num_boxes;
		hungarian = new HungarianAlgorithm(num_boxes, num_goals);

		boxes = new int[num_boxes];

		distance_goal = null; // new int[level.cells][level.alive];
		if (distance_goal != null)
			for (int[] d : distance_goal)
				Arrays.fill(d, Integer.MAX_VALUE);

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
			Arrays.fill(d, Integer.MAX_VALUE);
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
			assert distance[s_agent][s_box] != Integer.MAX_VALUE;
			distance_box[s_box][goal_ordinal] = Math.min(distance_box[s_box][goal_ordinal], distance[s_agent][s_box]);

			for (int c : level.moves[s_agent]) {
				if (c != s_box && distance[c][s_box] == Integer.MAX_VALUE) {
					set_distance(c, s_box, distance[s_agent][s_box] + 1, distance);
					deque.addLast(make_pair(c, s_box));
				}
				if (s_agent < level.alive && level.move(s_agent, level.delta[c][s_agent]) == s_box
						&& distance[c][s_agent] == Integer.MAX_VALUE) {
					set_distance(c, s_agent, distance[s_agent][s_box] + 1, distance);
					deque.addLast(make_pair(c, s_agent));
				}
			}
		}
	}

	private void set_distance(int a, int b, int d, int[][] distance) {
		distance[a][b] = d;
		if (distance_goal != null && d < distance_goal[a][b])
			distance_goal[a][b] = d;
	}

	public int evaluate(State s) {
		try (AutoTimer t = timer.open()) {
			int h = evaluate_internal(s);
			assert h >= 0;
			if (h == Integer.MAX_VALUE)
				deadlocks += 1;
			else {
				h *= 3; // overestimation
				non_deadlocks += 1;
			}
			return h;
		}
	}

	private int evaluate_internal_cheap(State s) {
		int h = 0;
		for (int i = 0; i < level.alive; i++)
			if (s.box(i))
				h += distance_goal[s.agent][i];
		return h;
	}

	private int evaluate_internal(State s) {
		int w = 0;
		for (int i = 0; i < level.alive; i++)
			if (s.box(i)) {
				System.arraycopy(distance_box[i], 0, hungarian.cost[w], 0, level.num_boxes);
				boxes[w++] = i;
			}
		assert w == boxes.length;

		int[] goal = hungarian.execute();
		int sum = 0;
		for (int i = 0; i < boxes.length; i++) {
			if (goal[i] < 0)
				return Integer.MAX_VALUE;
			int d = distance_box[boxes[i]][goal[i]];
			if (d == Integer.MAX_VALUE)
				return Integer.MAX_VALUE;
			assert d >= 0;
			sum += d;
			assert sum >= 0 && sum < Integer.MAX_VALUE;
		}
		return sum;
	}
}