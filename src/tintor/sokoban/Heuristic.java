package tintor.sokoban;

import java.util.Arrays;

import tintor.common.HungarianAlgorithm;
import tintor.common.Timer;
import tintor.common.Visitor;

// TODO instead of using simple distance over live cells, solve level with
// single box and using number of pushes
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

		Visitor visitor = level.visitor;
		distance_box = new int[level.alive][num_goals];
		for (int a = 0; a < level.alive; a++)
			Arrays.fill(distance_box[a], -1); // not reachable
		int w = 0;
		for (int g = 0; g < level.alive; g++) {
			if (!level.goal(g))
				continue;
			distance_box[g][w] = 0;
			for (int a : visitor.init(g))
				for (int b : level.moves[a]) {
					if (b >= level.alive || visitor.visited(b))
						continue;
					// TODO debug
					/*
					 * if (level.canMove(b, dir) == -1) continue;
					 */
					distance_box[b][w] = distance_box[a][w] + 1;
					visitor.add(b);
				}
			w += 1;
		}
	}

	public int evaluate(State s, State prev) {
		try (Timer t = timer.start()) {
			int h = (prev != null && !s.is_push) ? evaluate_delta(s, prev) : evaluate_internal(s);
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

		for (int i = 0; i < boxes.length; i++)
			for (int j = 0; j < distance_box[0].length; j++) {
				int d = distance_box[boxes[i]][j];
				hungarian.cost[i][j] = d < 0 ? Double.POSITIVE_INFINITY : d;
			}

		int[] goal = hungarian.execute();
		for (int i = 0; i < boxes.length; i++)
			if (goal[i] < 0)
				return Integer.MAX_VALUE;

		int sum = 0;
		for (int i = 0; i < boxes.length; i++) {
			int d = distance_box[boxes[i]][goal[i]];
			assert d >= 0;
			sum += d;
		}

		return sum + agent_to_nearest_box_distance(s);
	}

	private int agent_to_nearest_box_distance(State s) {
		int dist = Integer.MAX_VALUE;
		for (int i = 0; i < level.alive; i++)
			if (s.box(i))
				dist = Math.min(level.agent_distance[s.agent()][i], dist);
		assert dist > 0;
		return dist - 1;
	}
}